package com.bank.app.infrastructure.adapter.out.security;

import com.bank.app.common.application.port.out.JwtPort;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import jakarta.annotation.PostConstruct;
import io.jsonwebtoken.io.Decoders;
import org.springframework.core.env.Environment;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

@Service
public class JwtTokenProvider implements JwtPort {

    private String secretKey;
    private long jwtExpiration;
    private boolean allowDefaultSecret;
    private final Environment environment;

    public JwtTokenProvider(Environment environment,
                      @Value("${jwt.secret}") String secretKey,
                      @Value("${jwt.expiration:86400000}") long jwtExpiration,
                      @Value("${jwt.allow-default-secret:false}") boolean allowDefaultSecret) {
        this.environment = environment;
        this.secretKey = secretKey;
        this.jwtExpiration = jwtExpiration;
        this.allowDefaultSecret = allowDefaultSecret;
    }

    @PostConstruct
    public void validateSecret() {
        String devDefault = "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";
        boolean isProd = Arrays.asList(environment.getActiveProfiles()).contains("prod");

        if (devDefault.equals(secretKey)) {
            if (isProd || !allowDefaultSecret) {
                throw new IllegalStateException(
                    "Default JWT secret is not allowed in production or when allow-default-secret is disabled. " +
                    "Please configure a secure JWT secret key via the JWT_SECRET environment variable.");
            }
            return;
        }

        byte[] keyBytes = Decoders.BASE64.decode(secretKey);
        if (keyBytes.length < 32) {
            throw new IllegalStateException(
                "JWT secret must be at least 256 bits (32 bytes) when base64-decoded. " +
                "Current key length: " + (keyBytes.length * 8) + " bits. " +
                "Generate a secure key with: openssl rand -base64 32");
        }
    }

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    @Override
    public String generateToken(Long userId, String username) {
        return generateToken(userId, username, "ROLE_USER");
    }

    @Override
    public String generateToken(Long userId, String username, String role) {
        Map<String, Object> extraClaims = new HashMap<>();
        extraClaims.put("role", role);
        return generateToken(extraClaims, userId, username);
    }

    public String generateToken(Map<String, Object> extraClaims, Long userId, String username) {
        extraClaims.put("userId", userId);
        return Jwts.builder()
                .claims(extraClaims)
                .subject(username)
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + jwtExpiration))
                .signWith(getSignInKey())
                .compact();
    }

    @Override
    public String extractRole(String token) {
        return extractClaim(token, claims -> claims.get("role", String.class));
    }

    @Override
    public Long extractUserId(String token) {
        Number userId = extractClaim(token, claims -> claims.get("userId", Number.class));
        return userId != null ? userId.longValue() : null;
    }

    @Override
    public long getExpirationMs() {
        return jwtExpiration;
    }

    @Override
    public boolean isTokenValid(String token) {
        try {
            final String username = extractUsername(token);
            return username != null && !isTokenExpired(token);
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    private Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSignInKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private SecretKey getSignInKey() {
        byte[] keyBytes = Decoders.BASE64.decode(secretKey);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
