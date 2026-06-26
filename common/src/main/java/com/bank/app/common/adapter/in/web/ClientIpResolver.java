package com.bank.app.common.adapter.in.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

@Component
public class ClientIpResolver {

    public String resolveClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            return request.getRemoteAddr();
        }
        int commaIndex = ip.indexOf(',');
        return commaIndex != -1 ? ip.substring(0, commaIndex).trim() : ip.trim();
    }
}
