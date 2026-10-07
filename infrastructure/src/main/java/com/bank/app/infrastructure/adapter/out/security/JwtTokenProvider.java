package com.bank.app.infrastructure.adapter.out.security;

import com.bank.app.infrastructure.adapter.in.security.JwtProperties;
import com.bank.app.user.application.port.out.JwtPort;
import org.springframework.beans.factory.annotation.Autowired;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import jakarta.annotation.PostConstruct;
import io.jsonwebtoken.io.Decoders;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/**
 * JWT issuance and verification behind {@link JwtPort}.
 *
 * <p>The remaining narrow readers ({@code extractUsername},
 * {@code extractTokenType}, {@code getRemainingMs}) all re-verify the
 * signature on every call and fail closed (null/zero on any JWT failure), so
 * no caller can ever act on unverified claims. In particular
 * {@code getRemainingMs} must re-verify rather than trust a presented
 * expiry — a TTL read from an unverified token would be attacker-controlled.
 * Anything beyond these goes through {@code verifyAndDecode}: single-claim
 * extractors ({@code extractRole}, {@code extractUserId}) and the boolean
 * {@code isTokenValid} were removed as dead surface — every production caller
 * already used the verified token.
 */
@Service
public class JwtTokenProvider implements JwtPort {

    /**
     * Local-development fallback secret. Single owner of this value: startup
     * validation references this constant instead of duplicating the literal,
     * so the two can never silently diverge. Never use in production
     * ({@code jwt.allow-default-secret=false} fails fast; rotate immediately
     * if this value ever leaks — it lives in git history and images).
     */
    public static final String DEFAULT_JWT_SECRET =
            "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";

    /** Token type claim distinguishing access from refresh tokens. */
    public static final String TOKEN_TYPE_CLAIM = "typ";
    public static final String TOKEN_TYPE_ACCESS = "access";
    public static final String TOKEN_TYPE_REFRESH = "refresh";
    /** Token generation claim (V39): bumped on role/password changes. */
    public static final String TOKEN_VERSION_CLAIM = "ver";

    private String secretKey;
    private long accessExpiration;
    private long refreshExpiration;
    private final boolean allowDefaultSecret;
    private volatile SecretKey signingKey;
    private volatile JwtParser verifiedParser;

    // Primary constructor: typed properties (D13/K14) — no @Value scatter.
    @Autowired
    public JwtTokenProvider(JwtProperties properties) {
        this(properties.secret(), properties.accessExpiration(),
                properties.refreshExpiration(), properties.allowDefaultSecret());
    }

    // Legacy constructor kept for isolated unit tests (no Spring context).
    public JwtTokenProvider(String secretKey, long accessExpiration,
                            long refreshExpiration, boolean allowDefaultSecret) {
        this.secretKey = secretKey;
        this.accessExpiration = accessExpiration;
        this.refreshExpiration = refreshExpiration;
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
        } catch (JwtException | IllegalArgumentException invalid) {
            // Signature/expiry/claim problems mean "invalid token". Catching
            // only JWT failures (never bare Exception) keeps programming
            // errors visible instead of masking them as authentication noise.
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
        return generateToken(userId, username, role, 0L);
    }

    @Override
    public String generateToken(Long userId, String username, String role, long tokenVersion) {
        Map<String, Object> extraClaims = new HashMap<>();
        extraClaims.put("role", role);
        extraClaims.put(TOKEN_VERSION_CLAIM, tokenVersion);
        return generateToken(extraClaims, userId, username, TOKEN_TYPE_ACCESS, accessExpiration);
    }

    @Override
    public String generateRefreshToken(Long userId, String username, String role) {
        return generateRefreshToken(userId, username, role, 0L);
    }

    @Override
    public String generateRefreshToken(Long userId, String username, String role, long tokenVersion) {
        Map<String, Object> extraClaims = new HashMap<>();
        extraClaims.put("role", role);
        extraClaims.put(TOKEN_VERSION_CLAIM, tokenVersion);
        return generateToken(extraClaims, userId, username, TOKEN_TYPE_REFRESH, refreshExpiration);
    }

    public String generateToken(Map<String, Object> extraClaims, Long userId, String username) {
        return generateToken(extraClaims, userId, username, TOKEN_TYPE_ACCESS, accessExpiration);
    }

    private String generateToken(Map<String, Object> extraClaims, Long userId, String username,
                                 String tokenType, long ttlMs) {
        extraClaims.put("userId", userId);
        extraClaims.put(TOKEN_TYPE_CLAIM, tokenType);
        return Jwts.builder()
                .claims(extraClaims)
                .subject(username)
                .id(UUID.randomUUID().toString())
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + ttlMs))
                .signWith(getSignInKey())
                .compact();
    }

    @Override
    public String extractTokenType(String token) {
        try {
            String type = extractClaim(token, claims -> claims.get(TOKEN_TYPE_CLAIM, String.class));
            return (type == null || type.isBlank()) ? TOKEN_TYPE_ACCESS : type;
        } catch (JwtException | IllegalArgumentException invalid) {
            // Unverifiable tokens have no trustworthy type; callers validate
            // the signature first (verifyAndDecode) and treat these as access.
            return TOKEN_TYPE_ACCESS;
        }
    }

    @Override
    public long extractTokenVersion(String token) {
        try {
            Number version = extractClaim(token, claims -> claims.get(TOKEN_VERSION_CLAIM, Number.class));
            // Absent on pre-versioning tokens (rolling deploys) and on forged
            // shapes that passed type checks: both mean generation 0.
            return version == null ? 0L : Math.max(version.longValue(), 0L);
        } catch (JwtException | IllegalArgumentException invalid) {
            return 0L;
        }
    }

    @Override
    public long getExpirationMs() {
        return accessExpiration;
    }

    @Override
    public long getRefreshExpirationMs() {
        return refreshExpiration;
    }

    @Override
    public long getRemainingMs(String token) {
        try {
            long remaining = extractExpiration(token).getTime() - System.currentTimeMillis();
            return Math.max(remaining, 0);
        } catch (JwtException | IllegalArgumentException e) {
            return 0;
        }
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
