package com.lingxi.modules.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingxi.modules.chat.ChatMessage;
import com.lingxi.modules.chat.ChatMessageMapper;
import com.lingxi.modules.chat.ChatSession;
import com.lingxi.modules.chat.ChatSessionMapper;
import com.lingxi.modules.ops.OpsAlert;
import com.lingxi.modules.ops.OpsAlertMapper;
import com.lingxi.modules.report.Report;
import com.lingxi.modules.report.ReportMapper;
import com.lingxi.modules.user.SysUser;
import com.lingxi.modules.user.SysUserMapper;
import com.lingxi.rag.KbChunk;
import com.lingxi.rag.KbChunkMapper;
import com.lingxi.rag.KbDocument;
import com.lingxi.rag.KbDocumentMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 仪表盘统计（Redis 缓存 30s）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardService {

    private static final String CACHE_KEY = "lingxi:dashboard:summary";

    private final OpsAlertMapper alertMapper;
    private final ChatSessionMapper sessionMapper;
    private final ChatMessageMapper messageMapper;
    private final KbDocumentMapper documentMapper;
    private final KbChunkMapper chunkMapper;
    private final ReportMapper reportMapper;
    private final SysUserMapper userMapper;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @SuppressWarnings("unchecked")
    public Map<String, Object> summary() {
        try {
            String cached = redisTemplate.opsForValue().get(CACHE_KEY);
            if (cached != null) {
                return objectMapper.readValue(cached, Map.class);
            }
        } catch (Exception e) {
            log.debug("仪表盘缓存读取失败: {}", e.getMessage());
        }
        Map<String, Object> summary = compute();
        try {
            redisTemplate.opsForValue().set(CACHE_KEY, objectMapper.writeValueAsString(summary), Duration.ofSeconds(30));
        } catch (Exception e) {
            log.debug("仪表盘缓存写入失败: {}", e.getMessage());
        }
        return summary;
    }

    private Map<String, Object> compute() {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("activeAlerts", alertMapper.selectCount(new LambdaQueryWrapper<OpsAlert>()
                .eq(OpsAlert::getStatus, "ACTIVE")));
        summary.put("criticalAlerts", alertMapper.selectCount(new LambdaQueryWrapper<OpsAlert>()
                .eq(OpsAlert::getStatus, "ACTIVE").eq(OpsAlert::getSeverity, "P1")));
        summary.put("totalUsers", userMapper.selectCount(null));
        summary.put("totalSessions", sessionMapper.selectCount(null));
        summary.put("messages24h", messageMapper.selectCount(new LambdaQueryWrapper<ChatMessage>()
                .ge(ChatMessage::getCreatedAt, LocalDateTime.now().minusHours(24))));
        summary.put("totalDocuments", documentMapper.selectCount(new LambdaQueryWrapper<KbDocument>()));
        summary.put("totalChunks", chunkMapper.selectCount(new LambdaQueryWrapper<KbChunk>()));
        summary.put("totalReports", reportMapper.selectCount(new LambdaQueryWrapper<Report>()));
        summary.put("totalMessages", messageMapper.selectCount(new LambdaQueryWrapper<ChatMessage>()));
        summary.put("feedbackUp", messageMapper.selectCount(new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getFeedback, 1)));
        summary.put("feedbackDown", messageMapper.selectCount(new LambdaQueryWrapper<ChatMessage>()
                .eq(ChatMessage::getFeedback, -1)));

        LocalDateTime sevenDaysAgo = LocalDateTime.now().minusDays(7);
        List<Map<String, Object>> trend = alertMapper.selectMaps(new QueryWrapper<OpsAlert>()
                .select("DATE(started_at) AS day", "COUNT(*) AS cnt")
                .ge("started_at", sevenDaysAgo)
                .groupBy("day")
                .orderByAsc("day"));
        summary.put("alertsTrend", trend.stream().map(m -> Map.of(
                "day", String.valueOf(m.get("day")),
                "count", ((Number) m.get("cnt")).intValue())).toList());

        List<Map<String, Object>> severity = alertMapper.selectMaps(new QueryWrapper<OpsAlert>()
                .select("severity", "COUNT(*) AS cnt")
                .eq("status", "ACTIVE")
                .groupBy("severity"));
        summary.put("severityDist", severity.stream().map(m -> Map.of(
                "severity", String.valueOf(m.get("severity")),
                "count", ((Number) m.get("cnt")).intValue())).toList());

        List<Map<String, Object>> agents = sessionMapper.selectMaps(new QueryWrapper<ChatSession>()
                .select("agent_type", "COUNT(*) AS cnt")
                .groupBy("agent_type"));
        summary.put("agentDist", agents.stream().map(m -> Map.of(
                "agentType", String.valueOf(m.get("agent_type")),
                "count", ((Number) m.get("cnt")).intValue())).toList());
        return summary;
    }
}
