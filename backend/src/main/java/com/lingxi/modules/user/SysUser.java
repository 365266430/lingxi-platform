package com.lingxi.modules.user;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 系统用户。
 */
@Data
@TableName("sys_user")
public class SysUser {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String username;

    @JsonIgnore
    private String password;

    private String nickname;

    /** ADMIN | USER */
    private String role;

    /** 1 启用 0 禁用 */
    private Integer enabled;

    private LocalDateTime lastLoginAt;

    @TableLogic
    @JsonIgnore
    private Integer deleted;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
