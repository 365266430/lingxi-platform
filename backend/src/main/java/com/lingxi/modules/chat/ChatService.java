package com.lingxi.modules.chat;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lingxi.agent.core.AgentType;
import com.lingxi.agent.core.AgentDefinitionService;
import com.lingxi.agent.core.ConversationPersistence;
import com.lingxi.common.exception.BizException;
import com.lingxi.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 会话与消息管理。
 */
@Service
@RequiredArgsConstructor
public class ChatService {

    private final ChatSessionMapper sessionMapper;
    private final ChatMessageMapper messageMapper;
    private final ConversationPersistence persistence;
    private final AgentDefinitionService agentDefinitionService;

    public ChatSession createSession(Long userId, SessionCreateReq req) {
        String agentCode = agentDefinitionService.requireRunnable(req.getAgentType());
        ChatSession session = new ChatSession();
        session.setUserId(userId);
        session.setTitle(req.getTitle() == null || req.getTitle().isBlank() ? "新对话" : req.getTitle());
        session.setAgentType(agentCode);
        session.setMessageCount(0);
        sessionMapper.insert(session);
        return session;
    }

    public Page<ChatSession> listSessions(Long userId, long current, long size) {
        return sessionMapper.selectPage(new Page<>(current, size), new LambdaQueryWrapper<ChatSession>()
                .eq(ChatSession::getUserId, userId)
                .orderByDesc(ChatSession::getUpdatedAt));
    }

    public ChatSession getOwned(Long userId, Long sessionId) {
        ChatSession session = sessionMapper.selectById(sessionId);
        if (session == null) {
            throw BizException.notFound("会话");
        }
        if (!session.getUserId().equals(userId) && !SecurityUtils.isAdmin()) {
            throw BizException.forbidden("无权访问该会话");
        }
        return session;
    }

    public List<ChatMessage> listMessages(Long userId, Long sessionId) {
        getOwned(userId, sessionId);
        return messageMapper.selectList(new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getSessionId, sessionId)
                .orderByAsc(ChatMessage::getCreatedAt)
                .orderByAsc(ChatMessage::getId));
    }

    @Transactional
    public void deleteSession(Long userId, Long sessionId) {
        getOwned(userId, sessionId);
        persistence.deleteBySession(sessionId);
        sessionMapper.deleteById(sessionId);
    }

    public ChatMessage saveUserMessage(ChatSession session, String content) {
        ChatMessage message = new ChatMessage();
        message.setSessionId(session.getId());
        message.setUserId(session.getUserId());
        message.setRole("user");
        message.setContent(content);
        message.setRound(0);
        persistence.save(message);
        return message;
    }

    public void refreshCounts(Long sessionId) {
        Long count = messageMapper.selectCount(new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getSessionId, sessionId));
        ChatSession update = new ChatSession();
        update.setId(sessionId);
        update.setMessageCount(count == null ? 0 : count.intValue());
        update.setUpdatedAt(LocalDateTime.now());
        sessionMapper.updateById(update);
    }

    /** 助手消息反馈（👍=1 / 👎=-1 / 0 取消），仅会话归属人可评。 */
    public void feedback(Long userId, Long messageId, Integer feedback) {
        ChatMessage message = messageMapper.selectById(messageId);
        if (message == null) {
            throw BizException.notFound("消息");
        }
        getOwned(userId, message.getSessionId());
        if (!"assistant".equals(message.getRole())) {
            throw new BizException("仅支持对助手消息评价");
        }
        ChatMessage update = new ChatMessage();
        update.setId(messageId);
        update.setFeedback(feedback == null || feedback == 0 ? null : (feedback > 0 ? 1 : -1));
        messageMapper.updateById(update);
    }

    /** 会话导出为 Markdown（含工具调用记录）。 */
    public String exportMarkdown(Long userId, Long sessionId) {
        ChatSession session = getOwned(userId, sessionId);
        List<ChatMessage> messages = listMessages(userId, sessionId);
        StringBuilder sb = new StringBuilder();
        sb.append("# 会话记录：").append(session.getTitle()).append('\n')
                .append("\n- 智能体：").append(AgentType.fromCode(session.getAgentType()).getDisplayName())
                .append("\n- 消息数：").append(messages.size())
                .append("\n- 导出时间：").append(LocalDateTime.now().format(java.time.format.DateTimeFormatter
                        .ofPattern("yyyy-MM-dd HH:mm:ss")))
                .append("\n\n---\n");
        for (ChatMessage m : messages) {
            switch (m.getRole()) {
                case "user" -> sb.append("\n## 🙋 用户\n\n").append(m.getContent()).append('\n');
                case "assistant" -> {
                    sb.append("\n## 🤖 助手");
                    if (m.getRound() != null && m.getRound() > 0) {
                        sb.append("（第 ").append(m.getRound()).append(" 轮）");
                    }
                    sb.append('\n');
                    if (m.getToolCallsJson() != null && !m.getToolCallsJson().isBlank()) {
                        sb.append("\n**工具调用：**\n\n```json\n").append(m.getToolCallsJson()).append("\n```\n");
                    }
                    if (m.getContent() != null && !m.getContent().isBlank()) {
                        sb.append('\n').append(m.getContent()).append('\n');
                    }
                    if (m.getFeedback() != null) {
                        sb.append("\n> 用户反馈：").append(m.getFeedback() > 0 ? "👍 有帮助" : "👎 无帮助").append('\n');
                    }
                }
                case "tool" -> {
                    if (m.getContent() != null && !m.getContent().isBlank()) {
                        sb.append("\n### 🔧 工具返回\n\n```json\n").append(truncate(m.getContent())).append("\n```\n");
                    }
                }
                default -> {
                }
            }
        }
        sb.append("\n---\n\n*由灵犀智能体平台导出*\n");
        return sb.toString();
    }

    private String truncate(String s) {
        return s.length() <= 6000 ? s : s.substring(0, 6000) + "\n…(过长截断)";
    }
}
