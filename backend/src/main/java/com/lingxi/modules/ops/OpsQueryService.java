package com.lingxi.modules.ops;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lingxi.common.exception.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 运维数据查询服务（控制器与 Agent 工具共用）。
 */
@Service
@RequiredArgsConstructor
public class OpsQueryService {

    private final OpsAlertMapper alertMapper;
    private final OpsHostMapper hostMapper;
    private final OpsServiceInfoMapper serviceMapper;
    private final OpsMetricMapper metricMapper;

    public Page<OpsAlert> pageAlerts(long current, long size, String status, String severity, String keyword) {
        LambdaQueryWrapper<OpsAlert> wrapper = new LambdaQueryWrapper<OpsAlert>()
                .eq(status != null && !status.isBlank(), OpsAlert::getStatus, status)
                .eq(severity != null && !severity.isBlank(), OpsAlert::getSeverity, severity)
                .and(keyword != null && !keyword.isBlank(), w -> w
                        .like(OpsAlert::getTitle, keyword)
                        .or().like(OpsAlert::getServiceName, keyword)
                        .or().like(OpsAlert::getHostName, keyword))
                .orderByDesc(OpsAlert::getStartedAt);
        return alertMapper.selectPage(new Page<>(current, size), wrapper);
    }

    public List<OpsAlert> activeAlerts(String severity, String keyword) {
        return alertMapper.selectList(new LambdaQueryWrapper<OpsAlert>()
                .eq(OpsAlert::getStatus, "ACTIVE")
                .eq(severity != null && !severity.isBlank(), OpsAlert::getSeverity, severity)
                .and(keyword != null && !keyword.isBlank(), w -> w
                        .like(OpsAlert::getTitle, keyword)
                        .or().like(OpsAlert::getServiceName, keyword))
                .orderByDesc(OpsAlert::getStartedAt)
                .last("LIMIT 20"));
    }

    public OpsAlert getAlert(Long id) {
        OpsAlert alert = alertMapper.selectById(id);
        if (alert == null) {
            throw BizException.notFound("告警");
        }
        return alert;
    }

    public List<OpsHost> listHosts() {
        return hostMapper.selectList(new LambdaQueryWrapper<OpsHost>().orderByAsc(OpsHost::getHostName));
    }

    public List<OpsServiceInfo> listServices() {
        return serviceMapper.selectList(new LambdaQueryWrapper<OpsServiceInfo>()
                .orderByAsc(OpsServiceInfo::getTier));
    }

    /** 手动恢复告警。 */
    public OpsAlert resolveAlert(Long id) {
        OpsAlert alert = alertMapper.selectById(id);
        if (alert == null) {
            throw com.lingxi.common.exception.BizException.notFound("告警");
        }
        if (!"ACTIVE".equals(alert.getStatus())) {
            throw com.lingxi.common.exception.BizException.forbidden("该告警已恢复，请勿重复操作");
        }
        OpsAlert update = new OpsAlert();
        update.setId(id);
        update.setStatus("RESOLVED");
        update.setResolvedAt(LocalDateTime.now());
        alertMapper.updateById(update);
        alert.setStatus("RESOLVED");
        alert.setResolvedAt(update.getResolvedAt());
        return alert;
    }

    /** 指标概况 Markdown：近 hours 小时各服务 CPU/内存 最新值、均值、峰值。 */
    public String metricsMarkdown(String serviceName, int hours) {
        LocalDateTime since = LocalDateTime.now().minusHours(hours);
        List<OpsMetric> metrics = metricMapper.selectList(new LambdaQueryWrapper<OpsMetric>()
                .ge(OpsMetric::getCollectedAt, since)
                .eq(serviceName != null && !serviceName.isBlank(), OpsMetric::getServiceName, serviceName));
        if (metrics.isEmpty()) {
            return "（查询窗口内无指标数据）";
        }
        Map<String, List<OpsMetric>> byService = metrics.stream()
                .collect(Collectors.groupingBy(OpsMetric::getServiceName, LinkedHashMap::new, Collectors.toList()));

        StringBuilder sb = new StringBuilder("| 服务 | CPU 最新 | CPU 均值 | CPU 峰值 | 内存最新 | 内存均值 |\n")
                .append("|---|---|---|---|---|---|\n");
        byService.forEach((service, list) -> {
            double cpuLatest = latest(list, "cpu_usage");
            double cpuAvg = avg(list, "cpu_usage");
            double cpuMax = max(list, "cpu_usage");
            double memLatest = latest(list, "mem_usage");
            double memAvg = avg(list, "mem_usage");
            sb.append(String.format("| %s | %.1f%% | %.1f%% | %.1f%% | %.1f%% | %.1f%%%n",
                    service, cpuLatest, cpuAvg, cpuMax, memLatest, memAvg));
        });
        return sb.toString();
    }

