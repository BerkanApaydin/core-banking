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
import java.security.SecureRandom;

/**
 * HMAC-bound CSRF tokens (K7/D8). The MAC key is derived from the JWT secret
 * with a fixed domain-separation string, so no new secret must be provisioned
 * and CSRF compromise cannot leak the signing key (nor vice versa).
 */
@Component
public class HmacCsrfBindingAdapter implements CsrfBindingPort {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String DOMAIN = "bank-csrf-binding/v1";

    private final byte[] macKey;

    @Autowired
    public HmacCsrfBindingAdapter(JwtProperties jwtProperties) {
        byte[] secret = Decoders.BASE64.decode(jwtProperties.secret());
        this.macKey = hmac(secret, DOMAIN);
    }

    // Isolated unit tests and non-Spring construction (e.g. filter tests):
    // explicit key instead of JwtProperties.
    public HmacCsrfBindingAdapter(byte[] macKey) {
        if (macKey == null || macKey.length == 0) {
            throw new IllegalArgumentException("macKey must not be empty");
        }
        this.macKey = macKey.clone();
    }

    @Override
    public String issueCsrfToken(String boundCredential) {
        byte[] rand = new byte[32];
        RANDOM.nextBytes(rand);
        return BrowserSessionCookies.boundToken(rand, macKey, boundCredential);
    }

    @Override
    public boolean verifyCsrfToken(String header, String cookie, String boundCredential) {
        return BrowserSessionCookies.validBoundPair(header, cookie, boundCredential, macKey);
    }

    private static byte[] hmac(byte[] key, String message) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
        } catch (java.security.NoSuchAlgorithmException | java.security.InvalidKeyException e) {
            throw new IllegalStateException("HMAC-SHA256 unavailable", e);
        }
    }
}
