# rag-knowledge-qa · 基于 LangChain4j 的 RAG 智能知识库问答系统

> 个人项目 · Java 17 / Spring Boot 3 / LangChain4j / PostgreSQL + pgvector / Redis / Docker / SSE 流式输出

面向**课程资料与企业内部文档的智能问答场景**：用户上传文档后，系统自动完成解析、切分、向量化与入库；提问时基于向量检索召回相关片段，注入提示词后由大模型生成回答，支持 **SSE 流式输出**与**历史会话查询**（Redis 缓存热点会话）。

## 整体架构

```
                        ┌────────────────────────────────────────────┐
                        │              Spring Boot 3                 │
                        │                                            │
  上传文档 ──►  IngestService ──► Apache Tika 解析                    │
                 │              DocumentSplitters 递归切分(500/50)    │
                 │              EmbeddingModel 向量化                 │
                 ▼                                            │       │
        PgVectorEmbeddingStore ◄── similaritySearch ─────────┤       │
        (kb_chunk_vector,            ▲                       │       │
         dim=1024, IVFFlat)          │ top-5 + kbId 过滤     │       │
                 └──────────► RagService(检索→组装Prompt→流式生成)     │
                                        │                      │     │
                                        ▼                      ▼     │
                                 SseEmitter 流式返回   ChatHistory    │
                                                       (PostgreSQL)  │
                                                        ▲            │
                                        Redis 缓存 ──────┘            │
                        └────────────────────────────────────────────┘
```

## 检索增强（RAG）流程

1. **入库**：`Tika 解析 PDF/Word/TXT → recursive splitter 按段落+句子切分（max 500字符，overlap 50）→ text-embedding-v3 向量化 → 写入 pgvector 向量表（metadata 携带 kbId，JSONB 存储）`
2. **提问**：`问题向量化 → pgvector 余弦相似度检索 top-5（按知识库在库端过滤，score>0.6）→ 命中片段注入 Prompt → 要求"仅依据参考资料回答，资料不足时明确返回未找到相关依据" → qwen-plus 流式生成`
3. **返回**：`SSE 逐 token 推送 → 前端打字机效果 → 会话与消息落库 PostgreSQL，历史查询走 Redis 缓存`

## 技术栈

| 层次 | 选型 |
|---|---|
| 语言/框架 | Java 17 · Spring Boot 3.3 · MyBatis-Plus |
| AI 框架 | LangChain4j 1.4（ChatModel / EmbeddingModel / EmbeddingStore / DocumentSplitters） |
| 大模型 | 阿里云百炼 DashScope（OpenAI 兼容协议）：qwen-plus + text-embedding-v3 |
| 向量库 | PostgreSQL + pgvector（PgVectorEmbeddingStore，dim=1024，IVFFlat 索引，metadata JSONB 库端过滤） |
| 业务库 | PostgreSQL（联合索引覆盖历史消息查询）；`--spring.profiles.active=h2` 可切零依赖演示模式 |
| 缓存 | Redis（Cache-Aside 缓存热点会话历史，写后失效保证一致性，异常自动降级直查 DB） |
| 基础设施 | Docker Compose 一键起 PostgreSQL(pgvector) + Redis |
| 交互 | SSE 流式输出 · RESTful API · Postman 全场景测试 |

## 快速开始

### 前置条件

- JDK 17+（开发环境使用 JDK 25）
- Maven 3.6+（或 IDEA 内置 Maven）
- 数据库/缓存，二选一：
  - **Docker Desktop**：一条命令起 PostgreSQL 和 Redis（见下）
  - **云端托管（免安装）**：Neon（PostgreSQL + pgvector）+ Upstash（Redis），见「云端部署（免 Docker）」
