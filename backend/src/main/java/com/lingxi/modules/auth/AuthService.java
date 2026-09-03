package com.lingxi.modules.auth;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lingxi.common.exception.BizException;
import com.lingxi.config.AppProperties;
import com.lingxi.modules.user.SysUser;
import com.lingxi.modules.user.SysUserMapper;
import com.lingxi.security.JwtService;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 注册 / 登录 / 刷新令牌。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final SysUserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AppProperties properties;

    public AuthResp register(RegisterReq req) {
        Long exists = userMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, req.getUsername()));
        if (exists != null && exists > 0) {
            throw new BizException(409, "用户名已被占用");
        }
        SysUser user = new SysUser();
        user.setUsername(req.getUsername());
        user.setPassword(passwordEncoder.encode(req.getPassword()));
        user.setNickname(req.getNickname() == null || req.getNickname().isBlank()
                ? req.getUsername() : req.getNickname());
        user.setRole("USER");
        user.setEnabled(1);
        userMapper.insert(user);
        log.info("新用户注册: {}", user.getUsername());
        return issueTokens(user);
    }

    public AuthResp login(LoginReq req) {
        SysUser user = userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, req.getUsername()));
        if (user == null || !passwordEncoder.matches(req.getPassword(), user.getPassword())) {
            throw new BizException(401, "用户名或密码错误");
        }
        if (user.getEnabled() == null || user.getEnabled() != 1) {
            throw new BizException(403, "账号已被禁用，请联系管理员");
        }
        SysUser update = new SysUser();
        update.setId(user.getId());
        update.setLastLoginAt(LocalDateTime.now());
        userMapper.updateById(update);
        return issueTokens(user);
    }

    public AuthResp refresh(String refreshToken) {
        Claims claims;
        try {
            claims = jwtService.parse(refreshToken, "refresh");
        } catch (Exception e) {
            throw new BizException(401, "刷新令牌无效或已过期");
        }
        Long uid = claims.get(JwtService.CLAIM_UID, Long.class);
        SysUser user = userMapper.selectById(uid);
        if (user == null || user.getEnabled() == null || user.getEnabled() != 1) {
            throw new BizException(401, "用户不存在或已禁用");
        }
        return issueTokens(user);
    }

    private AuthResp issueTokens(SysUser user) {
        String access = jwtService.generateAccessToken(user.getId(), user.getUsername(), user.getRole());
        String refresh = jwtService.generateRefreshToken(user.getId(), user.getUsername(), user.getRole());
        return AuthResp.builder()
                .accessToken(access)
                .refreshToken(refresh)
                .expiresIn(properties.getJwt().getAccessTokenTtlMinutes() * 60L)
                .user(AuthResp.UserVo.from(user))
                .build();
    }
}
