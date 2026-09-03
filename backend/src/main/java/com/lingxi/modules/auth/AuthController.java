package com.lingxi.modules.auth;

import com.lingxi.common.api.Result;
import com.lingxi.common.ratelimit.RateLimit;
import com.lingxi.modules.user.SysUser;
import com.lingxi.modules.user.SysUserMapper;
import com.lingxi.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Tag(name = "认证")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final SysUserMapper userMapper;

    @Operation(summary = "注册")
    @PostMapping("/register")
    @RateLimit(scope = "register", limit = 10, windowSeconds = 60, by = RateLimit.KeyType.IP)
    public Result<AuthResp> register(@Valid @RequestBody RegisterReq req) {
        return Result.ok(authService.register(req));
    }

    @Operation(summary = "登录")
    @PostMapping("/login")
    @RateLimit(scope = "login", limit = 5, windowSeconds = 60, by = RateLimit.KeyType.IP)
    public Result<AuthResp> login(@Valid @RequestBody LoginReq req) {
        return Result.ok(authService.login(req));
    }

    @Operation(summary = "刷新令牌")
    @PostMapping("/refresh")
    public Result<AuthResp> refresh(@RequestBody Map<String, String> body) {
        return Result.ok(authService.refresh(body.get("refreshToken")));
    }

    @Operation(summary = "当前用户信息")
    @GetMapping("/me")
    public Result<AuthResp.UserVo> me() {
        Long uid = SecurityUtils.currentUserId();
        SysUser user = userMapper.selectById(uid);
        return Result.ok(AuthResp.UserVo.from(user));
    }
}
