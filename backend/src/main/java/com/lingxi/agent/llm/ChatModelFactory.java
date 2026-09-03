package com.lingxi.agent.llm;

import com.lingxi.agent.llm.mock.MockChatModel;
import com.lingxi.agent.llm.mock.MockEmbeddingModel;
import com.lingxi.config.AppProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.openai.api.OpenAiApi.Builder;
import org.springframework.stereotype.Service;

/**
 * ChatModel / EmbeddingModel 工厂：供应商无关。
 * provider=mock 时返回离线脚本化模型；openai 时按 OpenAI 兼容协议构建
 * （DeepSeek / 通义千问兼容模式 / OpenAI 均可直接对接）。配置热更新后调用 evict() 重建。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatModelFactory {

    private final AppProperties properties;
    private final ModelConfigService configService;

    private volatile ChatModel chatModel;
    private volatile EmbeddingModel embeddingModel;

    public record Effective(String provider, String baseUrl, String apiKey, String model, Double temperature,
                            String embeddingBaseUrl, String embeddingApiKey, String embeddingModel) {
    }

    public Effective effective() {
        return configService.effective(properties);
    }

    public ChatModel getChatModel() {
        ChatModel model = chatModel;
        if (model == null) {
            synchronized (this) {
                if (chatModel == null) {
                    chatModel = buildChatModel(effective());
                }
                model = chatModel;
            }
        }
        return model;
    }

    public EmbeddingModel getEmbeddingModel() {
        EmbeddingModel model = embeddingModel;
        if (model == null) {
            synchronized (this) {
                if (embeddingModel == null) {
                    embeddingModel = buildEmbeddingModel(effective());
                }
                model = embeddingModel;
            }
        }
        return model;
    }

    public void evict() {
        synchronized (this) {
            chatModel = null;
            embeddingModel = null;
        }
        log.info("模型实例已重置，将在下次调用时按最新配置重建");
    }

    private ChatModel buildChatModel(Effective e) {
        if (!"openai".equalsIgnoreCase(e.provider())) {
            log.info("使用离线 Mock 模型（provider=mock）");
            return new MockChatModel();
        }
        log.info("构建 OpenAI 兼容模型 base={} model={}", mask(e.baseUrl()), e.model());
        OpenAiApi api = openAiApi(e.baseUrl(), e.apiKey());
        return OpenAiChatModel.builder()
                .openAiApi(api)
                .defaultOptions(org.springframework.ai.openai.OpenAiChatOptions.builder()
                        .model(e.model())
                        .temperature(e.temperature())
                        .build())
                .build();
    }

    private EmbeddingModel buildEmbeddingModel(Effective e) {
        boolean realEmbedding = "openai".equalsIgnoreCase(e.provider())
                && notBlank(e.embeddingBaseUrl()) && notBlank(e.embeddingApiKey());
        if (!realEmbedding) {
            log.info("使用离线 Mock Embedding 模型");
            return new MockEmbeddingModel();
        }
        log.info("构建 OpenAI 兼容 Embedding 模型 model={}", e.embeddingModel());
        OpenAiApi api = openAiApi(e.embeddingBaseUrl(), e.embeddingApiKey());
        return new OpenAiEmbeddingModel(api, MetadataMode.EMBED,
                OpenAiEmbeddingOptions.builder().model(e.embeddingModel()).build());
    }

    private OpenAiApi openAiApi(String baseUrl, String apiKey) {
        Builder builder = OpenAiApi.builder().apiKey(apiKey);
        if (notBlank(baseUrl)) {
            builder.baseUrl(baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl);
        }
        return builder.build();
    }

    private boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private String mask(String s) {
        if (s == null || s.length() < 20) {
            return s;
        }
        return s.substring(0, 18) + "…";
    }
}
