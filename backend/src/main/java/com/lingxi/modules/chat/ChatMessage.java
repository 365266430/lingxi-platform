package com.lingxi.modules.chat;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 对话消息。role: user / assistant / tool / system
 */
@Data
@TableName("chat_message")
public class ChatMessage {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long sessionId;

    private Long userId;

    private String role;

    private String content;

    /** assistant 消息附带的工具调用 JSON（便于回放） */
    private String toolCallsJson;

    /** 推理轮次（从 1 开始），用户消息为 0 */
    private Integer round;

    private Integer tokenCount;

    /** 助手消息反馈：1 赞 / -1 踩 / null 未评 */
    private Integer feedback;

    private LocalDateTime createdAt;
}
