package com.bank.app.infrastructure.adapter.out.audit;

import com.bank.app.audit.application.port.out.AuditAnchorStorePort;
import com.bank.app.audit.application.dto.AuditAnchor;
import com.bank.app.infrastructure.adapter.in.audit.AuditAnchorProperties;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Append-only file sink for signed audit anchors. The file must live on
 * storage that is external to the database and, for a real anchor, immutable
 * (object-lock bucket or WORM volume). The adapter itself appends with
 * {@code O_APPEND}, takes an exclusive file lock, and forces the bytes to disk
 * before returning; it also restricts the file to owner-read/write where the
 * filesystem supports POSIX permissions.
 *
 * <p>One line per publication: version|keyId|lastSeq|lastHash|publishedAt|signature.
 * Reading the last non-blank line gives the latest anchor; a corrupt line fails
 * closed so the publisher alarms instead of silently accepting a truncated
 * anchor.
 */
@Component
@ConditionalOnProperty(name = "app.audit.anchor.enabled", havingValue = "true")
public class FileAuditAnchorAdapter implements AuditAnchorStorePort {

    private static final int FIELD_COUNT = 6;

    private final Path anchorFile;

    public FileAuditAnchorAdapter(AuditAnchorProperties properties) {
        this.anchorFile = Path.of(properties.file()).toAbsolutePath().normalize();
    }

    @Override
    public Optional<AuditAnchor> latest() {
        if (!Files.exists(anchorFile)) {
            return Optional.empty();
        }
        try {
            List<String> lines = Files.readAllLines(anchorFile, StandardCharsets.UTF_8);
            for (int index = lines.size() - 1; index >= 0; index--) {
                String line = lines.get(index);
                if (!line.isBlank()) {
                    return Optional.of(parse(line));
                }
            }
            return Optional.empty();
        } catch (IOException e) {
            throw new IllegalStateException("Audit anchor file cannot be read", e);
        }
    }

    @Override
    public void append(AuditAnchor anchor) {
        Path parent = anchorFile.getParent();
        try {
            if (parent != null) {
                Files.createDirectories(parent);
            }
            byte[] line = (anchor.toLine() + "\n").getBytes(StandardCharsets.UTF_8);
            try (FileChannel channel = FileChannel.open(anchorFile,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.APPEND);
                 FileLock ignored = channel.lock()) {
                ByteBuffer buffer = ByteBuffer.wrap(line);
                while (buffer.hasRemaining()) {
                    channel.write(buffer);
                }
                channel.force(true);
            }
            restrictPermissions();
        } catch (IOException e) {
            throw new IllegalStateException("Audit anchor file cannot be written", e);
        }
    }

    private void restrictPermissions() {
        try {
            Files.setPosixFilePermissions(anchorFile,
                    PosixFilePermissions.fromString("rw-------"));
        } catch (UnsupportedOperationException | IOException ignored) {
            // Windows/non-POSIX filesystems do not expose these permissions;
            // the mount's ACL is the operational control there.
        }
    }

    private static AuditAnchor parse(String line) {
        String[] parts = line.split("\\|", -1);
        if (parts.length != FIELD_COUNT) {
            throw new IllegalStateException("Corrupt audit anchor line: expected "
                    + FIELD_COUNT + " fields but found " + parts.length);
        }
        try {
            return new AuditAnchor(
                    Integer.parseInt(parts[0]),
                    parts[1],
                    Long.parseLong(parts[2]),
                    parts[3],
                    Long.parseLong(parts[4]),
                    parts[5]);
        } catch (IllegalArgumentException invalid) {
            throw new IllegalStateException("Corrupt audit anchor line", invalid);
        }
    }
}
