package com.bank.app.transfer.application.usecase;

import com.bank.app.common.application.ReadOnlyUseCase;
import com.bank.app.transfer.application.dto.TransferDetailResponse;
import com.bank.app.transfer.application.exception.TransferNotFoundException;
import com.bank.app.transfer.application.port.in.GetTransferDetailQuery;
import com.bank.app.common.application.port.out.AccountAclPort;
import com.bank.app.common.application.port.out.AccountAclPort.AccountInfo;
import com.bank.app.transfer.application.port.out.LoadTransferPort;
import com.bank.app.transfer.application.service.TransferAuthorizationService;
import com.bank.app.transfer.domain.Transfer;
import java.util.Objects;

@ReadOnlyUseCase
public class GetTransferDetailUseCaseImpl implements GetTransferDetailQuery {

    private final LoadTransferPort loadTransferPort;
    private final AccountAclPort accountAclPort;
    private final TransferAuthorizationService transferAuthorizationService;

    public GetTransferDetailUseCaseImpl(LoadTransferPort loadTransferPort,
                                     AccountAclPort accountAclPort,
                                     TransferAuthorizationService transferAuthorizationService) {
        this.loadTransferPort = loadTransferPort;
        this.accountAclPort = accountAclPort;
        this.transferAuthorizationService = transferAuthorizationService;
    }

    @Override
    public TransferDetailResponse execute(Long transferId) {
        Objects.requireNonNull(transferId, "Transfer ID must not be null");
        Transfer transfer = loadTransferPort.findById(transferId)
                .orElseThrow(() -> new TransferNotFoundException(transferId));

        AccountInfo sender = accountAclPort.getAccountInfo(transfer.getSenderAccountId());
        AccountInfo receiver = accountAclPort.getAccountInfo(transfer.getReceiverAccountId());

        transferAuthorizationService.authorizeTransferAccess(sender.userId(), receiver.userId(),
                "You are not authorized to view this transfer's details.");

        return TransferDetailResponse.from(transfer);
    }
}
