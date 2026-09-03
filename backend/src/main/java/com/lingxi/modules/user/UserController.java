package com.lingxi.modules.user;

import com.lingxi.common.api.Result;
import com.lingxi.common.exception.BizException;
import com.lingxi.security.SecurityUtils;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Map;

@Tag(name = "个人中心")
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final SysUserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    public record ProfileResp(Long id, String username, String nickname, String role, LocalDateTime createdAt) {
    }

    @Operation(summary = "我的资料")
    @GetMapping("/me")
    public Result<ProfileResp> profile() {
        SysUser user = mustCurrentUser();
        return Result.ok(new ProfileResp(user.getId(), user.getUsername(),
                user.getNickname(), user.getRole(), user.getCreatedAt()));
    }

    @Operation(summary = "修改昵称")
    @PutMapping("/me")
    public Result<ProfileResp> updateProfile(@RequestBody Map<String, String> body) {
        SysUser user = mustCurrentUser();
        String nickname = body.get("nickname");
        if (nickname == null || nickname.isBlank()) {
            throw new BizException("昵称不能为空");
        }
        SysUser update = new SysUser();
        update.setId(user.getId());
        update.setNickname(nickname.trim());
        userMapper.updateById(update);
        return profile();
    }

    @Operation(summary = "修改密码")
    @PutMapping("/me/password")
    public Result<Void> changePassword(@RequestBody ChangePasswordReq req) {
        SysUser user = mustCurrentUser();
        if (!passwordEncoder.matches(req.getOldPassword(), user.getPassword())) {
            throw new BizException("原密码不正确");
        }
        if (req.getNewPassword() == null || req.getNewPassword().length() < 6
                || req.getNewPassword().length() > 64) {
            throw new BizException("新密码长度需在 6-64 位之间");
        }
        SysUser update = new SysUser();
        update.setId(user.getId());
        update.setPassword(passwordEncoder.encode(req.getNewPassword()));
        userMapper.updateById(update);
        return Result.ok();
    }

    private SysUser mustCurrentUser() {
        SysUser user = userMapper.selectById(SecurityUtils.currentUserId());
        if (user == null) {
            throw new BizException(401, "用户不存在");
        }
        return user;
    }

    @Data
    public static class ChangePasswordReq {
        private String oldPassword;
        private String newPassword;
    }
}
