package com.bank.app.user.application.port.out;

/**
 * Brute-force guard for the credential check.
 *
 * <p>Three independent budgets, each with a defined job:
 * <ul>
 *   <li><b>IP</b> — stops one host from spraying many accounts.</li>
 *   <li><b>account + IP</b> — stops one host from grinding one account.
 *       Keyed on the pair, so an attacker who burns the budget for one
 *       account from one IP does NOT lock that account out for every other
 *       IP (the plain account counter used to do exactly that, which turned
 *       the guard into a cheap denial-of-service against a victim).</li>
 *   <li><b>account</b> (global, higher threshold) — stops a distributed
 *       attack that spreads attempts over many IPs, which the pair counter
 *       cannot see.</li>
 * </ul>
 */
public interface LoginAttemptPort {
    boolean isIpBlocked(String ip);

    /**
     * Whether the account is blocked for this client.
     *
     * @param ip the client address, or {@code null} when it could not be
     *           resolved (the account-only budget still applies)
     */
    boolean isUsernameBlocked(String username, String ip);

    /**
     * Account-only check (no client address). Applies the global account
     * budget and the pair budget of the "unknown client" bucket.
     */
    default boolean isUsernameBlocked(String username) {
        return isUsernameBlocked(username, null);
    }

    void recordFailure(String ip, String username);

    void reset(String ip);

    void resetByUsername(String username);

    int getWindowMinutes();
}
