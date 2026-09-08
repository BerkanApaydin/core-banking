package com.bank.app.infrastructure.adapter.in.web;

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
            // extractVersionFromPath never returns null here (path is /api/-prefixed).
            String pathVersion = extractVersionFromPath(path);
            if (!versionHeader.equals(pathVersion)) {
                httpResponse.setStatus(HttpStatus.NOT_ACCEPTABLE.value());
                httpResponse.setContentType("application/json");
                httpResponse.setCharacterEncoding("UTF-8");
                // Never reflect the raw header: quote/control characters would
                // break the JSON body (and quoted reflection is an XSS vector
                // if ever rendered). Emit only a safe allowlisted token.
                String safeHeader = versionHeader.matches("[A-Za-z0-9._-]{1,16}")
                        ? versionHeader : "invalid";
                httpResponse.getWriter().write(
                        "{\"status\":406,\"error\":\"API version mismatch\",\"message\":\"X-API-Version header '" +
                        safeHeader + "' does not match requested API version '" + pathVersion + "'\"}");
                return;
            }
        }

        chain.doFilter(request, response);
    }

    @Nullable
    private String extractVersionFromPath(String path) {
        // Caller guarantees the "/api/" prefix (checked in doFilter above).
        String withoutPrefix = path.substring(5);
        int slashIndex = withoutPrefix.indexOf('/');
        if (slashIndex == -1) return withoutPrefix;
        return withoutPrefix.substring(0, slashIndex);
    }
}
