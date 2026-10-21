package com.bank.app.infrastructure.adapter.in.handler;

import java.io.IOException;

/**
 * Signals that a request body exceeded the configured size cap while it was
 * being streamed (no trustworthy {@code Content-Length}, e.g. chunked
 * encoding). The filter throws it from the bounded stream; MVC body converters
 * wrap it in {@code HttpMessageNotReadableException}, so handlers must inspect
 * the cause chain before mapping it to an invalid-format 400.
 */
public class RequestBodyTooLargeException extends IOException {

    private static final long serialVersionUID = 1L;

    public RequestBodyTooLargeException(String message) {
        super(message);
    }
}
