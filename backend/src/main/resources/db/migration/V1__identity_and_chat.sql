-- V1: 身份与对话域
CREATE TABLE sys_user (
    id            BIGINT       NOT NULL COMMENT '雪花ID',
    username      VARCHAR(64)  NOT NULL COMMENT '用户名',
    password      VARCHAR(100) NOT NULL COMMENT 'BCrypt 密码',
    nickname      VARCHAR(64)  NULL COMMENT '昵称',
    role          VARCHAR(20)  NOT NULL DEFAULT 'USER' COMMENT '角色 ADMIN/USER',
    enabled       TINYINT      NOT NULL DEFAULT 1 COMMENT '1启用 0禁用',
    last_login_at DATETIME     NULL,
    deleted       TINYINT      NOT NULL DEFAULT 0,
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_user_username (username)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT ='系统用户';

CREATE TABLE chat_session
(
    id            BIGINT      NOT NULL,
    user_id       BIGINT      NOT NULL,
    title         VARCHAR(128) NULL,
    agent_type    VARCHAR(32) NULL COMMENT 'supervisor/knowledge_qa/ops_diagnosis/data_analysis/report',
    message_count INT         NOT NULL DEFAULT 0,
    deleted       TINYINT     NOT NULL DEFAULT 0,
    created_at    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_chat_session_user (user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT ='对话会话';

CREATE TABLE chat_message
(
    id              BIGINT       NOT NULL,
    session_id      BIGINT       NOT NULL,
    user_id         BIGINT       NOT NULL,
    role            VARCHAR(20)  NOT NULL COMMENT 'user/assistant/tool/system',
    content         MEDIUMTEXT   NULL,
    tool_calls_json TEXT         NULL COMMENT '工具调用明细 JSON',
    round           INT          NOT NULL DEFAULT 0 COMMENT '推理轮次',
    token_count     INT          NULL,
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_chat_message_session (session_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT ='对话消息';

CREATE TABLE agent_tool_invocation
(
    id          BIGINT      NOT NULL,
    session_id  BIGINT      NOT NULL,
    tool_name   VARCHAR(64) NOT NULL,
    arguments   TEXT        NULL,
    result      TEXT        NULL,
    latency_ms  INT         NULL,
    created_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_tool_invocation_session (session_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT ='工具调用审计';
