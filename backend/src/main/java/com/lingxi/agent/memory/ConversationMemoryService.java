package com.lingxi.agent.memory;

import com.lingxi.agent.core.ConversationPersistence;
import com.lingxi.config.AppProperties;
import com.lingxi.modules.chat.ChatMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 对话记忆：滑窗加载近期消息并转换为 Spring AI Message，超预算时丢弃最旧消息。
 */
@Service
@RequiredArgsConstructor
public class ConversationMemoryService {

    private final ConversationPersistence persistence;
    private final AppProperties properties;

    public List<Message> buildWindow(Long sessionId) {
        List<ChatMessage> recent = persistence.loadWindow(sessionId, properties.getAgent().getMemoryWindow());
        int budget = properties.getAgent().getHistoryMaxChars();
        List<Message> messages = new ArrayList<>();
        int used = 0;
        for (int i = recent.size() - 1; i >= 0; i--) {
            ChatMessage m = recent.get(i);
            String content = m.getContent() == null ? "" : m.getContent();
            if (used + content.length() > budget && !messages.isEmpty()) {
                break;
            }
            used += content.length();
            messages.add(0, "user".equals(m.getRole()) ? new UserMessage(content) : new AssistantMessage(content));
        }
        return messages;
    }
}
