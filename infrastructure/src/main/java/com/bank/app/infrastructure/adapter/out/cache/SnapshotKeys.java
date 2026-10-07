package com.bank.app.infrastructure.adapter.out.cache;

import com.bank.app.accountapi.AccountSnapshotCache;

/**
 * Redis key namespace for account snapshots (S4 split: naming lives here, not
 * scattered across the adapter). One id-to-IBAN mapping per account (never a
 * combinatorial batch key): repeated report pages share entries instead of
 * minting a key per page. All entries share the snapshot TTL so index keys
 * can never outlive the data.
 */
final class SnapshotKeys {

    static final String KEY_PREFIX = "account-snapshot:";
    private static final String ID_PREFIX = "id-";
    private static final String IBAN_PREFIX = "iban-";
    private static final String MAP_PREFIX = "iban-of:";
    private static final String IDX_IBANS_BY_ID_PREFIX = "idx:ibans-by-id:";
    private static final String IDX_ID_BY_IBAN_PREFIX = "idx:id-by-iban:";

    private SnapshotKeys() {}

    /** Prefix passed to the evict Lua script so key names live in one place. */
    static String ibanKeyPrefix() {
        return KEY_PREFIX + IBAN_PREFIX;
    }

    /** Prefix passed to the evict Lua script so key names live in one place. */
    static String idByIbanPrefix() {
        return KEY_PREFIX + IDX_ID_BY_IBAN_PREFIX;
    }

    static String scanPattern() {
        return KEY_PREFIX + "*";
    }

    static String idKey(Long accountId) {
        return KEY_PREFIX + ID_PREFIX + accountId;
    }

    static String ibanKey(String ibanValue) {
        return KEY_PREFIX + IBAN_PREFIX + AccountSnapshotCache.ibanKey(ibanValue);
    }

    static String ibanKeyRaw(String normalizedIbanKey) {
        return ibanKeyPrefix() + normalizedIbanKey;
    }

    static String mapKey(Long accountId) {
        return KEY_PREFIX + MAP_PREFIX + accountId;
    }

    static String ibansByIdIndex(Long accountId) {
        return KEY_PREFIX + IDX_IBANS_BY_ID_PREFIX + accountId;
    }

    /** Reverse-lookup path hands the id back as a stored string; keep it opaque. */
    static String ibansByIdIndex(String accountId) {
        return KEY_PREFIX + IDX_IBANS_BY_ID_PREFIX + accountId;
    }

    static String idByIbanIndex(String ibanKey) {
        return KEY_PREFIX + IDX_ID_BY_IBAN_PREFIX + ibanKey;
    }
}
