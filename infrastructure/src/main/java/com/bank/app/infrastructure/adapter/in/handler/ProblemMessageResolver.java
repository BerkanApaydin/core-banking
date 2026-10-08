package com.bank.app.infrastructure.adapter.in.handler;

import com.bank.app.common.domain.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.context.NoSuchMessageException;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

/**
 * Shared i18n resolution for every problem handler. Extracted from the former
 * monolithic {@code GlobalExceptionHandler} so the split advice classes do not
 * duplicate the fallback contract: missing key, echoed key and empty bundle
 * values all degrade to the caller-supplied generic message, never to raw
 * exception detail.
 */
@Component
public class ProblemMessageResolver {

    private static final Logger log = LoggerFactory.getLogger(ProblemMessageResolver.class);

    private final MessageSource messageSource;

    public ProblemMessageResolver(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    public String resolveMessage(String messageKey, @Nullable Object[] args) {
        if (messageKey == null) return "";
        try {
            return messageSource.getMessage(messageKey, args, LocaleContextHolder.getLocale());
        } catch (NoSuchMessageException e) {
            log.trace("Message not found for key: {}", messageKey, e);
            return messageKey;
        }
    }

    public String resolveMessage(String messageKey) {
        return resolveMessage(messageKey, null);
    }

    public String resolveOrDefault(String messageKey, String fallback) {
        String message = resolveMessage(messageKey);
        if (message == null || message.isEmpty() || message.equals(messageKey)) {
            return fallback;
        }
        return message;
    }

    /**
     * Fail-closed business message resolution (ERR-01): a missing or echoed
     * bundle key must never reflect raw exception detail to the wire — the
     * default message may carry IBANs, balances or other PII. Unmapped keys
     * degrade to the generic catalog message; the raw detail stays in the
     * server log for diagnosis.
     */
    public String resolveBusinessMessage(BusinessException ex) {
        String message = resolveMessage(ex.getMessageKey(), ex.getArgs());
        if (!message.isEmpty() && !message.equals(ex.getMessageKey())) {
            return message;
        }
        log.warn("Missing problem message for key '{}' (code {}); detail: {}",
                ex.getMessageKey(), ex.getErrorCode(), ex.getMessage());
        return resolveOrDefault(
                "error.general_internal_error", "Request could not be completed.");
    }
}
