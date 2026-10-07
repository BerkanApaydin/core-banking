package com.bank.app.infrastructure.adapter.out.cache;

import com.bank.app.accountapi.AccountSnapshot;

/**
 * Wire format for cached snapshots (S4 split): {@code id|userId|currency|status}.
 * Deliberately minimal — identity and status only, never balances (see
 * docs/account-snapshot-cache.md). Malformed payloads decode to null so a
 * corrupt entry falls back to the DB instead of failing the request.
 */
final class SnapshotCodec {

    private SnapshotCodec() {}

    static String encode(AccountSnapshot snapshot) {
        return snapshot.id() + "|" + snapshot.userId() + "|" + snapshot.currency() + "|" + snapshot.status();
    }

    static AccountSnapshot decode(String raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        String[] parts = raw.split("\\|", -1);
        if (parts.length != 4) {
            return null;
        }
        try {
            return new AccountSnapshot(Long.parseLong(parts[0]), Long.parseLong(parts[1]), parts[2], parts[3]);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
