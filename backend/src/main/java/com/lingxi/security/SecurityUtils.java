package com.lingxi.security;

import com.lingxi.common.exception.BizException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 从 SecurityContext 获取当前登录用户。
 */
public final class SecurityUtils {

    public static final String ROLE_ADMIN = "ROLE_ADMIN";

    private SecurityUtils() {
    }

    public static LoginPrincipal current() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof LoginPrincipal p) {
            return p;
        }
        throw new BizException(401, "未登录");
    }

    public static Long currentUserId() {
        return current().userId();
    }

    public static boolean isAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return false;
        }
        for (GrantedAuthority authority : auth.getAuthorities()) {
            if (ROLE_ADMIN.equals(authority.getAuthority())) {
                return true;
            }
        }
        return false;
    }

    /** 认证成功后放入 SecurityContext 的最小用户凭证。 */
    public record LoginPrincipal(Long userId, String username, String role) {
    }
}
