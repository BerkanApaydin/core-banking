package com.bank.app.infrastructure.adapter.out.security;

import com.bank.app.common.adapter.in.security.BrowserSessionCookies;
import com.bank.app.infrastructure.adapter.in.security.JwtProperties;
import com.bank.app.user.application.port.out.CsrfBindingPort;
import io.jsonwebtoken.io.Decoders;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;

/**
 * HMAC-bound CSRF tokens (K7/D8). The MAC key is derived from the JWT secret
 * with a fixed domain-separation string, so no new secret must be provisioned
 * and CSRF compromise cannot leak the signing key (nor vice versa).
 */
@Component
public class HmacCsrfBindingAdapter implements CsrfBindingPort {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String DOMAIN = "bank-csrf-binding/v1";

    /**
     * Active key first, then the configured retired JWT keys. Issuing always
     * uses the active key; verification accepts a retired key so a JWT
     * rotation does not 403 every browser mid-session.
     */
    private final List<byte[]> macKeys;

    @Autowired
    public HmacCsrfBindingAdapter(JwtProperties jwtProperties) {
        List<byte[]> keys = new ArrayList<>();
        keys.add(deriveMacKey(jwtProperties.secret()));
        jwtProperties.retiredKeys().values().forEach(
                retiredSecret -> keys.add(deriveMacKey(retiredSecret)));
        this.macKeys = List.copyOf(keys);
    }

    // Isolated unit tests and non-Spring construction (e.g. filter tests):
    // explicit key instead of JwtProperties.
    public HmacCsrfBindingAdapter(byte[] macKey) {
        if (macKey == null || macKey.length == 0) {
            throw new IllegalArgumentException("macKey must not be empty");
        }
        this.macKeys = List.of(macKey.clone());
    }

    @Override
    public String issueCsrfToken(String boundCredential) {
        byte[] rand = new byte[32];
        RANDOM.nextBytes(rand);
        return BrowserSessionCookies.boundToken(rand, macKeys.get(0), boundCredential);
    }

    @Override
    public boolean verifyCsrfToken(String header, String cookie, String boundCredential) {
        for (byte[] macKey : macKeys) {
            if (BrowserSessionCookies.validBoundPair(header, cookie, boundCredential, macKey)) {
                return true;
            }
        }
        return false;
    }

    private static byte[] deriveMacKey(String base64Secret) {
        return hmac(Decoders.BASE64.decode(base64Secret), DOMAIN);
    }

    private static byte[] hmac(byte[] key, String message) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("HMAC-SHA256 unavailable", e);
        }
    }
}
