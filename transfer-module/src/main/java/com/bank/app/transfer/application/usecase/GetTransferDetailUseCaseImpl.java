package com.bank.app.transfer.application.usecase;

import com.bank.app.common.application.ReadOnlyUseCase;
import com.bank.app.common.application.port.out.security.SecurityContextPort;
import com.bank.app.common.domain.exception.AuthorizationException;
import com.bank.app.transfer.application.dto.TransferDetailResponse;
import com.bank.app.transfer.application.exception.TransferNotFoundException;
import com.bank.app.transfer.application.port.in.GetTransferDetailQuery;
import com.bank.app.common.application.port.out.AccountAclPort;
import com.bank.app.common.application.port.out.AccountAclPort.AccountInfo;
import com.bank.app.transfer.application.port.out.LoadTransferPort;
import com.bank.app.transfer.domain.Transfer;
import java.util.Objects;

@ReadOnlyUseCase
public class GetTransferDetailUseCaseImpl implements GetTransferDetailQuery {

    private final LoadTransferPort loadTransferPort;
    private final AccountAclPort accountAclPort;
    private final SecurityContextPort securityContextPort;

    public GetTransferDetailUseCaseImpl(LoadTransferPort loadTransferPort,
                                     AccountAclPort accountAclPort,
                                     SecurityContextPort securityContextPort) {
        this.loadTransferPort = loadTransferPort;
        this.accountAclPort = accountAclPort;
        this.securityContextPort = securityContextPort;
    }

    @Override
    public TransferDetailResponse execute(Long transferId) {
        Objects.requireNonNull(transferId, "Transfer ID null olamaz");
        Transfer transfer = loadTransferPort.findById(transferId)
                .orElseThrow(() -> new TransferNotFoundException(transferId));

        AccountInfo sender = accountAclPort.getAccountInfo(transfer.getSenderAccountId());
        AccountInfo receiver = accountAclPort.getAccountInfo(transfer.getReceiverAccountId());

        Long currentUserId = securityContextPort.getCurrentUserId()
                .orElseThrow(() -> new AuthorizationException("Oturum bulunamadı."));
        if (!currentUserId.equals(sender.userId()) && !currentUserId.equals(receiver.userId())) {
            throw new AuthorizationException("Bu transferin detaylarını görme yetkiniz yok.");
        }

        return TransferDetailResponse.from(transfer);
    }
}
