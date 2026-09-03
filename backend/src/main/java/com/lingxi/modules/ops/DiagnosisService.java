package com.lingxi.modules.ops;

import com.lingxi.agent.core.AgentType;
import com.lingxi.modules.chat.ChatService;
import com.lingxi.modules.chat.ChatSession;
import com.lingxi.modules.chat.SessionCreateReq;
import com.lingxi.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 一键 AI 诊断：为告警创建专属诊断会话并生成预置提问。
 */
@Service
@RequiredArgsConstructor
public class DiagnosisService {

    private final OpsQueryService opsQueryService;
    private final ChatService chatService;

    public Map<String, Object> diagnose(Long alertId) {
        OpsAlert alert = opsQueryService.getAlert(alertId);
        SecurityUtils.LoginPrincipal principal = SecurityUtils.current();

        SessionCreateReq createReq = new SessionCreateReq();
        createReq.setTitle("诊断 · " + alert.getTitle());
        createReq.setAgentType(AgentType.OPS_DIAGNOSIS.code());
        ChatSession session = chatService.createSession(principal.userId(), createReq);

        String prompt = """
                请诊断以下线上告警：
                - 告警：[%s] %s
                - 服务：%s
                - 主机：%s
                - 指标：%s 当前值 %s
                - 开始时间：%s
                - 描述：%s

                请先查询该服务的指标走势与相关处置手册，给出根因分析、影响面评估和分步骤处置方案。
                """.formatted(
                alert.getSeverity(), alert.getTitle(), alert.getServiceName(), alert.getHostName(),
                alert.getMetricName(), alert.getMetricValue(), alert.getStartedAt(),
                alert.getDescription() == null ? "无" : alert.getDescription());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("sessionId", session.getId());
        result.put("sessionTitle", session.getTitle());
        result.put("agentType", AgentType.OPS_DIAGNOSIS.code());
        result.put("prompt", prompt);
        return result;
    }
}
