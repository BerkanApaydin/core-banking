package com.bank.app.accountapi;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Contract guard for docs/account-snapshot-cache.md rule 1: the snapshot is
 * identity + status only. A balance (or any mutable ledger state) cached here
 * would bypass the overdraft protection that re-reads authoritative state
 * under pessimistic locks — so the shape is pinned and the build breaks if it
 * grows a balance component.
 */
class AccountSnapshotContractTest {

    @Test
    @DisplayName("snapshot carries identity and status only — never balances (14.1/K10)")
    void snapshotCarriesNoBalance() {
        Set<String> components = Arrays.stream(AccountSnapshot.class.getRecordComponents())
                .map(c -> c.getName())
                .collect(Collectors.toSet());

        assertEquals(Set.of("id", "userId", "currency", "status"), components);
    }

    @Test
    @DisplayName("no snapshot component is numeric ledger state")
    void noNumericLedgerState() {
        for (var component : AccountSnapshot.class.getRecordComponents()) {
            assertTrue(component.getType() == Long.class || component.getType() == String.class,
                    "Snapshot component must stay an identifier/code, got: " + component);
        }
    }
}