- 阿里云百炼 API Key（[开通地址](https://bailian.console.aliyun.com/)，开通后获取 sk-xxx）

### 启动（方式一：Docker 本地起库）

```bash
# 1. 一键起基础设施（PostgreSQL+pgvector、Redis）
docker compose up -d

# 2. 配置大模型 Key（阿里云百炼，OpenAI 兼容模式）
export DASHSCOPE_API_KEY=sk-xxxx

# 3. 打包
mvn package -DskipTests

# 4. 启动（自动建表：业务表走 schema-postgresql.sql，向量表由 PgVectorEmbeddingStore 创建）
java -jar target/rag-knowledge-qa-1.0.0.jar
```

> 零依赖演示模式（无 Docker）：`java -jar target/rag-knowledge-qa-1.0.0.jar --spring.profiles.active=h2`
> 内存向量库 + H2 文件库，重启向量数据清空，仅用于快速体验。

### 云端部署（方式二：免 Docker，Neon + Upstash）

本机无法运行 Docker 时，用免费云托管跑通完整 PG+pgvector+Redis 链路：

1. **PostgreSQL + pgvector**：注册 [neon.tech](https://neon.tech)（GitHub 登录）→ 创建项目 → Dashboard 复制 **JDBC** 格式连接串（形如 `jdbc:postgresql://ep-xxx.aws.neon.tech/neondb?sslmode=require`）
2. **Redis**：注册 [upstash.com](https://upstash.com) → 创建免费 Redis → 复制 `rediss://default:xxx@...` 连接串（`rediss` 前缀自动走 TLS）
3. **启动**（Git Bash，连接串用环境变量注入，不落盘）：

```bash
export DASHSCOPE_API_KEY=sk-xxxx
export PG_JDBC_URL='jdbc:postgresql://ep-xxx.aws.neon.tech/neondb?sslmode=require'
export PG_USERNAME='neondb_owner'
export PG_PASSWORD='xxxx'
export REDIS_URL='rediss://default:xxxx@xxx.upstash.io:6379'
java -jar target/rag-knowledge-qa-1.0.0.jar
```

启动时 schema-postgresql.sql 自动执行（幂等：`CREATE EXTENSION IF NOT EXISTS vector` + `CREATE TABLE IF NOT EXISTS`，云库可重复执行不丢数据），向量表由 PgVectorEmbeddingStore 首次写入时自动创建。

启动后访问：
- API 地址：http://localhost:8080
- 数据库连接（Docker 方式）：`localhost:5432/ragdb`，用户 `rag` / 密码 `rag123456`（docker-compose 默认）

### 快速验证（一键冒烟测试）

项目启动后，在项目根目录执行：

```bash
bash scripts/test-api.sh
```

脚本自动完成 6 步：创建知识库 → 上传 `docs/sample-faq.txt`（14 条后端技术规范）→ 轮询向量化状态 → SSE 流式问答「缓存击穿怎么处理」→ 查看会话与消息历史。看到流式回答即全链路打通。

### 接口一览

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/kb` | 创建知识库 `{ "name": "Java面试八股" }` |
| GET | `/api/kb/list` | 知识库列表 |
| POST | `/api/kb/{kbId}/document` | 上传文档（multipart，PDF/DOCX/TXT） |
| POST | `/api/chat?kbId=1` | 提问（SSE 流式返回） |
| GET | `/api/chat/sessions?kbId=1` | 会话列表 |
| GET | `/api/chat/messages?sessionId=1` | 某会话的历史消息（Redis 缓存加速） |

### curl 示例

```bash
# 创建知识库
curl -X POST http://localhost:8080/api/kb \
     -H "Content-Type: application/json" \
     -d '{"name":"Java面试八股","description":"后端面试常考知识点"}'

# 上传文档
curl -F "file=@./docs/sample-faq.txt" http://localhost:8080/api/kb/1/document

# 流式问答
curl -N -X POST "http://localhost:8080/api/chat?kbId=1" \
     -H "Content-Type: application/json" \
     -d '{"question":"什么是回表查询？"}'
```

## 目录结构

```
rag-knowledge-qa
├── docker-compose.yml          # PostgreSQL(pgvector) + Redis 一键起库
├── docs/
│   └── sample-faq.txt          # 样例知识库文档（14条后端规范）
├── scripts/
│   └── test-api.sh             # 接口冒烟测试脚本
├── src/main/java/com/chenxuekun/rag
│   ├── config/AiConfig.java    # 模型装配 + pgvector/内存向量库条件化切换
│   ├── controller/
│   │   ├── KnowledgeBaseController.java
│   │   ├── ChatController.java  # SSE 流式问答
│   │   └── GlobalExceptionHandler.java
│   ├── service/
│   │   ├── IngestService.java  # 文档解析→切分→向量化→入库
│   │   └── RagService.java     # 检索→Prompt→流式生成 + Redis 历史缓存
│   ├── entity/  mapper/
│   └── RagKnowledgeQaApplication.java
├── src/main/resources/
│   ├── application.yml         # 配置文件（默认 PG+Redis，h2 profile 演示模式）
│   ├── schema-postgresql.sql   # PG 建表脚本（含联合索引，启动自动执行）
│   └── schema-h2.sql           # H2 演示模式建表脚本
└── pom.xml
```

## 面试高频考点（提前备好答案）

1. **为什么选 pgvector 而不是 Milvus/Pinecone？**（个人项目数据量在百万级以内，pgvector 与业务库同实例、无额外运维、支持 SQL 生态和 JSONB 库端过滤；数据量上千万再考虑专用向量数据库）
2. **IVFFlat 和 HNSW 的区别？**（IVFFlat 倒排聚类、建索引快内存省，查询要先扫聚类桶；HNSW 图索引、查询更快召回更高但建索引慢内存大；本项目数据量小用 IVFFlat 足够）
3. **切分策略怎么定的？chunk 太大/太小会怎样？**（500/50 折中：太大稀释相关性、太小丢失上下文；overlap 防止语义被截断）
4. **怎么减少模型幻觉？**（检索结果注入 + "仅依据资料回答"约束 + score 阈值过滤 + 资料不足兜底话术 + 无资料时不调模型直接返回，省 token 且杜绝幻觉）
5. **Redis 缓存策略？一致性怎么保证？**（Cache-Aside：读时先查缓存 miss 回源回填 TTL 30min；写后失效——新消息落库后删 key，下次查询重建；Redis 不可用自动降级直查 DB，不影响主流程）
6. **SSE 和 WebSocket 的取舍？**（单向推送场景 SSE 更轻，天然支持 HTTP 生态）
7. **向量检索为什么可能召回不相关内容？怎么优化？**（Embedding 语义鸿沟 → 调 top-k/score、混合检索、重排序）
