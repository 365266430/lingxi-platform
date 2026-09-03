package com.lingxi.modules.auth;

import com.lingxi.modules.user.SysUser;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 登录/注册响应：双 token + 用户信息。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthResp {

    private String accessToken;
    private String refreshToken;
    private Long expiresIn;
    private UserVo user;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserVo {
        private Long id;
        private String username;
        private String nickname;
        private String role;
        private LocalDateTime createdAt;

        public static UserVo from(SysUser user) {
            return UserVo.builder()
                    .id(user.getId())
                    .username(user.getUsername())
                    .nickname(user.getNickname())
                    .role(user.getRole())
                    .createdAt(user.getCreatedAt())
                    .build();
        }
    }
}
