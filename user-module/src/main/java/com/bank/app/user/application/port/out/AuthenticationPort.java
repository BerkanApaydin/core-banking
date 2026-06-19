package com.bank.app.user.application.port.out;

public interface AuthenticationPort {
    void authenticate(String username, String password);
}
