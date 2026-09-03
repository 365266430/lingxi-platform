package com.lingxi.modules.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lingxi.common.api.PageResult;
import com.lingxi.common.api.Result;
import com.lingxi.common.exception.BizException;
import com.lingxi.modules.user.SysUser;
import com.lingxi.modules.user.SysUserMapper;
import com.lingxi.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Tag(name = "系统管理-用户")
@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final SysUserMapper userMapper;

    @Operation(summary = "用户分页")
    @GetMapping
    public Result<PageResult<SysUser>> page(@RequestParam(defaultValue = "1") long current,
                                            @RequestParam(defaultValue = "10") long size,
                                            @RequestParam(required = false) String keyword) {
        Page<SysUser> page = userMapper.selectPage(new Page<>(current, size),
                new LambdaQueryWrapper<SysUser>()
                        .and(keyword != null && !keyword.isBlank(), w -> w
                                .like(SysUser::getUsername, keyword)
                                .or().like(SysUser::getNickname, keyword))
                        .orderByDesc(SysUser::getCreatedAt));
        return Result.ok(PageResult.of(page.getTotal(), page.getCurrent(), page.getSize(), page.getRecords()));
    }

    @Operation(summary = "启用/禁用用户")
    @PutMapping("/{id}/status")
    public Result<Void> updateStatus(@PathVariable Long id, @RequestBody Map<String, Integer> body) {
        if (id.equals(SecurityUtils.currentUserId())) {
            throw new BizException("不能禁用当前登录账号");
        }
        SysUser update = new SysUser();
        update.setId(id);
        update.setEnabled(body.getOrDefault("enabled", 1));
        userMapper.updateById(update);
        return Result.ok();
    }

    @Operation(summary = "修改角色")
    @PutMapping("/{id}/role")
    public Result<Void> updateRole(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String role = body.get("role");
        if (!"ADMIN".equals(role) && !"USER".equals(role)) {
            throw new BizException("角色仅支持 ADMIN / USER");
        }
        SysUser update = new SysUser();
        update.setId(id);
        update.setRole(role);
        userMapper.updateById(update);
        return Result.ok();
    }
}
