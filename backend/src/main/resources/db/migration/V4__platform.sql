-- V4: 平台域（模型配置 / 报告）
CREATE TABLE ai_model_config
(
    id                 BIGINT       NOT NULL,
    provider           VARCHAR(20)  NULL COMMENT 'mock/openai',
    base_url           VARCHAR(255) NULL,
    api_key            VARCHAR(255) NULL,
    model              VARCHAR(64)  NULL,
    temperature        DOUBLE       NULL,
    embedding_base_url VARCHAR(255) NULL,
    embedding_api_key  VARCHAR(255) NULL,
    embedding_model    VARCHAR(64)  NULL,
    runtime_override   TINYINT      NOT NULL DEFAULT 0 COMMENT '1=DB配置覆盖环境配置',
    updated_by         VARCHAR(64)  NULL,
    updated_at         DATETIME     NULL,
    PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT ='模型配置（单行 id=1）';

CREATE TABLE rpt_report
(
    id         BIGINT       NOT NULL,
    title      VARCHAR(255) NOT NULL,
    content    MEDIUMTEXT   NULL COMMENT 'Markdown 内容',
    session_id BIGINT       NULL COMMENT '生成会话',
    creator_id BIGINT       NULL,
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT ='AI 报告';
