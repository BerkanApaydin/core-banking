package com.bank.app.common.adapter.in.security;

/** Shared cookie names and CSRF header without coupling bounded-context adapters. */
public record BrowserSessionCookies(boolean secure) {
    public String sessionCookieName() {
        return secure ? "__Host-BANK_SESSION" : "BANK_SESSION";
    }

    public String csrfCookieName() {
        return secure ? "__Host-BANK_CSRF" : "BANK_CSRF";
    }

    public static final String CSRF_HEADER = "X-CSRF-Token";
}
