package com.bank.app.transfer.application.usecase;

import com.bank.app.transfer.application.port.out.AccountOperationPort;
import com.bank.app.transfer.application.port.out.AccountOperationPort.AccountInfo;
import com.bank.app.transfer.application.exception.TransferNotFoundException;
import com.bank.app.common.security.port.out.SecurityContextPort;
import com.bank.app.transfer.application.dto.TransferDetailResponse;
import com.bank.app.transfer.application.port.out.LoadTransferPort;
import com.bank.app.transfer.application.port.in.GetTransferDetailQuery;
import com.bank.app.transfer.domain.Transfer;
import com.bank.app.common.exception.AuthorizationException;
import java.util.Objects;

public class GetTransferDetailUseCaseImpl implements GetTransferDetailQuery {

    private final LoadTransferPort loadTransferPort;
    private final AccountOperationPort accountOperationPort;
    private final SecurityContextPort securityContextPort;

    public GetTransferDetailUseCaseImpl(LoadTransferPort loadTransferPort,
                                     AccountOperationPort accountOperationPort,
                                     SecurityContextPort securityContextPort) {
        this.loadTransferPort = loadTransferPort;
        this.accountOperationPort = accountOperationPort;
        this.securityContextPort = securityContextPort;
    }

    public TransferDetailResponse execute(Long transferId) {
        Objects.requireNonNull(transferId, "Transfer ID null olamaz");
        Transfer transfer = loadTransferPort.findById(transferId)
                .orElseThrow(() -> new TransferNotFoundException(transferId));

        AccountInfo sender = accountOperationPort.getAccountInfo(transfer.getSenderAccountId());
        AccountInfo receiver = accountOperationPort.getAccountInfo(transfer.getReceiverAccountId());

        Long currentUserId = securityContextPort.getCurrentUserId()
                .orElseThrow(() -> new AuthorizationException("Oturum bulunamadı."));
        if (!currentUserId.equals(sender.userId()) && !currentUserId.equals(receiver.userId())) {
            throw new AuthorizationException("Bu transferin detaylarını görme yetkiniz yok.");
        }

        return TransferDetailResponse.from(transfer);
    }
}
