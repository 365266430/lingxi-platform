package com.lingxi.agent.tools;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 只读 SQL 工具测试：安全护栏（H2 内存库）。
 */
class SqlQueryToolTest {

    private static SqlQueryTool sqlQueryTool;

    @BeforeAll
    static void initDatabase() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:sqltool;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("CREATE TABLE ops_alert (id BIGINT, service_name VARCHAR(64), severity VARCHAR(10))");
        jdbcTemplate.update("INSERT INTO ops_alert VALUES (1, 'order-service', 'P1')");
        jdbcTemplate.update("INSERT INTO ops_alert VALUES (2, 'api-gateway', 'P2')");
        jdbcTemplate.update("INSERT INTO ops_alert VALUES (3, 'order-service', 'P2')");
        sqlQueryTool = new SqlQueryTool(jdbcTemplate);
    }

    @Test
    @DisplayName("合法 SELECT 正常执行并输出 Markdown 表格")
    void validSelect() {
        String result = sqlQueryTool.runReadonlySql("SELECT service_name, COUNT(*) AS cnt FROM ops_alert GROUP BY service_name");
        assertThat(result).startsWith("|");
        assertThat(result).contains("order-service").contains("2");
    }

    @Test
    @DisplayName("拒绝 DML/DDL：INSERT / UPDATE / DROP / CREATE")
    void rejectDmlDdl() {
        assertThat(sqlQueryTool.runReadonlySql("INSERT INTO ops_alert VALUES (9,'x','P1')")).contains("被禁止");
        assertThat(sqlQueryTool.runReadonlySql("UPDATE ops_alert SET severity = 'P1'")).contains("拒绝").contains("UPDATE");
        assertThat(sqlQueryTool.runReadonlySql("DROP TABLE ops_alert")).contains("拒绝").contains("DROP");
        assertThat(sqlQueryTool.runReadonlySql("CREATE TABLE t (id INT)")).contains("拒绝").contains("CREATE");
    }

    @Test
    @DisplayName("拒绝非 SELECT 开头、多语句、注释与非 ops_ 表")
    void rejectOtherPatterns() {
        assertThat(sqlQueryTool.runReadonlySql("SELECT 1; DROP TABLE ops_alert")).contains("单条语句");
        assertThat(sqlQueryTool.runReadonlySql("SELECT * FROM sys_user -- 注释")).contains("注释");
        assertThat(sqlQueryTool.runReadonlySql("SELECT * FROM information_schema.tables")).contains("ops_ 前缀");
        assertThat(sqlQueryTool.runReadonlySql("SELECT 1")).contains("FROM/JOIN");
        assertThat(sqlQueryTool.runReadonlySql("")).contains("为空");
    }

    @Test
    @DisplayName("自动追加 LIMIT 100，已有 LIMIT 不重复追加")
    void ensureLimit() {
        assertThat(SqlQueryTool.ensureLimit("SELECT * FROM ops_alert")).endsWith("LIMIT 100");
        assertThat(SqlQueryTool.ensureLimit("SELECT * FROM ops_alert LIMIT 10;"))
                .isEqualTo("SELECT * FROM ops_alert LIMIT 10");
    }

    @Test
    @DisplayName("SQL 语法错误时返回可读错误而不是抛异常")
    void syntaxErrorReturnsMessage() {
        String result = sqlQueryTool.runReadonlySql("SELECT * FROM ops_alert WHERE");
        assertThat(result).startsWith("SQL 执行失败");
    }
}
