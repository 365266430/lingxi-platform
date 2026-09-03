package com.lingxi.security;

import com.lingxi.config.AppProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * JWT 签发与校验（HS256）。
 */
@Service
@RequiredArgsConstructor
public class JwtService {

    public static final String CLAIM_UID = "uid";
    public static final String CLAIM_ROLE = "role";
    public static final String CLAIM_TYPE = "typ";

    private final AppProperties properties;

    private SecretKey key() {
        return Keys.hmacShaKeyFor(properties.getJwt().getSecret().getBytes(StandardCharsets.UTF_8));
    }

    public String generateAccessToken(Long userId, String username, String role) {
        return build(userId, username, role, "access",
                properties.getJwt().getAccessTokenTtlMinutes() * 60_000L);
    }

    public String generateRefreshToken(Long userId, String username, String role) {
        return build(userId, username, role, "refresh",
                properties.getJwt().getRefreshTokenTtlDays() * 24 * 3600_000L);
    }

    private String build(Long userId, String username, String role, String type, long ttlMillis) {
        Map<String, Object> claims = new HashMap<>();
        claims.put(CLAIM_UID, userId);
        claims.put(CLAIM_ROLE, role);
        claims.put(CLAIM_TYPE, type);
        Date now = new Date();
        return Jwts.builder()
                .subject(username)
                .claims(claims)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + ttlMillis))
                .signWith(key())
                .compact();
    }

    /**
     * 解析并校验 token；type 为期望类型（access/refresh），不匹配抛 JwtException。
     */
    public Claims parse(String token, String expectedType) {
        Claims claims = Jwts.parser()
                .verifyWith(key())
                .build()
                .parseSignedClaims(token)
                .getPayload();
        if (expectedType != null && !expectedType.equals(claims.get(CLAIM_TYPE, String.class))) {
            throw new JwtException("token 类型不匹配");
        }
        return claims;
    }
}
