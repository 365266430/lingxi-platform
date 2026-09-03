-- V5: 体验增强（消息反馈 / 快捷提示词模板）
ALTER TABLE chat_message
    ADD COLUMN feedback TINYINT NULL COMMENT '助手消息反馈：1赞 -1踩 NULL未评' AFTER token_count;

CREATE TABLE sys_prompt_template
(
    id         BIGINT       NOT NULL COMMENT '雪花ID',
    title      VARCHAR(64)  NOT NULL COMMENT '模板标题',
    icon       VARCHAR(16)  NULL COMMENT '图标 emoji',
    content    VARCHAR(1024) NOT NULL COMMENT '提示词内容',
    sort       INT          NOT NULL DEFAULT 0 COMMENT '排序',
    enabled    TINYINT      NOT NULL DEFAULT 1 COMMENT '1启用 0停用',
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT ='快捷提示词模板';

INSERT INTO sys_prompt_template (id, title, icon, content, sort) VALUES
(1, '诊断 CPU 告警', '🩺', '订单服务 CPU 告警，帮我诊断一下', 1),
(2, '统计活跃告警', '📊', '统计一下各服务当前活跃告警数量', 2),
(3, '查处置手册', '📚', '磁盘空间不足的处置手册是什么', 3),
(4, '生成巡检报告', '📝', '帮我生成一份今天的巡检报告', 4),
(5, '分析服务风险', '🔍', '分析一下当前哪些服务风险最高，给出排序和理由', 5),
(6, '查看资产概况', '🖥️', '查询一下 CMDB 里核心等级为 T1 的服务部署在哪些主机上', 6);
