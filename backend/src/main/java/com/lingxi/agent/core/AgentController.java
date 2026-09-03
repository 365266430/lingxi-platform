package com.lingxi.agent.core;

import com.lingxi.common.api.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@Tag(name = "智能体")
@RestController
@RequestMapping("/api/agents")
@RequiredArgsConstructor
public class AgentController {

    private final AgentRegistry agentRegistry;

    @Operation(summary = "智能体列表")
    @GetMapping
    public Result<List<Map<String, Object>>> list() {
        return Result.ok(agentRegistry.listDescriptors());
    }
}
