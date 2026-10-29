package com.bank.app.infrastructure.adapter.out.audit;

import com.bank.app.audit.application.dto.AuditAnchor;
import com.bank.app.infrastructure.adapter.in.audit.AuditAnchorProperties;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("FileAuditAnchorAdapter")
class FileAuditAnchorAdapterTest {

    private static final String HASH = "a".repeat(64);
    private static final String OTHER_HASH = "b".repeat(64);
    private static final String SIGNATURE = "A".repeat(43);
    // Derived at runtime (same helper as AuditAnchorSignerTest) so the fixture
    // never lands in the source as a secret-shaped Base64 literal, which the
    // gitleaks generic-api-key rule reports. See docs/decisions/secret-management.md.
    private static final String SECRET = secret("file-anchor-key");

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("should return empty when the anchor file does not exist")
    void shouldReturnEmptyWhenMissing() {
        FileAuditAnchorAdapter adapter = adapter(tempDir.resolve("missing/anchor.log"));

        assertThat(adapter.latest()).isEmpty();
    }

    @Test
    @DisplayName("should append and read back the latest anchor")
    void shouldAppendAndReadLatest() throws Exception {
        Path file = tempDir.resolve("nested/anchor.log");
        FileAuditAnchorAdapter adapter = adapter(file);
        AuditAnchor first = anchor(1, HASH, 1_790_000_000L);
        AuditAnchor second = anchor(2, OTHER_HASH, 1_790_000_060L);

        adapter.append(first);
        adapter.append(second);

        assertThat(adapter.latest()).contains(second);
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        assertThat(lines).hasSize(2);
        assertThat(lines.get(0)).isEqualTo(first.toLine());
        assertThat(lines.get(1)).isEqualTo(second.toLine());
    }

    @Test
    @DisplayName("should fail closed on a corrupt anchor line")
    void shouldFailClosedOnCorruptLine() throws Exception {
        Path file = tempDir.resolve("anchor.log");
        Files.writeString(file, "1|anchor-v1|not-a-number|" + HASH + "|1|" + SIGNATURE + "\n",
                StandardCharsets.UTF_8);
        FileAuditAnchorAdapter adapter = adapter(file);

        assertThatThrownBy(adapter::latest)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Corrupt audit anchor line");
    }

    private FileAuditAnchorAdapter adapter(Path file) {
        return new FileAuditAnchorAdapter(new AuditAnchorProperties(
                true, file.toString(), "anchor-v1", SECRET, Map.of()));
    }

    private static AuditAnchor anchor(long seq, String hash, long publishedAt) {
        return new AuditAnchor(1, "anchor-v1", seq, hash, publishedAt, SIGNATURE);
    }

    private static String secret(String value) {
        byte[] key = Arrays.copyOf(value.getBytes(StandardCharsets.UTF_8), 32);
        return Base64.getEncoder().encodeToString(key);
    }
}
