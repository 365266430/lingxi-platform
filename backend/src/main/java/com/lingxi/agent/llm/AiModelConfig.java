package com.lingxi.agent.llm;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 模型配置（管理端可热更新，单行 id=1）。
 */
@Data
@TableName("ai_model_config")
public class AiModelConfig {

    @TableId(type = IdType.INPUT)
    private Long id;

    /** mock | openai */
    private String provider;

    private String baseUrl;

    private String apiKey;

    private String model;

    private Double temperature;

    private String embeddingBaseUrl;

    private String embeddingApiKey;

    private String embeddingModel;

    /** 是否启用数据库配置覆盖环境变量配置 */
    private Boolean runtimeOverride;

    private String updatedBy;

    private LocalDateTime updatedAt;
}
