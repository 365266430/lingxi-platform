package com.lingxi.agent.llm;

import com.lingxi.config.AppProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 模型配置服务：环境变量配置为基线，管理端可开启 DB 配置热覆盖。
 */
@Service
@RequiredArgsConstructor
public class ModelConfigService {

    private final AiModelConfigMapper configMapper;

    public AiModelConfig get() {
        AiModelConfig config = configMapper.selectById(1L);
        if (config == null) {
            config = new AiModelConfig();
            config.setId(1L);
            config.setRuntimeOverride(false);
        }
        return config;
    }

    public AiModelConfig save(AiModelConfig incoming, String operator) {
        AiModelConfig existing = configMapper.selectById(1L);
        AiModelConfig config = existing == null ? new AiModelConfig() : existing;
        config.setId(1L);
        config.setProvider(incoming.getProvider());
        config.setBaseUrl(incoming.getBaseUrl());
        // 前端传回掩码时保持原值
        if (incoming.getApiKey() != null && !incoming.getApiKey().isBlank()
                && !incoming.getApiKey().matches("^\\*+$")) {
            config.setApiKey(incoming.getApiKey());
        }
        config.setModel(incoming.getModel());
        config.setTemperature(incoming.getTemperature());
        config.setEmbeddingBaseUrl(incoming.getEmbeddingBaseUrl());
        if (incoming.getEmbeddingApiKey() != null && !incoming.getEmbeddingApiKey().isBlank()
                && !incoming.getEmbeddingApiKey().matches("^\\*+$")) {
            config.setEmbeddingApiKey(incoming.getEmbeddingApiKey());
        }
        config.setEmbeddingModel(incoming.getEmbeddingModel());
        config.setRuntimeOverride(incoming.getRuntimeOverride());
        config.setUpdatedBy(operator);
        config.setUpdatedAt(java.time.LocalDateTime.now());
        if (existing == null) {
            configMapper.insert(config);
        } else {
            configMapper.updateById(config);
        }
        return config;
    }

    /** 计算生效配置：runtimeOverride 开启时以 DB 为准，空字段回退环境配置。 */
    public ChatModelFactory.Effective effective(AppProperties props) {
        AppProperties.Llm env = props.getLlm();
        AiModelConfig db = configMapper.selectById(1L);
        if (db == null || !Boolean.TRUE.equals(db.getRuntimeOverride())) {
            return new ChatModelFactory.Effective(env.getProvider(), env.getBaseUrl(), env.getApiKey(),
                    env.getModel(), env.getTemperature(), env.getEmbeddingBaseUrl(),
                    env.getEmbeddingApiKey(), env.getEmbeddingModel());
        }
        String provider = orDefault(db.getProvider(), env.getProvider());
        String baseUrl = orDefault(db.getBaseUrl(), env.getBaseUrl());
        String apiKey = orDefault(db.getApiKey(), env.getApiKey());
        String model = orDefault(db.getModel(), env.getModel());
        Double temperature = db.getTemperature() != null ? db.getTemperature() : env.getTemperature();
        String embedUrl = orDefault(db.getEmbeddingBaseUrl(), env.getEmbeddingBaseUrl());
        String embedKey = orDefault(db.getEmbeddingApiKey(), env.getEmbeddingApiKey());
        String embedModel = orDefault(db.getEmbeddingModel(), env.getEmbeddingModel());
        return new ChatModelFactory.Effective(provider, baseUrl, apiKey, model, temperature,
                embedUrl, embedKey, embedModel);
    }

    private String orDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
