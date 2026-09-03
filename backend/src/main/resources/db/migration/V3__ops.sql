-- V3: 运维域（AIOps 演示场景）
CREATE TABLE ops_host
(
    id         BIGINT      NOT NULL,
    host_name  VARCHAR(64) NOT NULL,
    ip         VARCHAR(64) NULL,
    cpu_cores  INT         NULL,
    memory_gb  INT         NULL,
    disk_gb    INT         NULL,
    env        VARCHAR(20) NULL COMMENT 'PROD/STAGING/DEV',
    status     VARCHAR(20) NULL COMMENT 'ONLINE/OFFLINE',
    created_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT ='CMDB 主机';

CREATE TABLE ops_service_info
(
    id           BIGINT       NOT NULL,
    service_name VARCHAR(64)  NOT NULL,
    owner        VARCHAR(64)  NULL COMMENT '负责人',
    tier         INT          NULL COMMENT '核心等级 1~3',
    host_names   VARCHAR(255) NULL COMMENT '部署主机',
    repo         VARCHAR(255) NULL,
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT ='CMDB 服务';

CREATE TABLE ops_alert
(
    id           BIGINT       NOT NULL,
    title        VARCHAR(255) NOT NULL,
    severity     VARCHAR(10)  NOT NULL COMMENT 'P1/P2/P3',
    service_name VARCHAR(64)  NULL,
    host_name    VARCHAR(64)  NULL,
    metric_name  VARCHAR(64)  NULL,
    metric_value VARCHAR(64)  NULL,
    status       VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/RESOLVED',
    description  VARCHAR(1024) NULL,
    started_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    resolved_at  DATETIME     NULL,
    PRIMARY KEY (id),
    KEY idx_ops_alert_status (status),
    KEY idx_ops_alert_started (started_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT ='告警';

CREATE TABLE ops_metric
(
    id           BIGINT      NOT NULL,
    service_name VARCHAR(64) NOT NULL,
    metric_name  VARCHAR(20) NOT NULL COMMENT 'cpu_usage/mem_usage',
    metric_value DOUBLE      NULL,
    collected_at DATETIME    NOT NULL,
    PRIMARY KEY (id),
    KEY idx_ops_metric_service (service_name, collected_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT ='指标时序';
