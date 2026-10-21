package com.bank.app.transfer.adapter.in.web;

import com.bank.app.common.domain.TokenDigest;
import com.bank.app.transfer.application.dto.TransferReportResponse;
import java.util.stream.Collectors;

/**
 * HTTP cache validators for transfer report responses. ETag syntax and weak
 * validator shape are transport concerns, so this helper lives in the web
 * adapter rather than the application layer.
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
public final class TransferReportEtag {

    private TransferReportEtag() {
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
        // SHA-256 via the shared TokenDigest primitive (single MessageDigest ->
        // HexFormat chain in the codebase); first 8 bytes keep the header short.
        String fullHex = TokenDigest.sha256Hex(norm.toString());
        return "W/\"report-" + fullHex.substring(0, 16) + "\"";
    }
}
