package com.bank.app.common.adapter.in.web;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ApiVersionValidationFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        String path = httpRequest.getRequestURI();

        if (!path.startsWith("/api/")) {
            chain.doFilter(request, response);
            return;
        }

        String versionHeader = httpRequest.getHeader("X-API-Version");
        if (versionHeader != null && !versionHeader.isBlank()) {
            String pathVersion = extractVersionFromPath(path);
            if (pathVersion != null && !versionHeader.equals(pathVersion)) {
                httpResponse.setStatus(HttpStatus.NOT_ACCEPTABLE.value());
                httpResponse.setContentType("application/json");
                httpResponse.setCharacterEncoding("UTF-8");
                httpResponse.getWriter().write(
                        "{\"status\":406,\"error\":\"API version mismatch\",\"message\":\"X-API-Version header '" +
                        versionHeader + "' does not match requested API version '" + pathVersion + "'\"}");
                return;
            }
        }

        chain.doFilter(request, response);
    }

    @Nullable
    private String extractVersionFromPath(String path) {
        if (!path.startsWith("/api/")) return null;
        String withoutPrefix = path.substring(5);
        int slashIndex = withoutPrefix.indexOf('/');
        if (slashIndex == -1) return withoutPrefix;
        return withoutPrefix.substring(0, slashIndex);
    }
}
