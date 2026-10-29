package com.bank.app.infrastructure.adapter.in.audit;

import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * External audit-anchor configuration (F1). Disabled by default so existing
 * deployments do not require a WORM mount; enabling it requires a file path
 * on append-only/object-lock storage plus an HMAC key that is independent of
 * the JWT signing secret.
 */
@Validated
@ConfigurationProperties(prefix = "app.audit.anchor")
public record AuditAnchorProperties(
        @DefaultValue("false") boolean enabled,
        String file,
        @DefaultValue("audit-anchor-v1") String keyId,
        String secret,
        Map<String, String> retiredKeys
) {

    public AuditAnchorProperties {
        if (retiredKeys == null || retiredKeys.isEmpty()) {
            retiredKeys = Map.of();
        } else {
            retiredKeys = Map.copyOf(retiredKeys);
        }

        if (keyId == null || keyId.isBlank()) {
            throw new IllegalArgumentException("Audit anchor key id must not be blank");
        }
        if (keyId.indexOf('|') >= 0) {
            throw new IllegalArgumentException("Audit anchor key id must not contain '|'");
        }
        retiredKeys.forEach((retiredKeyId, retiredSecret) -> {
            if (retiredKeyId == null || retiredKeyId.isBlank()) {
                throw new IllegalArgumentException("Audit anchor retired key id must not be blank");
            }
            if (retiredKeyId.indexOf('|') >= 0) {
                throw new IllegalArgumentException(
                        "Audit anchor retired key id must not contain '|'");
            }
            if (retiredSecret == null || retiredSecret.isBlank()) {
                throw new IllegalArgumentException(
                        "Audit anchor retired secret must not be blank for key id: " + retiredKeyId);
            }
        });
        if (retiredKeys.containsKey(keyId)) {
            throw new IllegalArgumentException(
                    "Audit anchor retired keys must not contain the active key id: " + keyId);
        }

        if (enabled) {
            if (file == null || file.isBlank()) {
                throw new IllegalArgumentException(
                        "Audit anchor file must be set when app.audit.anchor.enabled=true");
            }
            if (secret == null || secret.isBlank()) {
                throw new IllegalArgumentException(
                        "Audit anchor secret must be set when app.audit.anchor.enabled=true");
            }
        }
    }
}
