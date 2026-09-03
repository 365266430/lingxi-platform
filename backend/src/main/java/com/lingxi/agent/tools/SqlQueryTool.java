package com.lingxi.agent.tools;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 只读 SQL 分析工具：白名单校验（仅 SELECT、仅 ops_ 前缀表、强制 LIMIT、5s 超时）。
 */
@Component
public class SqlQueryTool {

    private static final Pattern FORBIDDEN = Pattern.compile(
            "(?i)\\b(insert|update|delete|drop|alter|truncate|create|grant|revoke|set|use|call|lock|"
                    + "unlock|merge|replace|handler|prepare|execute|rename|comment|analyze|optimize)\\b");
    private static final Pattern TABLE_REF = Pattern.compile("(?i)\\b(?:from|join)\\s+([a-zA-Z_][a-zA-Z0-9_]*)");
    private static final Pattern HAS_LIMIT = Pattern.compile("(?i)\\blimit\\s+\\d+");
    private static final int MAX_ROWS_IN_OUTPUT = 50;

    private final JdbcTemplate jdbcTemplate;

    public SqlQueryTool(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.setQueryTimeout(5);
        this.jdbcTemplate = jdbcTemplate;
    }

    @Tool(name = "run_readonly_sql",
            description = "对运维业务库执行只读 SQL 分析查询。仅允许单条 SELECT/WITH 语句，仅可访问 ops_ 前缀的表"
                    + "（ops_alert 告警 / ops_host 主机 / ops_service_info 服务 / ops_metric 指标），自动追加 LIMIT 防止大结果集")
    public String runReadonlySql(@ToolParam(description = "完整的只读 SQL 语句") String sql) {
        String rejection = SqlGuard.validate(sql);
        if (rejection != null) {
            return "SQL 已被安全策略拒绝：" + rejection;
        }
        String safe = ensureLimit(sql.trim());
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(safe);
            if (rows.isEmpty()) {
                return "查询成功：0 行";
            }
            return toMarkdownTable(rows);
        } catch (DataAccessException e) {
            return "SQL 执行失败：" + (e.getMostSpecificCause() == null ? e.getMessage()
                    : e.getMostSpecificCause().getMessage());
        }
    }

    static String ensureLimit(String sql) {
        String trimmed = sql.endsWith(";") ? sql.substring(0, sql.length() - 1) : sql;
        return HAS_LIMIT.matcher(trimmed).find() ? trimmed : trimmed + " LIMIT 100";
    }

    private String toMarkdownTable(List<Map<String, Object>> rows) {
        List<String> headers = rows.get(0).keySet().stream().toList();
        StringBuilder sb = new StringBuilder("| ");
        sb.append(String.join(" | ", headers)).append(" |\n|");
        sb.append("---|".repeat(headers.size())).append("\n");
        int shown = Math.min(rows.size(), MAX_ROWS_IN_OUTPUT);
        for (int i = 0; i < shown; i++) {
            Map<String, Object> row = rows.get(i);
            sb.append("| ");
            sb.append(headers.stream().map(h -> String.valueOf(row.get(h))).reduce((a, b) -> a + " | " + b)
                    .orElse("")).append(" |\n");
        }
        sb.append("\n共 ").append(rows.size()).append(" 行");
        if (rows.size() > shown) {
            sb.append("（仅显示前 ").append(shown).append(" 行）");
        }
        return sb.toString();
    }

    /** SQL 安全护栏（纯静态，便于单测）。 */
    static final class SqlGuard {

        private SqlGuard() {
        }

        static String validate(String sql) {
            if (sql == null || sql.isBlank()) {
                return "SQL 为空";
            }
            String normalized = sql.trim();
            if (normalized.endsWith(";")) {
                normalized = normalized.substring(0, normalized.length() - 1).trim();
            }
            if (normalized.contains(";")) {
                return "仅允许单条语句";
            }
            if (normalized.contains("--") || normalized.contains("/*")) {
                return "不允许 SQL 注释";
            }
            Matcher forbidden = FORBIDDEN.matcher(normalized);
            if (forbidden.find()) {
                return "包含被禁止的关键字：" + forbidden.group();
            }
            String lower = normalized.toLowerCase();
            if (!(lower.startsWith("select") || lower.startsWith("with"))) {
                return "仅允许 SELECT/WITH 查询";
            }
            Matcher tables = TABLE_REF.matcher(normalized);
            boolean found = false;
            while (tables.find()) {
                found = true;
                String table = tables.group(1).toLowerCase();
                if (!table.startsWith("ops_")) {
                    return "仅允许访问 ops_ 前缀的表，检测到：" + table;
                }
            }
            if (!found) {
                return "未识别到 FROM/JOIN 表引用";
            }
            return null;
        }
    }
}
