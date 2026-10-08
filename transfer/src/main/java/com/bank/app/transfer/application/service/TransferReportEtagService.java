package com.bank.app.transfer.application.service;

import com.bank.app.transfer.application.dto.TransferReportResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.stream.Collectors;

/**
 * Cache validators for transfer report responses (extracted from the former
 * fat {@code TransferController}: hashing report content is application
 * logic, not web plumbing, and deserves direct unit tests).
 *
 * <p>ETag: SHA-256 over stable report content (account + page + volume +
 * cursor + item fingerprint + whole-range totals). 32-bit Objects.hash
 * collides too easily for a cache validator shared across accounts;
 * 64-bit truncated SHA-256 keeps the header short while making accidental
 * collisions negligible. Volumes are normalized (stripTrailingZeros) so
 * 10.0 and 10.00 produce the same tag. The item fingerprint
 * (id:status:amount per row) prevents a false 304 when two pages share
 * the same count/volume/currency but list different transfers; totalCount
 * /totalVolume are included so includeTotals/combined responses change
 * the tag when the range aggregates move even if the page is identical.
 * Clients send If-None-Match (or *) to get 304 without re-render.
 */
public final class TransferReportEtagService {

    private TransferReportEtagService() {
    }

    public static String etagFor(TransferReportResponse body) {
        String volume = body.pageVolume().stripTrailingZeros().toPlainString();
        StringBuilder norm = new StringBuilder();
        norm.append(body.accountId()).append('|').append(body.pageTransferCount()).append('|')
                .append(volume).append('|').append(body.currency()).append('|').append(body.hasNext()).append('|')
                .append(body.nextCursorCreatedAt()).append('|').append(body.nextCursorId()).append('|')
                .append(body.transfers().size()).append("|items:");
        String itemsFingerprint = body.transfers().stream()
                .map(t -> t.id() + ":" + t.status() + ":"
                        + t.amount().stripTrailingZeros().toPlainString() + ":" + t.currency())
                .collect(Collectors.joining(","));
        norm.append(itemsFingerprint).append("|totals:")
                .append(body.totalCount()).append(':')
                .append(body.totalVolume() == null
                        ? "null"
                        : body.totalVolume().stripTrailingZeros().toPlainString());
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(norm.toString().getBytes(StandardCharsets.UTF_8));
            return "W/\"report-" + HexFormat.of().formatHex(digest, 0, 8) + "\"";
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }
}
