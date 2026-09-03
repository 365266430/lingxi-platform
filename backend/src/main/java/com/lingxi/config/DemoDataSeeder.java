package com.lingxi.config;

import com.lingxi.agent.llm.AiModelConfig;
import com.lingxi.agent.llm.AiModelConfigMapper;
import com.lingxi.modules.ops.OpsAlert;
import com.lingxi.modules.ops.OpsAlertMapper;
import com.lingxi.modules.ops.OpsHost;
import com.lingxi.modules.ops.OpsHostMapper;
import com.lingxi.modules.ops.OpsMetric;
import com.lingxi.modules.ops.OpsMetricMapper;
import com.lingxi.modules.ops.OpsServiceInfo;
import com.lingxi.modules.ops.OpsServiceInfoMapper;
import com.lingxi.modules.user.SysUser;
import com.lingxi.modules.user.SysUserMapper;
import com.lingxi.rag.KbDocument;
import com.lingxi.rag.KbDocumentMapper;
import com.lingxi.rag.KbSpace;
import com.lingxi.rag.KbSpaceMapper;
import com.lingxi.rag.service.IngestionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Random;

/**
 * 演示数据种子器（幂等）：账号、CMDB、告警、24h 指标、模型配置、知识库 Runbook。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DemoDataSeeder implements CommandLineRunner {

    private final SysUserMapper userMapper;
    private final OpsHostMapper hostMapper;
    private final OpsServiceInfoMapper serviceMapper;
    private final OpsAlertMapper alertMapper;
    private final OpsMetricMapper metricMapper;
    private final KbSpaceMapper spaceMapper;
    private final KbDocumentMapper documentMapper;
    private final AiModelConfigMapper modelConfigMapper;
    private final PasswordEncoder passwordEncoder;
    private final IngestionService ingestionService;
    private final AppProperties properties;

    @Override
    public void run(String... args) {
        if (!properties.getSeed().isEnabled()) {
            log.info("演示数据种子已关闭（lingxi.seed.enabled=false）");
            return;
        }
        seedUsers();
        seedModelConfig();
        seedOps();
        seedKnowledge();
    }

    private void seedUsers() {
        try {
            if (userMapper.selectCount(null) > 0) {
                return;
            }
            insertUser("admin", "admin123", "ADMIN", "平台管理员");
            insertUser("opsuser", "user123", "USER", "运维同学");
            log.info("已创建种子账号 admin/admin123、opsuser/user123");
        } catch (Exception e) {
            log.error("种子账号创建失败", e);
        }
    }

    private void insertUser(String username, String password, String role, String nickname) {
        SysUser user = new SysUser();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(password));
        user.setRole(role);
        user.setNickname(nickname);
        user.setEnabled(1);
        userMapper.insert(user);
    }

    private void seedModelConfig() {
        try {
            if (modelConfigMapper.selectById(1L) != null) {
                return;
            }
            AiModelConfig config = new AiModelConfig();
            config.setId(1L);
            config.setProvider(properties.getLlm().getProvider());
            config.setRuntimeOverride(false);
            modelConfigMapper.insert(config);
        } catch (Exception e) {
            log.error("模型配置种子失败", e);
        }
    }

    private void seedOps() {
        try {
            if (hostMapper.selectCount(null) > 0) {
                return;
            }
            seedHosts();
            seedServices();
            seedAlerts();
            seedMetrics();
            log.info("演示运维数据（CMDB/告警/指标）已就绪");
        } catch (Exception e) {
            log.error("运维演示数据种子失败", e);
        }
    }

    private void seedHosts() {
        List<Object[]> hosts = List.of(
                new Object[]{"web-gw-01", "10.0.1.11", 8, 16, 200, "PROD"},
                new Object[]{"web-gw-02", "10.0.1.12", 8, 16, 200, "PROD"},
                new Object[]{"app-order-01", "10.0.2.21", 8, 16, 300, "PROD"},
                new Object[]{"app-order-02", "10.0.2.22", 8, 16, 300, "PROD"},
                new Object[]{"app-pay-01", "10.0.2.31", 8, 16, 300, "PROD"},
                new Object[]{"app-pay-02", "10.0.2.32", 8, 16, 300, "PROD"},
                new Object[]{"app-user-01", "10.0.2.41", 4, 8, 200, "PROD"},
                new Object[]{"app-product-01", "10.0.2.51", 4, 8, 200, "PROD"},
                new Object[]{"db-mysql-01", "10.0.3.61", 16, 64, 2000, "PROD"},
                new Object[]{"db-mysql-02", "10.0.3.62", 16, 64, 2000, "PROD"},
                new Object[]{"redis-cache-01", "10.0.3.71", 8, 32, 200, "PROD"},
                new Object[]{"mq-01", "10.0.3.81", 8, 16, 500, "PROD"});
        for (Object[] h : hosts) {
            OpsHost host = new OpsHost();
            host.setHostName((String) h[0]);
            host.setIp((String) h[1]);
            host.setCpuCores((Integer) h[2]);
            host.setMemoryGb((Integer) h[3]);
            host.setDiskGb((Integer) h[4]);
            host.setEnv((String) h[5]);
            host.setStatus("ONLINE");
            hostMapper.insert(host);
        }
    }

    private void seedServices() {
        List<Object[]> services = List.of(
                new Object[]{"api-gateway", "陈曦", 1, "web-gw-01,web-gw-02", "git@git.corp:infra/api-gateway.git"},
                new Object[]{"order-service", "李明", 1, "app-order-01,app-order-02", "git@git.corp:biz/order-service.git"},
                new Object[]{"payment-service", "王芳", 1, "app-pay-01,app-pay-02", "git@git.corp:biz/payment-service.git"},
                new Object[]{"user-service", "赵磊", 2, "app-user-01", "git@git.corp:biz/user-service.git"},
                new Object[]{"product-service", "周洁", 2, "app-product-01", "git@git.corp:biz/product-service.git"},
                new Object[]{"mysql-cluster", "吴强", 1, "db-mysql-01,db-mysql-02", "-"},
                new Object[]{"redis-cache", "郑浩", 1, "redis-cache-01", "-"},
                new Object[]{"rabbitmq-mq", "孙丽", 3, "mq-01", "-"});
        for (Object[] s : services) {
            OpsServiceInfo service = new OpsServiceInfo();
            service.setServiceName((String) s[0]);
            service.setOwner((String) s[1]);
            service.setTier((Integer) s[2]);
            service.setHostNames((String) s[3]);
            service.setRepo((String) s[4]);
            serviceMapper.insert(service);
        }
    }

    private void seedAlerts() {
        LocalDateTime now = LocalDateTime.now();
        List<Object[]> active = List.of(
                new Object[]{"CPU 使用率超过 90%", "P1", "order-service", "app-order-01", "cpu_usage", "92.4%",
                        "订单服务 CPU 持续高位，接口平均 RT 由 80ms 升至 650ms，疑似大促流量或慢查询引发", now.minusMinutes(42)},
                new Object[]{"数据库连接池使用率 98%", "P1", "payment-service", "app-pay-01", "pool_usage", "98%",
                        "支付服务连接池接近耗尽，出现获取连接超时日志，疑似连接泄漏", now.minusMinutes(18)},
                new Object[]{"磁盘使用率超过 85%", "P2", "mysql-cluster", "db-mysql-01", "disk_usage", "85.2%",
                        "MySQL 主库磁盘增长过快，binlog 与慢日志未及时轮转", now.minusHours(3)},
                new Object[]{"HTTP 5xx 错误率 3.2%", "P2", "api-gateway", "web-gw-01", "error_rate", "3.2%",
                        "网关 5xx 错误率上升，主要来自 order-service 路由的超时请求", now.minusMinutes(65)},
                new Object[]{"内存使用率缓慢增长 78%", "P3", "api-gateway", "web-gw-02", "mem_usage", "78.5%",
                        "网关实例内存 48 小时内从 45% 增长至 78%，疑似缓存未设置上限", now.minusHours(20)},
                new Object[]{"Redis 内存碎片率 1.6", "P3", "redis-cache", "redis-cache-01", "mem_fragmentation", "1.6",
                        "缓存实例碎片率偏高，activedefrag 未开启", now.minusHours(8)});
        for (Object[] a : active) {
            insertAlert(a[0], a[1], a[2], a[3], a[4], a[5], a[6], (LocalDateTime) a[7], "ACTIVE", null);
        }
        // 近 7 天历史告警（已恢复），供趋势图展示
        String[][] historical = {
                {"CPU 使用率超过 85%", "P2", "user-service", "app-user-01", "cpu_usage"},
                {"磁盘使用率超过 80%", "P3", "mq-01", "mq-01", "disk_usage"},
                {"HTTP 5xx 错误率 2.1%", "P2", "api-gateway", "web-gw-01", "error_rate"},
                {"慢查询数量突增", "P2", "mysql-cluster", "db-mysql-02", "slow_query"},
                {"连接池使用率 90%", "P3", "order-service", "app-order-02", "pool_usage"},
                {"内存使用率超过 85%", "P2", "product-service", "app-product-01", "mem_usage"}};
        Random random = new Random(42);
        for (int i = 0; i < 18; i++) {
            String[] template = historical[i % historical.length];
            LocalDateTime started = now.minusDays(random.nextInt(6) + 1).minusHours(random.nextInt(20));
            insertAlert(template[0], template[1], template[2], template[3], template[4],
                    "见监控", "历史告警（演示数据）", started, "RESOLVED", started.plusMinutes(30 + random.nextInt(180)));
        }
    }

    private void insertAlert(Object title, Object severity, Object service, Object host, Object metric,
                             Object value, Object desc, LocalDateTime started, String status,
                             LocalDateTime resolved) {
        OpsAlert alert = new OpsAlert();
        alert.setTitle((String) title);
        alert.setSeverity((String) severity);
        alert.setServiceName((String) service);
        alert.setHostName((String) host);
        alert.setMetricName((String) metric);
        alert.setMetricValue((String) value);
        alert.setDescription((String) desc);
        alert.setStatus(status);
        alert.setStartedAt(started);
        alert.setResolvedAt(resolved);
        alertMapper.insert(alert);
    }

    private void seedMetrics() {
        LocalDateTime now = LocalDateTime.now();
        Random random = new Random(7);
        List<Object[]> profiles = List.of(
                new Object[]{"api-gateway", 32.0, 55.0},
                new Object[]{"order-service", 55.0, 60.0},
                new Object[]{"payment-service", 40.0, 58.0},
                new Object[]{"user-service", 25.0, 45.0},
                new Object[]{"product-service", 30.0, 50.0},
                new Object[]{"mysql-cluster", 50.0, 70.0},
                new Object[]{"redis-cache", 45.0, 62.0},
                new Object[]{"rabbitmq-mq", 20.0, 40.0});
        int total = 0;
        for (Object[] p : profiles) {
            String service = (String) p[0];
            double cpuBase = (Double) p[1];
            double memBase = (Double) p[2];
            for (int step = 48; step >= 0; step--) {
                LocalDateTime at = now.minusMinutes(step * 30L);
                // order-service 近 1 小时模拟 CPU 飙高（对应演示诊断场景）
                boolean cpuSpike = "order-service".equals(service) && step <= 2;
                double cpu = cpuSpike
                        ? 85 + random.nextDouble() * 10
                        : clamp(cpuBase + Math.sin(step / 5.0) * 8 + random.nextDouble() * 6 - 3);
                double mem = clamp(memBase + Math.sin(step / 9.0) * 5 + random.nextDouble() * 4 - 2);
                total += insertMetric(service, "cpu_usage", cpu, at)
                        + insertMetric(service, "mem_usage", mem, at);
            }
        }
        log.info("已写入演示指标 {} 条", total);
    }

    private int insertMetric(String service, String metric, double value, LocalDateTime at) {
        OpsMetric m = new OpsMetric();
        m.setServiceName(service);
        m.setMetricName(metric);
        m.setMetricValue(Math.round(value * 10) / 10.0);
        m.setCollectedAt(at);
        metricMapper.insert(m);
        return 1;
    }

    private double clamp(double v) {
        return Math.max(2, Math.min(99, v));
    }

    private void seedKnowledge() {
        try {
            if (documentMapper.selectCount(null) > 0) {
                return;
            }
            SysUser admin = userMapper.selectOne(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SysUser>()
                            .eq(SysUser::getUsername, "admin"));
            KbSpace space = new KbSpace();
            space.setName("运维手册库");
            space.setDescription("SRE 处置手册（Runbook）与故障案例，供 AI 诊断引用");
            space.setOwnerId(admin == null ? null : admin.getId());
            space.setDocCount(0);
            spaceMapper.insert(space);

            for (Object[] doc : runbooks()) {
                String filename = (String) doc[0];
                String markdown = (String) doc[1];
                KbDocument record = new KbDocument();
                record.setSpaceId(space.getId());
                record.setFilename(filename);
                record.setContentType("text/markdown");
                record.setSizeBytes((long) markdown.getBytes(StandardCharsets.UTF_8).length);
                record.setStatus("PROCESSING");
                record.setSource("SEED");
                record.setUploaderId(admin == null ? null : admin.getId());
                documentMapper.insert(record);
                try {
                    ingestionService.ingest(record.getId(), markdown.getBytes(StandardCharsets.UTF_8), filename);
                } catch (Exception e) {
                    log.warn("Runbook 向量化未完成（向量库不可用？）doc={}: {}", filename, e.getMessage());
                }
            }
            KbSpace update = new KbSpace();
            update.setId(space.getId());
            update.setDocCount(4);
            spaceMapper.updateById(update);
            log.info("知识库 Runbook 种子完成（4 篇）");
        } catch (Exception e) {
            log.error("知识库种子失败", e);
        }
    }

    private Object[][] runbooks() {
        return new Object[][]{
                {"CPU飙高排查处置手册.md", """
                        # CPU 飙高排查处置手册（Runbook-CPU-001）

                        ## 适用场景
                        服务 CPU 使用率持续超过 85%，伴随接口 RT 上涨、吞吐下降。

                        ## 排查步骤
                        1. 确认告警对应实例，使用 `top -Hp <pid>` 定位高 CPU 线程，`printf '%x' <tid>` 转十六进制。
                        2. `jstack <pid> | grep -A 20 <nid>` 查看线程栈，判断是业务代码热点、GC 线程还是锁竞争。
                        3. 若 GC 线程占用高：`jstat -gcutil <pid> 1000` 观察 GC 频率与老年代占用，dump 堆分析大对象。
                        4. 若业务线程占用高：开启 async-profiler 采样 60 秒定位热点方法；重点检查正则回溯、大循环、序列化。
                        5. 检查近期变更：发布记录、流量突增（网关 QPS 曲线）、上下游依赖超时重试风暴。

                        ## 处置动作
                        - 流量型：开启限流/扩容实例。
                        - 代码型：回滚最近版本，或热修复后重新发布。
                        - GC 型：调整堆参数或修复内存泄漏后重启实例。

                        ## 升级条件
                        处置 30 分钟后 CPU 仍高于 90%，或核心接口错误率超过 5%，升级至值班主管。

                        ## 相关案例
                        2026-05 订单服务大促预热期间 CPU 95%：根因为商品快照正则解析回溯，热修复后恢复。
                        """},
                {"磁盘空间不足处置手册.md", """
                        # 磁盘空间不足处置手册（Runbook-DISK-002）

                        ## 适用场景
                        主机磁盘使用率超过 80%（P2）或 90%（P1），存在写满风险。

                        ## 排查步骤
                        1. `df -h` 确认分区；`du -xh --max-depth=2 / | sort -rh | head -20` 定位大目录。
                        2. 日志类：检查 `/var/log` 与应用日志目录，确认日志轮转配置是否生效。
                        3. 数据库类：检查 binlog、慢日志、临时表空间；`purge master logs before now() - interval 3 day;`。
                        4. 查找已删除未释放句柄：`lsof | grep deleted`，必要时重启对应进程。

                        ## 处置动作
                        - 按保留策略压缩/清理过期日志：`find /logs -mtime +7 -name '*.log' -exec gzip {} \\;`
                        - 调整日志级别或采集采样率。
                        - 紧急情况下按变更流程扩容云盘（在线扩容）。

                        ## 预防措施
                        磁盘水位告警阈值 80%，日志统一轮转 + 采集 + 7 天保留；binlog 保留 3 天。

                        ## 升级条件
                        清理后仍低于 10% 可用空间，或写满将影响核心库，立即升级 DBA 值班。
                        """},
                {"数据库连接池耗尽处置手册.md", """
                        # 数据库连接池耗尽处置手册（Runbook-POOL-003）

                        ## 适用场景
                        应用连接池使用率超过 90%，出现 `Connection is not available, request timed out` 类日志。

                        ## 排查步骤
                        1. 应用侧：确认池配置（maxPoolSize、maxLifetime、leakDetectionThreshold）与连接获取超时日志。
                        2. 数据库侧：`show processlist;` 查看活跃连接与状态，`select * from information_schema.innodb_trx;` 检查长事务。
                        3. 慢查询：`select * from sys.statements_with_full_table_scans limit 10;`，确认近期新增慢 SQL。
                        4. 连接泄漏：对比连接获取/归还指标，jstack 查看持有连接未释放的业务栈。

                        ## 处置动作
                        - 短期：对长事务/慢会话执行 `kill <id>`；流量高峰临时上调 maxPoolSize 后滚动重启。
                        - 泄漏型：回滚引发泄漏的版本；修复未在 finally 中归还连接的代码。
                        - 慢 SQL 型：加索引或限流降级对应接口。

                        ## 预防措施
                        连接池使用率告警阈值 80%；开启泄漏检测（阈值 60s）；上线前压测容量。

                        ## 升级条件
                        kill 会话与扩池后 15 分钟内使用率仍超 95%，或影响支付核心链路，立即升级 DBA + 系统负责人。
                        """},
                {"Redis内存告警处置手册.md", """
                        # Redis 内存告警处置手册（Runbook-REDIS-004）

                        ## 适用场景
                        Redis used_memory 超过 maxmemory 的 85%，或内存碎片率（mem_fragmentation_ratio）大于 1.5。

                        ## 排查步骤
                        1. `info memory` 确认 used_memory、maxmemory、碎片率、淘汰策略。
                        2. 大 Key 扫描：`redis-cli --bigkeys`；确认是否存在异常大 Value 或集合无限增长。
                        3. 检查过期策略：是否存在大量未设置 TTL 的 Key（`info keyspace` 对比样本）。
                        4. 碎片率偏高时确认 activedefrag 是否开启、近期是否大量删除/过期。

                        ## 处置动作
                        - 大 Key：业务侧拆分或异步删除（unlink）。
                        - 未设 TTL：补齐过期时间，重要数据改为持久化存储。
                        - 碎片率：开启 `activedefrag yes` 或低峰重启实例。
                        - 容量不足：按流程扩容（主从升级或集群分片）。

                        ## 预防措施
                        缓存一律设置 TTL；上线前评审 Key 设计；碎片率与水位纳入日巡检。
                        """}};
    }
}
