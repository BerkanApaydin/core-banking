package com.bank.app.infrastructure.adapter.in.web;

import com.bank.app.infrastructure.adapter.in.handler.ProblemDetailFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ApiVersionValidationFilter implements Filter {

    private final ObjectMapper objectMapper;

    public ApiVersionValidationFilter() {
        this(new ObjectMapper());
    }

    @Autowired
    public ApiVersionValidationFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        // Same normalization as the rate limiter: encoded aliases and context
        // paths must not silently disable version validation either. The
        // "/api/" shape check stays as-is (extractVersionFromPath assumes it).
        String path = RequestPathResolver.resolve(httpRequest);

        if (!path.startsWith("/api/")) {
            chain.doFilter(request, response);
            return;
        }

        String versionHeader = httpRequest.getHeader("X-API-Version");
        if (versionHeader != null && !versionHeader.isBlank()) {
            // extractVersionFromPath never returns null here (path is /api/-prefixed).
            String pathVersion = extractVersionFromPath(path);
            if (!versionHeader.equals(pathVersion)) {
                // Same RFC 7807 shape as every other error body (ProblemDetail):
                // the previous hand-rolled {"status","error","message"} payload
                // forced clients to maintain two parsers. The allowlisted token
                // rule below stays: raw header reflection would break JSON and
                // is an XSS vector if ever rendered.
                String safeHeader = versionHeader.matches("[A-Za-z0-9._-]{1,16}")
                        ? versionHeader : "invalid";
                ProblemDetailFactory.writeProblem(httpResponse, objectMapper,
                        HttpStatus.NOT_ACCEPTABLE, "API_VERSION_MISMATCH",
                        "API version mismatch: X-API-Version header '" + safeHeader
                                + "' does not match requested API version '" + pathVersion + "'",
                        path);
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
