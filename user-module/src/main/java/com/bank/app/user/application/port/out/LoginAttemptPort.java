package com.bank.app.user.application.port.out;

public interface LoginAttemptPort {
    boolean isIpBlocked(String ip);
    boolean isUsernameBlocked(String username);
    void recordFailure(String ip, String username);
    void reset(String ip);
    void resetByUsername(String username);
    int getWindowMinutes();
}
