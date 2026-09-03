package com.lingxi.modules.auth;

import com.lingxi.common.exception.BizException;
import com.lingxi.config.AppProperties;
import com.lingxi.modules.user.SysUser;
import com.lingxi.modules.user.SysUserMapper;
import com.lingxi.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 认证服务单元测试。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthServiceTest {

    @Mock
    private SysUserMapper userMapper;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private AuthService authService;

    @BeforeEach
    void setUp() {
        AppProperties properties = new AppProperties();
        properties.getJwt().setSecret("lingxi-test-secret-key-0123456789abcdef-0123456789abcdef-0123456789abcdef");
        authService = new AuthService(userMapper, passwordEncoder,
                new JwtService(properties), properties);
    }

    @Test
    @DisplayName("注册成功：默认 USER 角色、密码加密、签发双令牌")
    void registerSuccess() {
        when(userMapper.selectCount(any())).thenReturn(0L);
        AuthResp resp = authService.register(req("newuser", "pass123456"));
        assertThat(resp.getAccessToken()).isNotBlank();
        assertThat(resp.getRefreshToken()).isNotBlank();
        assertThat(resp.getUser().getRole()).isEqualTo("USER");
        assertThat(resp.getUser().getNickname()).isEqualTo("newuser");
        verify(userMapper).insert(any(SysUser.class));
    }

    @Test
    @DisplayName("注册失败：用户名重复返回 409")
    void registerDuplicate() {
        when(userMapper.selectCount(any())).thenReturn(1L);
        assertThatThrownBy(() -> authService.register(req("admin", "pass123456")))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getCode())
                .isEqualTo(409);
    }

    @Test
    @DisplayName("登录失败：密码错误返回 401")
    void loginWrongPassword() {
        SysUser user = buildUser(1L, "admin", passwordEncoder.encode("admin123"), "ADMIN", 1);
        when(userMapper.selectOne(any())).thenReturn(user);
        assertThatThrownBy(() -> authService.login(loginReq("admin", "wrong-password")))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getCode())
                .isEqualTo(401);
    }

    @Test
    @DisplayName("登录成功：签发令牌并记录最后登录时间")
    void loginSuccess() {
        SysUser user = buildUser(1L, "admin", passwordEncoder.encode("admin123"), "ADMIN", 1);
        when(userMapper.selectOne(any())).thenReturn(user);
        AuthResp resp = authService.login(loginReq("admin", "admin123"));
        assertThat(resp.getAccessToken()).isNotBlank();
        assertThat(resp.getUser().getRole()).isEqualTo("ADMIN");
        verify(userMapper).updateById(any(SysUser.class));
    }

    @Test
    @DisplayName("禁用账号登录被拒绝 403")
    void disabledUserRejected() {
        SysUser user = buildUser(2L, "frozen", passwordEncoder.encode("pass123"), "USER", 0);
        when(userMapper.selectOne(any())).thenReturn(user);
        assertThatThrownBy(() -> authService.login(loginReq("frozen", "pass123")))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getCode())
                .isEqualTo(403);
    }

    @Test
    @DisplayName("无效刷新令牌返回 401")
    void invalidRefreshToken() {
        assertThatThrownBy(() -> authService.refresh("not-a-jwt"))
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getCode())
                .isEqualTo(401);
    }

    @Test
    @DisplayName("有效刷新令牌换发新令牌对")
    void refreshSuccess() {
        SysUser user = buildUser(1L, "admin", "x", "ADMIN", 1);
        when(userMapper.selectById(eq(1L))).thenReturn(user);
        String refreshToken = new JwtService(newProps())
                .generateRefreshToken(1L, "admin", "ADMIN");
        AuthResp resp = authService.refresh(refreshToken);
        assertThat(resp.getAccessToken()).isNotBlank();
        assertThat(resp.getRefreshToken()).isNotBlank();
    }

    private AppProperties newProps() {
        AppProperties properties = new AppProperties();
        properties.getJwt().setSecret("lingxi-test-secret-key-0123456789abcdef-0123456789abcdef-0123456789abcdef");
        return properties;
    }

    private RegisterReq req(String username, String password) {
        RegisterReq registerReq = new RegisterReq();
        registerReq.setUsername(username);
        registerReq.setPassword(password);
        return registerReq;
    }

    private LoginReq loginReq(String username, String password) {
        LoginReq loginReq = new LoginReq();
        loginReq.setUsername(username);
        loginReq.setPassword(password);
        return loginReq;
    }

    private SysUser buildUser(Long id, String username, String password, String role, int enabled) {
        SysUser user = new SysUser();
        user.setId(id);
        user.setUsername(username);
        user.setPassword(password);
        user.setRole(role);
        user.setEnabled(enabled);
        user.setNickname(username);
        return user;
    }
}
