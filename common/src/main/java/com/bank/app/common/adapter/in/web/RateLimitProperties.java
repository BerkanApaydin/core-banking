package com.bank.app.common.adapter.in.web;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "app.security.rate-limit")
public class RateLimitProperties {

    private List<String> paths = List.of(
            "/api/v1/auth/login",
            "/api/v1/auth/register",
            "/api/v1/accounts",
            "/api/v1/transfers"
    );

    private String backend = "caffeine";
    private int maxRequests = 10;
    private int timeWindowMs = 10000;

    public List<String> getPaths() {
        return paths;
    }

    public void setPaths(List<String> paths) {
        this.paths = paths;
    }

    public String getBackend() {
        return backend;
    }

    public void setBackend(String backend) {
        this.backend = backend;
    }

    public int getMaxRequests() {
        return maxRequests;
    }

    public void setMaxRequests(int maxRequests) {
        this.maxRequests = maxRequests;
    }

    public int getTimeWindowMs() {
        return timeWindowMs;
    }

    public void setTimeWindowMs(int timeWindowMs) {
        this.timeWindowMs = timeWindowMs;
    }
}
