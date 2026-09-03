package com.lingxi.modules.ops;

import com.lingxi.agent.core.AgentType;
import com.lingxi.common.api.PageResult;
import com.lingxi.common.api.Result;
import com.lingxi.modules.chat.ChatService;
import com.lingxi.modules.chat.ChatSession;
import com.lingxi.modules.chat.SessionCreateReq;
import com.lingxi.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@Tag(name = "运维中心")
@RestController
@RequestMapping("/api/ops")
@RequiredArgsConstructor
public class OpsController {

    private final OpsQueryService opsQueryService;
    private final DiagnosisService diagnosisService;

    @Operation(summary = "告警分页")
    @GetMapping("/alerts")
    public Result<PageResult<OpsAlert>> alerts(@RequestParam(defaultValue = "1") long current,
                                               @RequestParam(defaultValue = "10") long size,
                                               @RequestParam(required = false) String status,
                                               @RequestParam(required = false) String severity,
                                               @RequestParam(required = false) String keyword) {
        var page = opsQueryService.pageAlerts(current, size, status, severity, keyword);
        return Result.ok(PageResult.of(page.getTotal(), page.getCurrent(), page.getSize(), page.getRecords()));
    }

    @Operation(summary = "一键 AI 诊断：创建诊断会话并返回预置提问")
    @PostMapping("/alerts/{id}/diagnose")
    public Result<Map<String, Object>> diagnose(@PathVariable Long id) {
        return Result.ok(diagnosisService.diagnose(id));
    }

    @Operation(summary = "手动恢复告警")
    @PostMapping("/alerts/{id}/resolve")
    public Result<OpsAlert> resolve(@PathVariable Long id) {
        return Result.ok(opsQueryService.resolveAlert(id));
    }

    @Operation(summary = "主机列表")
    @GetMapping("/hosts")
    public Result<List<OpsHost>> hosts() {
        return Result.ok(opsQueryService.listHosts());
    }

    @Operation(summary = "服务列表")
    @GetMapping("/services")
    public Result<List<OpsServiceInfo>> services() {
        return Result.ok(opsQueryService.listServices());
    }

    @Operation(summary = "指标时序")
    @GetMapping("/metrics")
    public Result<List<Map<String, Object>>> metrics(@RequestParam(required = false) String serviceName,
                                                     @RequestParam(defaultValue = "24") int hours) {
        return Result.ok(opsQueryService.metricSeries(serviceName, hours));
    }

    @Operation(summary = "服务下拉选项")
    @GetMapping("/metrics/service-names")
    public Result<List<String>> serviceNames() {
        return Result.ok(opsQueryService.listServices().stream()
                .map(OpsServiceInfo::getServiceName).toList());
    }
}
