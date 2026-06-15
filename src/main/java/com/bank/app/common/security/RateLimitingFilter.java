package com.bank.app.common.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class RateLimitingFilter implements Filter {

    private static final int MAX_REQUESTS = 10;
    private static final long TIME_WINDOW_MS = 10000; // 10 seconds

    private final Cache<String, RateLimitInfo> cache = Caffeine.newBuilder()
            .expireAfterWrite(TIME_WINDOW_MS, TimeUnit.MILLISECONDS)
            .maximumSize(10000)
            .build();

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        String path = httpRequest.getRequestURI();
        
        // Rate limit auth endpoints and transfer endpoints
        if (path.startsWith("/api/v1/auth/login") || 
                path.startsWith("/api/v1/auth/register") || 
                path.startsWith("/api/v1/transfers")) {
            String ip = httpRequest.getHeader("X-Forwarded-For");
            if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
                ip = httpRequest.getRemoteAddr();
            } else {
                int commaIndex = ip.indexOf(',');
                if (commaIndex != -1) {
                    ip = ip.substring(0, commaIndex).trim();
                }
            }

            RateLimitInfo info = cache.asMap().compute(ip, (k, v) -> {
                if (v == null || v.isExpired()) {
                    return new RateLimitInfo(1, TIME_WINDOW_MS);
                } else {
                    v.requestCount.incrementAndGet();
                    return v;
                }
            });

            if (info.requestCount.get() > MAX_REQUESTS) {
                httpResponse.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
                httpResponse.setContentType("application/json");
                httpResponse.setCharacterEncoding("UTF-8");
                httpResponse.getWriter().write("{\"status\":429,\"message\":\"Çok fazla istek gönderildi. Lütfen daha sonra tekrar deneyin.\"}");
                return;
            }
        }

        chain.doFilter(request, response);
    }

    private static class RateLimitInfo {
        final long resetTime;
        final AtomicInteger requestCount;

        RateLimitInfo(int count, long durationMs) {
            this.resetTime = System.currentTimeMillis() + durationMs;
            this.requestCount = new AtomicInteger(count);
        }

        boolean isExpired() {
            return System.currentTimeMillis() > resetTime;
        }
    }
}
