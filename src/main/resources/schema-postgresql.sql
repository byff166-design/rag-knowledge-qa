-- PostgreSQL 建表脚本（默认模式：docker-compose 或云托管 Neon 均适用）
-- 向量表 kb_chunk_vector 由 PgVectorEmbeddingStore 自动创建，无需手写
-- 幂等脚本：可重复执行，不破坏已有数据（云库严禁 DROP）

CREATE EXTENSION IF NOT EXISTS vector;   -- pgvector 扩展（Neon/docker 镜像均预装或允许创建）

CREATE TABLE IF NOT EXISTS kb (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(128)  NOT NULL,
    description VARCHAR(512),
    create_time TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS kb_document (
    id          BIGSERIAL PRIMARY KEY,
    kb_id       BIGINT        NOT NULL,
    file_name   VARCHAR(256)  NOT NULL,
    status      VARCHAR(16)   NOT NULL DEFAULT 'PENDING',
    chunk_count INT           NOT NULL DEFAULT 0,
    create_time TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_kb_document_kb_id ON kb_document(kb_id);

CREATE TABLE IF NOT EXISTS chat_session (
    id          BIGSERIAL PRIMARY KEY,
    kb_id       BIGINT        NOT NULL,
    title       VARCHAR(256),
    create_time TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS chat_message (
    id          BIGSERIAL PRIMARY KEY,
    session_id  BIGINT        NOT NULL,
    role        VARCHAR(16)   NOT NULL,
    content     TEXT          NOT NULL,
    create_time TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
);
-- 联合索引：覆盖 WHERE session_id=? ORDER BY create_time ASC 的历史消息查询
CREATE INDEX IF NOT EXISTS idx_chat_message_session_time ON chat_message(session_id, create_time);
