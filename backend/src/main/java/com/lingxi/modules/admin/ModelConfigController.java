package com.lingxi.modules.admin;

import com.lingxi.agent.llm.AiModelConfig;
import com.lingxi.agent.llm.ChatModelFactory;
import com.lingxi.agent.llm.ModelConfigService;
import com.lingxi.common.api.Result;
import com.lingxi.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Tag(name = "系统管理-模型配置")
@Slf4j
@RestController
@RequestMapping("/api/admin/model-config")
@RequiredArgsConstructor
public class ModelConfigController {

    private final ModelConfigService modelConfigService;
    private final ChatModelFactory chatModelFactory;

    @Operation(summary = "查看模型配置（密钥掩码）")
    @GetMapping
    public Result<Map<String, Object>> get() {
        AiModelConfig config = modelConfigService.get();
        ChatModelFactory.Effective effective = chatModelFactory.effective();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("config", masked(config));
        body.put("effectiveProvider", effective.provider());
        body.put("effectiveModel", effective.model());
        return Result.ok(body);
    }

    @Operation(summary = "保存模型配置并热更新模型实例")
    @PutMapping
    public Result<Map<String, Object>> save(@RequestBody AiModelConfig incoming) {
        AiModelConfig saved = modelConfigService.save(incoming, SecurityUtils.current().username());
        chatModelFactory.evict();
        return Result.ok(masked(saved));
    }

    @Operation(summary = "连通性测试")
    @PostMapping("/test")
    public Result<Map<String, Object>> test() {
        Map<String, Object> body = new LinkedHashMap<>();
        try {
            ChatModel model = chatModelFactory.getChatModel();
            Instant start = Instant.now();
            String reply = model.call(new Prompt("ping"))
                    .getResult().getOutput().getText();
            long latency = Duration.between(start, Instant.now()).toMillis();
            body.put("ok", true);
            body.put("latencyMs", latency);
            body.put("reply", reply == null ? "" : reply.substring(0, Math.min(200, reply.length())));
        } catch (Exception e) {
            log.warn("模型连通性测试失败", e);
            body.put("ok", false);
            body.put("message", e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        }
        return Result.ok(body);
    }

    private Map<String, Object> masked(AiModelConfig config) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", config.getId());
        body.put("provider", config.getProvider());
        body.put("baseUrl", config.getBaseUrl());
        body.put("apiKey", mask(config.getApiKey()));
        body.put("model", config.getModel());
        body.put("temperature", config.getTemperature());
        body.put("embeddingBaseUrl", config.getEmbeddingBaseUrl());
        body.put("embeddingApiKey", mask(config.getEmbeddingApiKey()));
        body.put("embeddingModel", config.getEmbeddingModel());
        body.put("runtimeOverride", config.getRuntimeOverride());
        body.put("updatedAt", config.getUpdatedAt());
        body.put("updatedBy", config.getUpdatedBy());
        return body;
    }

    private String mask(String secret) {
        if (secret == null || secret.isBlank()) {
            return "";
        }
        if (secret.length() <= 8) {
            return "****";
        }
        return secret.substring(0, 4) + "****" + secret.substring(secret.length() - 4);
    }
}
