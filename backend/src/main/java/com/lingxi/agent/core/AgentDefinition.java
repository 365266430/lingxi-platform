package com.lingxi.agent.core;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/** 管理端可配置的 Agent 定义。 */
@Data
@TableName("ai_agent")
public class AgentDefinition {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String code;
    private String name;
    private String icon;
    private String description;
    private String systemPrompt;
    private String toolsJson;
    private Integer enabled;
    private Integer builtin;
    private Integer sort;
    private Long createdBy;
    private Long updatedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
