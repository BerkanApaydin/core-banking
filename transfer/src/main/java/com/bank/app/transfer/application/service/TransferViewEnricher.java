package com.bank.app.transfer.application.service;

import com.bank.app.transfer.application.dto.TransferResponse;
import com.bank.app.transfer.application.port.out.AccountAclPort;
import com.bank.app.transfer.domain.Transfer;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Enriches transfer entities with human-readable IBANs for list views (history, reports).
 * Account IBANs are batch-loaded in a single call to avoid N+1 queries.
 */
public class TransferViewEnricher {

    private final AccountAclPort accountAclPort;

    public TransferViewEnricher(AccountAclPort accountAclPort) {
        this.accountAclPort = Objects.requireNonNull(accountAclPort, "AccountAclPort must not be null");
    }

    public List<TransferResponse> enrich(List<Transfer> transfers) {
        Objects.requireNonNull(transfers, "Transfers must not be null");
        Map<Long, String> ibansByAccountId = loadIbans(transfers);
        return transfers.stream()
                .map(transfer -> TransferResponse.from(
                        transfer,
                        ibansByAccountId.get(transfer.getSenderAccountId()),
                        ibansByAccountId.get(transfer.getReceiverAccountId())))
                .collect(Collectors.toList());
    }

    public Map<Long, String> loadIbans(List<Transfer> transfers) {
        Objects.requireNonNull(transfers, "Transfers must not be null");
        Set<Long> accountIds = transfers.stream()
                .flatMap(t -> Stream.of(t.getSenderAccountId(), t.getReceiverAccountId()))
                .collect(Collectors.toSet());
        return accountAclPort.getIbansForAccounts(accountIds);
    }
}
