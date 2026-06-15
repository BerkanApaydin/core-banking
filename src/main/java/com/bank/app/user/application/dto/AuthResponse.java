package com.bank.app.user.application.dto;

public record AuthResponse(
    String token,
    Long userId,
    String username
) {}
