-- V6: 可配置 Agent 管理
CREATE TABLE ai_agent
(
    id            BIGINT       NOT NULL,
    code          VARCHAR(64)  NOT NULL COMMENT '稳定的 Agent 编码',
    name          VARCHAR(64)  NOT NULL COMMENT '展示名称',
    icon          VARCHAR(16)  NULL,
    description   VARCHAR(255) NULL,
    system_prompt MEDIUMTEXT   NOT NULL,
    tools_json    TEXT         NOT NULL COMMENT '允许使用的工具名称 JSON 数组',
    enabled       TINYINT      NOT NULL DEFAULT 1,
    builtin       TINYINT      NOT NULL DEFAULT 0 COMMENT '内置 Agent 不允许删除',
    sort          INT          NOT NULL DEFAULT 99,
    created_by    BIGINT       NULL,
    updated_by    BIGINT       NULL,
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_ai_agent_code (code),
    KEY idx_ai_agent_enabled_sort (enabled, sort)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT ='企业 Agent 定义';
