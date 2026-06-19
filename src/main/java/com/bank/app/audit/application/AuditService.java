package com.bank.app.audit.application;

import com.bank.app.audit.application.port.SaveAuditLogPort;
import com.bank.app.audit.domain.AuditAction;
import com.bank.app.audit.domain.AuditLog;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditService {

    private final SaveAuditLogPort saveAuditLogPort;

    public AuditService(SaveAuditLogPort saveAuditLogPort) {
        this.saveAuditLogPort = saveAuditLogPort;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(AuditAction action, String details) {
        String username = getCurrentUsername();
        log(username, action, details);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(String username, AuditAction action, String details) {
        AuditLog auditLog = AuditLog.create(username, action, details);
        saveAuditLogPort.save(auditLog);
    }

    private String getCurrentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() &&
                !(auth instanceof AnonymousAuthenticationToken) &&
                !"anonymousUser".equals(auth.getName())) {
            return auth.getName();
        }
        return "system";
    }
}
