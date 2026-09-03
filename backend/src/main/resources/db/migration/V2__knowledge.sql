-- V2: 知识库域
CREATE TABLE kb_space
(
    id          BIGINT       NOT NULL,
    name        VARCHAR(64)  NOT NULL,
    description VARCHAR(255) NULL,
    owner_id    BIGINT       NULL,
    doc_count   INT          NOT NULL DEFAULT 0,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT ='知识空间';

CREATE TABLE kb_document
(
    id            BIGINT       NOT NULL,
    space_id      BIGINT       NOT NULL,
    filename      VARCHAR(255) NOT NULL,
    object_key    VARCHAR(512) NULL COMMENT 'MinIO 对象键',
    content_type  VARCHAR(128) NULL,
    size_bytes    BIGINT       NULL,
    status        VARCHAR(20)  NOT NULL DEFAULT 'PROCESSING' COMMENT 'PROCESSING/COMPLETED/FAILED',
    chunk_count   INT          NULL,
    error_message VARCHAR(1024) NULL,
    source        VARCHAR(20)  NOT NULL DEFAULT 'UPLOAD' COMMENT 'UPLOAD/SEED',
    uploader_id   BIGINT       NULL,
    deleted       TINYINT      NOT NULL DEFAULT 0,
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_kb_document_space (space_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT ='知识文档';

CREATE TABLE kb_chunk
(
    id          BIGINT      NOT NULL,
    document_id BIGINT      NOT NULL,
    space_id    BIGINT      NOT NULL,
    chunk_index INT         NOT NULL DEFAULT 0,
    content     TEXT        NULL,
    vector_id   VARCHAR(64) NULL COMMENT 'Qdrant 向量点 ID',
    created_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_kb_chunk_document (document_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT ='知识分块';
