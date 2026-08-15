-- H2 表结构，启动时自动执行（Spring Boot 内嵌数据库默认会跑 schema.sql）
-- 命名与 com.chenxuekun.rag.entity.* 的 @TableName 严格对齐

DROP TABLE IF EXISTS chat_message;
DROP TABLE IF EXISTS chat_session;
DROP TABLE IF EXISTS kb_document;
DROP TABLE IF EXISTS kb;

CREATE TABLE kb (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(128)  NOT NULL,
    description VARCHAR(512),
    create_time TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE kb_document (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    kb_id       BIGINT        NOT NULL,
    file_name   VARCHAR(256)  NOT NULL,
    status      VARCHAR(16)   NOT NULL DEFAULT 'PENDING',
    chunk_count INT           NOT NULL DEFAULT 0,
    create_time TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_kb_document_kb_id ON kb_document(kb_id);

CREATE TABLE chat_session (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    kb_id       BIGINT        NOT NULL,
    title       VARCHAR(256),
    create_time TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE chat_message (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id  BIGINT        NOT NULL,
    role        VARCHAR(16)   NOT NULL,
    content     CLOB          NOT NULL,
    create_time TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP
);
-- 联合索引：覆盖 WHERE session_id=? ORDER BY create_time ASC 的历史消息查询
CREATE INDEX idx_chat_message_session_time ON chat_message(session_id, create_time);