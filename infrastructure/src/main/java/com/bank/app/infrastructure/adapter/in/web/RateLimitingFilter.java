package com.bank.app.infrastructure.adapter.in.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class RateLimitingFilter implements Filter {

    private final RateLimiter rateLimiter;
    private final MessageSource messageSource;
    private final List<String> rateLimitedPaths;
    private final ObjectMapper objectMapper;
    private final ClientIpResolver clientIpResolver;

    public RateLimitingFilter(RateLimiter rateLimiter,
                              MessageSource messageSource,
                              RateLimitProperties rateLimitProperties,
                              ObjectMapper objectMapper,
                              ClientIpResolver clientIpResolver) {
        this.rateLimiter = rateLimiter;
        this.messageSource = messageSource;
        this.rateLimitedPaths = rateLimitProperties.getPaths();
        this.objectMapper = objectMapper;
        this.clientIpResolver = clientIpResolver;
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

        boolean matchesRateLimitedPath = rateLimitedPaths.stream().anyMatch(path::startsWith);
        if (matchesRateLimitedPath) {
            String ip = clientIpResolver.resolveClientIp(httpRequest);

            if (!rateLimiter.tryAcquire(ip)) {
                String message = messageSource.getMessage("error.rate_limit_exceeded", null,
                        "Çok fazla istek gönderildi. Lütfen daha sonra tekrar deneyin.", LocaleContextHolder.getLocale());
                if (message == null) {
                    message = "Çok fazla istek gönderildi. Lütfen daha sonra tekrar deneyin.";
                }
                httpResponse.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
                httpResponse.setContentType("application/json");
                httpResponse.setCharacterEncoding("UTF-8");
                String json = objectMapper.writeValueAsString(Map.of("status", 429, "message", message));
                httpResponse.getWriter().write(json);
                return;
            }
        }

        chain.doFilter(request, response);
    }

}
