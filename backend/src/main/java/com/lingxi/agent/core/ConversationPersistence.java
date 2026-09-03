package com.lingxi.agent.core;

import com.lingxi.modules.chat.ChatMessage;

import java.util.List;

/**
 * 对话持久化抽象：隔离 Agent 编排与数据库实现，便于单元测试。
 */
public interface ConversationPersistence {

    void save(ChatMessage message);

    /** 按时间正序加载最近 window 条 USER/ASSISTANT 消息 */
    List<ChatMessage> loadWindow(Long sessionId, int window);

    void deleteBySession(Long sessionId);
}
