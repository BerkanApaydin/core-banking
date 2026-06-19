package com.bank.app.common.web;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class RateLimitingFilter implements Filter {

    private final RateLimiter rateLimiter;
    private final MessageSource messageSource;

    public RateLimitingFilter(RateLimiter rateLimiter, MessageSource messageSource) {
        this.rateLimiter = rateLimiter;
        this.messageSource = messageSource;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        String method = httpRequest.getMethod();
        String path = httpRequest.getRequestURI();

        boolean isWriteOperation = "POST".equals(method) || "PUT".equals(method) || "DELETE".equals(method) || "PATCH".equals(method);
        if (!isWriteOperation) {
            chain.doFilter(request, response);
            return;
        }

        if (path.startsWith("/api/v1/auth/login")
                || path.startsWith("/api/v1/auth/register")
                || path.startsWith("/api/v1/accounts")
                || path.startsWith("/api/v1/transfers")) {
            String ip = resolveClientIp(httpRequest);

            if (!rateLimiter.tryAcquire(ip)) {
                String message = messageSource.getMessage("error.rate_limit_exceeded", null,
                        "Çok fazla istek gönderildi. Lütfen daha sonra tekrar deneyin.", LocaleContextHolder.getLocale());
                httpResponse.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
                httpResponse.setContentType("application/json");
                httpResponse.setCharacterEncoding("UTF-8");
                httpResponse.getWriter().write(
                        "{\"status\":429,\"message\":\"" + message + "\"}");
                return;
            }
        }

        chain.doFilter(request, response);
    }

    private String resolveClientIp(HttpServletRequest httpRequest) {
        String ip = httpRequest.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            return httpRequest.getRemoteAddr();
        }
        int commaIndex = ip.indexOf(',');
        return commaIndex != -1 ? ip.substring(0, commaIndex).trim() : ip.trim();
    }
}
