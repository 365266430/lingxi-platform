package com.lingxi.security;

import com.lingxi.config.AppProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * JWT 服务单元测试。
 */
class JwtServiceTest {

    private static final String SECRET =
            "lingxi-test-secret-key-0123456789abcdef-0123456789abcdef-0123456789abcdef";

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        AppProperties properties = new AppProperties();
        properties.getJwt().setSecret(SECRET);
        jwtService = new JwtService(properties);
    }

    @Test
    @DisplayName("签发并解析访问令牌，携带用户与角色声明")
    void generateAndParseAccessToken() {
        String token = jwtService.generateAccessToken(10086L, "alice", "ADMIN");
        Claims claims = jwtService.parse(token, "access");
        assertThat(claims.getSubject()).isEqualTo("alice");
        assertThat(claims.get(JwtService.CLAIM_UID, Long.class)).isEqualTo(10086L);
        assertThat(claims.get(JwtService.CLAIM_ROLE, String.class)).isEqualTo("ADMIN");
    }

    @Test
    @DisplayName("令牌类型不匹配时拒绝解析（access 不能当 refresh 用）")
    void rejectTypeMismatch() {
        String access = jwtService.generateAccessToken(1L, "bob", "USER");
        assertThatThrownBy(() -> jwtService.parse(access, "refresh")).isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("被篡改的令牌被拒绝")
    void rejectTamperedToken() {
        String token = jwtService.generateAccessToken(1L, "bob", "USER");
        String tampered = token.substring(0, token.length() - 4) + "AAAA";
        assertThatThrownBy(() -> jwtService.parse(tampered, "access")).isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("刷新令牌可正常签发解析")
    void refreshTokenRoundTrip() {
        String refresh = jwtService.generateRefreshToken(7L, "carol", "USER");
        Claims claims = jwtService.parse(refresh, "refresh");
        assertThat(claims.getSubject()).isEqualTo("carol");
        assertThat(claims.get(JwtService.CLAIM_UID, Long.class)).isEqualTo(7L);
    }
}
