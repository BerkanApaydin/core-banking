package com.bank.app.transfer.application.usecase;

import com.bank.app.account.application.usecase.AccountInternalService;
import com.bank.app.account.application.usecase.AccountInternalService.AccountInfo;
import com.bank.app.common.exception.TransferNotFoundException;
import com.bank.app.common.security.SecurityUtils;
import com.bank.app.transfer.application.dto.TransferDetailResponse;
import com.bank.app.transfer.application.port.LoadTransferPort;
import com.bank.app.transfer.domain.Transfer;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Service
@Transactional(readOnly = true)
public class GetTransferDetailUseCase {

    private final LoadTransferPort loadTransferPort;
    private final AccountInternalService accountInternalService;
    private final SecurityUtils securityUtils;

    public GetTransferDetailUseCase(LoadTransferPort loadTransferPort, 
                                     AccountInternalService accountInternalService,
                                     SecurityUtils securityUtils) {
        this.loadTransferPort = loadTransferPort;
        this.accountInternalService = accountInternalService;
        this.securityUtils = securityUtils;
    }

    public TransferDetailResponse execute(Long transferId) {
        Objects.requireNonNull(transferId, "Transfer ID null olamaz");
        Transfer transfer = loadTransferPort.findById(transferId)
                .orElseThrow(() -> new TransferNotFoundException(transferId));

        // Load account metadata through the internal service (decoupled from domain Account entity)
        AccountInfo sender = accountInternalService.getAccountInfo(transfer.getSenderAccountId());
        AccountInfo receiver = accountInternalService.getAccountInfo(transfer.getReceiverAccountId());

        // Check authorization: User must be owner of sender OR receiver account
        Long currentUserId = securityUtils.getCurrentUserId()
                .orElseThrow(() -> new AccessDeniedException("Oturum bulunamadı."));
        if (!currentUserId.equals(sender.userId()) && !currentUserId.equals(receiver.userId())) {
            throw new AccessDeniedException("Bu transferin detaylarını görme yetkiniz yok.");
        }

        return TransferDetailResponse.from(transfer);
    }
}
