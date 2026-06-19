package com.bank.app.common.security;

import com.bank.app.common.security.port.out.JwtPort;
import com.bank.app.user.domain.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
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
                      @Value("${jwt.secret:404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970}") String secretKey,
                      @Value("${jwt.expiration:86400000}") long jwtExpiration,
                      @Value("${jwt.allow-default-secret:false}") boolean allowDefaultSecret) {
        this.environment = environment;
        this.secretKey = secretKey;
        this.jwtExpiration = jwtExpiration;
        this.allowDefaultSecret = allowDefaultSecret;
    }

    @PostConstruct
    public void validateSecret() {
        boolean isProd = Arrays.asList(environment.getActiveProfiles()).contains("prod");
        if (isProd || !allowDefaultSecret) {
            String defaultSecret = "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";
            if (defaultSecret.equals(secretKey)) {
                throw new IllegalStateException("Default JWT secret is not allowed in production or when allow-default-secret is disabled. Please configure a secure JWT secret key.");
            }
        }
    }

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    public String generateToken(User user) {
        return generateToken(new HashMap<>(), user);
    }

    public String generateToken(Map<String, Object> extraClaims, User user) {
        extraClaims.put("userId", user.getId());
        return Jwts.builder()
                .claims(extraClaims)
                .subject(user.getUsername())
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + jwtExpiration))
                .signWith(getSignInKey())
                .compact();
    }

    public boolean isTokenValid(String token, UserDetails userDetails) {
        final String username = extractUsername(token);
        return (username.equals(userDetails.getUsername())) && !isTokenExpired(token);
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
