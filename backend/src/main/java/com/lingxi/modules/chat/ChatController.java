package com.lingxi.modules.chat;

import com.lingxi.agent.core.AgentType;
import com.lingxi.agent.core.AgentOrchestrator;
import com.lingxi.common.api.PageResult;
import com.lingxi.common.api.Result;
import com.lingxi.common.ratelimit.RateLimit;
import com.lingxi.security.SecurityUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@Tag(name = "对话")
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;
    private final AgentOrchestrator orchestrator;

    @Operation(summary = "创建会话")
    @PostMapping("/sessions")
    public Result<ChatSession> createSession(@RequestBody SessionCreateReq req) {
        return Result.ok(chatService.createSession(SecurityUtils.currentUserId(), req));
    }

    @Operation(summary = "会话列表")
    @GetMapping("/sessions")
    public Result<PageResult<ChatSession>> listSessions(@RequestParam(defaultValue = "1") long current,
                                                        @RequestParam(defaultValue = "20") long size) {
        Page<ChatSession> page = chatService.listSessions(SecurityUtils.currentUserId(), current, size);
        return Result.ok(PageResult.of(page.getTotal(), page.getCurrent(), page.getSize(), page.getRecords()));
    }

    @Operation(summary = "会话消息")
    @GetMapping("/sessions/{id}/messages")
    public Result<List<ChatMessage>> listMessages(@PathVariable Long id) {
        return Result.ok(chatService.listMessages(SecurityUtils.currentUserId(), id));
    }

    @Operation(summary = "删除会话")
    @DeleteMapping("/sessions/{id}")
    public Result<Void> deleteSession(@PathVariable Long id) {
        chatService.deleteSession(SecurityUtils.currentUserId(), id);
        return Result.ok();
    }

    @Operation(summary = "消息反馈（👍/👎）")
    @PostMapping("/messages/{id}/feedback")
    public Result<Void> feedback(@PathVariable Long id, @RequestBody Map<String, Integer> body) {
        chatService.feedback(SecurityUtils.currentUserId(), id, body.get("feedback"));
        return Result.ok();
    }

    @Operation(summary = "导出会话为 Markdown")
    @GetMapping("/sessions/{id}/export")
    public ResponseEntity<byte[]> exportSession(@PathVariable Long id) {
        String markdown = chatService.exportMarkdown(SecurityUtils.currentUserId(), id);
        String encoded = URLEncoder.encode("会话记录-" + id + ".md", StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encoded)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(markdown.getBytes(StandardCharsets.UTF_8));
    }

    @Operation(summary = "对话流式推理（SSE）")
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @RateLimit(scope = "chat-stream", limit = 30, windowSeconds = 60)
    public SseEmitter stream(@RequestParam(required = false) Long sessionId,
                             @RequestParam(required = false) String agentType,
                             @Valid @RequestBody ChatStreamReq req) {
        SecurityUtils.LoginPrincipal principal = SecurityUtils.current();
        ChatSession session;
        if (sessionId == null) {
            SessionCreateReq createReq = new SessionCreateReq();
            createReq.setTitle(abbreviate(req.getContent(), 24));
            createReq.setAgentType(agentType);
            session = chatService.createSession(principal.userId(), createReq);
        } else {
            session = chatService.getOwned(principal.userId(), sessionId);
        }
        chatService.saveUserMessage(session, req.getContent());
        AgentType resolvedAgent = agentType != null && !agentType.isBlank()
                ? AgentType.fromCode(agentType)
                : AgentType.fromCode(session.getAgentType());
        return orchestrator.startStream(session, resolvedAgent);
    }

    private String abbreviate(String s, int max) {
        String clean = s.replaceAll("\\s+", " ").trim();
        return clean.length() <= max ? clean : clean.substring(0, max) + "…";
    }
}
