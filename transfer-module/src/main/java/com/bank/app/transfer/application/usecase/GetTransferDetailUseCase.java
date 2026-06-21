package com.bank.app.transfer.application.usecase;

import com.bank.app.transfer.application.port.out.AccountOperationPort;
import com.bank.app.transfer.application.port.out.AccountOperationPort.AccountInfo;
import com.bank.app.transfer.exception.TransferNotFoundException;
import com.bank.app.common.security.port.out.SecurityContextPort;
import com.bank.app.transfer.application.dto.TransferDetailResponse;
import com.bank.app.transfer.application.port.out.LoadTransferPort;
import com.bank.app.transfer.application.port.in.GetTransferDetailPort;
import com.bank.app.transfer.domain.Transfer;
import org.springframework.security.access.AccessDeniedException;

import java.util.Objects;

public class GetTransferDetailUseCase implements GetTransferDetailPort {

    private final LoadTransferPort loadTransferPort;
    private final AccountOperationPort accountOperationPort;
    private final SecurityContextPort securityContextPort;

    public GetTransferDetailUseCase(LoadTransferPort loadTransferPort,
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

        // Load account metadata through the internal service (decoupled from domain Account entity)
        AccountInfo sender = accountOperationPort.getAccountInfo(transfer.getSenderAccountId());
        AccountInfo receiver = accountOperationPort.getAccountInfo(transfer.getReceiverAccountId());

        Long currentUserId = securityContextPort.getCurrentUserId()
                .orElseThrow(() -> new AccessDeniedException("Oturum bulunamadı."));
        if (!currentUserId.equals(sender.userId()) && !currentUserId.equals(receiver.userId())) {
            throw new AccessDeniedException("Bu transferin detaylarını görme yetkiniz yok.");
        }

        return TransferDetailResponse.from(transfer);
    }
}
