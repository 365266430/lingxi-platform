package com.lingxi.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * 平台配置项（lingxi.*）。
 */
@Data
@ConfigurationProperties(prefix = "lingxi")
public class AppProperties {

    private Jwt jwt = new Jwt();
    private Llm llm = new Llm();
    private Storage storage = new Storage();
    private Agent agent = new Agent();
    private Vector vector = new Vector();
    private RateLimit ratelimit = new RateLimit();
    private Cors cors = new Cors();
    private Seed seed = new Seed();

    @Data
    public static class Jwt {
        private String secret;
        private int accessTokenTtlMinutes = 120;
        private int refreshTokenTtlDays = 7;
    }

    @Data
    public static class Llm {
        /** mock | openai */
        private String provider = "mock";
        private String baseUrl;
        private String apiKey;
        private String model;
        private Double temperature = 0.3;
        private String embeddingBaseUrl;
        private String embeddingApiKey;
        private String embeddingModel;
        private boolean runtimeOverride;
    }

    @Data
    public static class Storage {
        private String endpoint;
        private String accessKey;
        private String secretKey;
        private String bucket = "lingxi-kb";
    }

    @Data
    public static class Agent {
        private int maxIterations = 8;
        private int memoryWindow = 20;
        private int historyMaxChars = 24000;
    }

    @Data
    public static class Vector {
        private String host = "localhost";
        private int port = 6334;
        private String collection = "lingxi_kb";
    }

    @Data
    public static class RateLimit {
        private int loginLimit = 5;
        private int loginWindowSeconds = 60;
        private int chatLimit = 30;
        private int chatWindowSeconds = 60;
    }

    @Data
    public static class Cors {
        private List<String> allowedOrigins;
    }

    @Data
    public static class Seed {
        private boolean enabled = true;
    }
}
