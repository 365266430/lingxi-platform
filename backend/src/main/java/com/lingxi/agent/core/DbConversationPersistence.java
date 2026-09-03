package com.lingxi.agent.core;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lingxi.modules.chat.ChatMessage;
import com.lingxi.modules.chat.ChatMessageMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 基于 MySQL 的对话持久化实现。
 */
@Component
@RequiredArgsConstructor
public class DbConversationPersistence implements ConversationPersistence {

    private final ChatMessageMapper messageMapper;

    @Override
    public void save(ChatMessage message) {
        messageMapper.insert(message);
    }

    @Override
    public List<ChatMessage> loadWindow(Long sessionId, int window) {
        // 先倒序取最近 window 条，再反转为正序
        List<ChatMessage> latest = messageMapper.selectList(new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getSessionId, sessionId)
                .in(ChatMessage::getRole, "user", "assistant")
                .isNotNull(ChatMessage::getContent)
                .orderByDesc(ChatMessage::getCreatedAt)
                .last("LIMIT " + window));
        java.util.Collections.reverse(latest);
        return latest;
    }

    @Override
    public void deleteBySession(Long sessionId) {
        messageMapper.delete(new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getSessionId, sessionId));
    }
}
