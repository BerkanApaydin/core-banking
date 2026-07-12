package com.bank.app.user.application.port.out;

import jakarta.servlet.http.HttpServletRequest;

public interface ClientIpResolverPort {
    String resolveClientIp(HttpServletRequest request);
}
