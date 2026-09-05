-- 本地 MySQL 首次启动准备：与 Docker 内部 MySQL 的数据库相互独立。
-- 在 mysql 客户端连接到本机实例后执行；已有数据库不会被删除或覆盖。
CREATE DATABASE IF NOT EXISTS lingxi
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;
-- 表结构由后端启动时的 Flyway V1~V5 迁移创建，无需手动导入表。