    /** 指标时序（图表用）。 */
    public List<Map<String, Object>> metricSeries(String serviceName, int hours) {
        LocalDateTime since = LocalDateTime.now().minusHours(hours);
        List<OpsMetric> metrics = metricMapper.selectList(new LambdaQueryWrapper<OpsMetric>()
                .ge(OpsMetric::getCollectedAt, since)
                .eq(serviceName != null && !serviceName.isBlank(), OpsMetric::getServiceName, serviceName)
                .orderByAsc(OpsMetric::getCollectedAt));
        Map<String, Map<String, Map<String, Object>>> grouped = new LinkedHashMap<>();
        for (OpsMetric m : metrics) {
            Map<String, Map<String, Object>> serviceSeries =
                    grouped.computeIfAbsent(m.getServiceName(), k -> new LinkedHashMap<>());
            Map<String, Object> point = serviceSeries.computeIfAbsent(m.getCollectedAt().withSecond(0).toString(),
                    k -> {
                        Map<String, Object> p = new LinkedHashMap<>();
                        p.put("time", m.getCollectedAt().withSecond(0).withNano(0));
                        return p;
                    });
            point.put(m.getMetricName(), m.getMetricValue());
        }
        return grouped.entrySet().stream()
                .map(e -> Map.<String, Object>of("serviceName", e.getKey(),
                        "points", e.getValue().values().stream()
                                .sorted(Comparator.comparing(p -> (LocalDateTime) p.get("time")))
                                .toList()))
                .toList();
    }

    /** 告警列表 Markdown（供 Agent 工具输出）。 */
    public String activeAlertsMarkdown(String severity, String keyword) {
        List<OpsAlert> alerts = activeAlerts(severity, keyword);
        if (alerts.isEmpty()) {
            return "（当前无匹配的活跃告警）";
        }
        StringBuilder sb = new StringBuilder("| ID | 级别 | 服务 | 主机 | 标题 | 指标 | 开始时间 |\n|---|---|---|---|---|---|---|\n");
        for (OpsAlert a : alerts) {
            sb.append(String.format("| %d | %s | %s | %s | %s | %s=%s | %s%n",
                    a.getId(), a.getSeverity(), a.getServiceName(), a.getHostName(),
                    a.getTitle(), a.getMetricName(), a.getMetricValue(), a.getStartedAt()));
        }
        sb.append("\n共 ").append(alerts.size()).append(" 条活跃告警。");
        return sb.toString();
    }

    public String cmdbMarkdown(String keyword) {
        List<OpsServiceInfo> services = listServices().stream()
                .filter(s -> keyword == null || keyword.isBlank()
                        || s.getServiceName().contains(keyword) || s.getOwner().contains(keyword))
                .toList();
        StringBuilder sb = new StringBuilder("**服务列表**\n\n| 服务 | 负责人 | 等级 | 主机 |\n|---|---|---|---|\n");
        for (OpsServiceInfo s : services) {
            sb.append(String.format("| %s | %s | T%d | %s%n", s.getServiceName(), s.getOwner(),
                    s.getTier(), s.getHostNames()));
        }
        List<OpsHost> hosts = listHosts().stream()
                .filter(h -> keyword == null || keyword.isBlank()
                        || h.getHostName().contains(keyword) || h.getIp().contains(keyword))
                .limit(15)
                .toList();
        sb.append("\n**主机（前 15 台）**\n\n| 主机 | IP | 配置 | 环境 | 状态 |\n|---|---|---|---|---|\n");
        for (OpsHost h : hosts) {
            sb.append(String.format("| %s | %s | %dC%dG%dG | %s | %s%n", h.getHostName(), h.getIp(),
                    h.getCpuCores(), h.getMemoryGb(), h.getDiskGb(), h.getEnv(), h.getStatus()));
        }
        return sb.toString();
    }

    private double latest(List<OpsMetric> list, String metricName) {
        return list.stream().filter(m -> metricName.equals(m.getMetricName()))
                .max(Comparator.comparing(OpsMetric::getCollectedAt))
                .map(OpsMetric::getMetricValue).orElse(0d);
    }

    private double avg(List<OpsMetric> list, String metricName) {
        return list.stream().filter(m -> metricName.equals(m.getMetricName()))
                .mapToDouble(OpsMetric::getMetricValue).average().orElse(0d);
    }

    private double max(List<OpsMetric> list, String metricName) {
        return list.stream().filter(m -> metricName.equals(m.getMetricName()))
                .mapToDouble(OpsMetric::getMetricValue).max().orElse(0d);
    }
}
