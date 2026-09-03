package com.lingxi.modules.chat;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 快捷提示词模板。
 */
@Data
@TableName("sys_prompt_template")
public class SysPromptTemplate {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String title;

    private String icon;

    private String content;

    private Integer sort;

    private Integer enabled;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
