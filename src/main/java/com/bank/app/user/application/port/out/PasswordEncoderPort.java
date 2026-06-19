package com.bank.app.user.application.port.out;

public interface PasswordEncoderPort {
    String encode(String rawPassword);
}
