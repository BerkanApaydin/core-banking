package com.bank.app.infrastructure.adapter.out.security;

import com.bank.app.user.application.port.out.JwtPort;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import jakarta.annotation.PostConstruct;
import io.jsonwebtoken.io.Decoders;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

@Service
public class JwtTokenProvider implements JwtPort {

    private static final String DEFAULT_JWT_SECRET =
            "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";

    private String secretKey;
    private long jwtExpiration;
    private final boolean allowDefaultSecret;
    private volatile SecretKey signingKey;
    private volatile JwtParser verifiedParser;

    public JwtTokenProvider(@Value("${jwt.secret}") String secretKey,
                            @Value("${jwt.expiration:86400000}") long jwtExpiration,
                            @Value("${jwt.allow-default-secret:false}") boolean allowDefaultSecret) {
        this.secretKey = secretKey;
        this.jwtExpiration = jwtExpiration;
        this.allowDefaultSecret = allowDefaultSecret;
    }

    @PostConstruct
    public void validateSecret() {
        if (secretKey == null || secretKey.isBlank()) {
            throw new IllegalStateException(
                "JWT secret must not be blank. Set JWT_SECRET environment variable. "
                + "Generate a secure key with: openssl rand -base64 32");
        }
        if (DEFAULT_JWT_SECRET.equals(secretKey) && !allowDefaultSecret) {
            throw new IllegalStateException(
                "Default JWT secret is not allowed (jwt.allow-default-secret=false). "
                + "Set JWT_SECRET environment variable.");
        }
        byte[] keyBytes = Decoders.BASE64.decode(secretKey);
        if (keyBytes.length < 32) {
            throw new IllegalStateException(
                "JWT secret must be at least 256 bits (32 bytes) when base64-decoded. " +
                "Current key length: " + (keyBytes.length * 8) + " bits. " +
                "Generate a secure key with: openssl rand -base64 32");
        }
        signingKey = Keys.hmacShaKeyFor(keyBytes);
        verifiedParser = Jwts.parser().verifyWith(signingKey).build();
    }

    @Override
    public VerifiedToken verifyAndDecode(String token) {
        if (token == null || token.isBlank()) return null;
        try {
            Claims claims = extractAllClaims(token);
            Number userId = claims.get("userId", Number.class);
            String username = claims.getSubject();
            String role = claims.get("role", String.class);
            Date expiration = claims.getExpiration();
            if (username == null || username.isBlank() || userId == null || role == null
                    || role.isBlank() || expiration == null || !expiration.after(new Date())) {
                return null;
            }
            return new VerifiedToken(username, userId.longValue(), role,
                    claims.getId(), expiration.getTime());
        } catch (Exception invalid) {
            return null;
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
                .id(UUID.randomUUID().toString())
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
    public long getRemainingMs(String token) {
        try {
            long remaining = extractExpiration(token).getTime() - System.currentTimeMillis();
            return Math.max(remaining, 0);
        } catch (Exception e) {
            return 0;
        }
    }

    @Override
    public boolean isTokenValid(String token) {
        return verifyAndDecode(token) != null;
    }

    private Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    private Claims extractAllClaims(String token) {
        JwtParser parser = verifiedParser;
        if (parser == null) {
            validateSecret();
            parser = verifiedParser;
        }
        return parser.parseSignedClaims(token)
                .getPayload();
    }

    private SecretKey getSignInKey() {
        SecretKey key = signingKey;
        if (key == null) {
            validateSecret();
            key = signingKey;
        }
        return key;
    }
}
