# rag-knowledge-qa · 基于 LangChain4j 的 RAG 智能知识库问答系统

> 个人项目 · Java 17 / Spring Boot 3 / LangChain4j / H2 / SSE 流式输出

面向**课程资料与企业内部文档的智能问答场景**：用户上传文档后，系统自动完成解析、切分、向量化与入库；提问时基于向量检索召回相关片段，注入提示词后由大模型生成回答，支持 **SSE 流式输出**与**历史会话查询**。

## 整体架构

```
                        ┌────────────────────────────────────────────┐
                        │              Spring Boot 3                 │
                        │                                            │
  上传文档 ──►  IngestService ──► Apache Tika 解析                    │
                 │              DocumentSplitters 递归切分(500/50)    │
                 │              EmbeddingModel 向量化                 │
                 ▼                                            │       │
        InMemoryEmbeddingStore ◄── similaritySearch ──────────┤       │
        (dim=1024)                    ▲                       │       │
                 │                     │ top-5 + kbId 过滤     │       │
                 └──────────► RagService(检索→组装Prompt→流式生成)     │
                                        │                      │       │
                                        ▼                      ▼       │
                                 SseEmitter 流式返回    ChatHistory     │
                                                        (H2 文件库)    │
                        └────────────────────────────────────────────┘
```

## 检索增强（RAG）流程

1. **入库**：`Tika 解析 PDF/Word/TXT → recursive splitter 按段落+句子切分（max 500字符，overlap 50）→ text-embedding-v3 向量化 → 写入内存向量库（metadata 携带 kbId）`
2. **提问**：`问题向量化 → 内存向量库余弦相似度检索 top-5（按知识库过滤，score>0.6）→ 命中片段注入 Prompt → 要求"仅依据参考资料回答，资料不足时明确返回未找到相关依据" → qwen-plus 流式生成`
3. **返回**：`SSE 逐 token 推送 → 前端打字机效果 → 会话与消息落库 H2`

## 技术栈

| 层次 | 选型 |
|---|---|
| 语言/框架 | Java 17 · Spring Boot 3.3 · MyBatis-Plus |
| AI 框架 | LangChain4j 1.4（ChatModel / EmbeddingModel / EmbeddingStore / DocumentSplitters） |
| 大模型 | 阿里云百炼 DashScope（OpenAI 兼容协议）：qwen-plus + text-embedding-v3 |
| 向量库 | InMemoryEmbeddingStore（开发/演示）；生产可换 PgVectorEmbeddingStore |
| 业务库 | H2 文件库（零外部依赖，重启不丢数据）；生产可换 MySQL/PostgreSQL |
| 交互 | SSE 流式输出 · RESTful API · Postman 全场景测试 |

## 快速开始

### 前置条件

- JDK 17+（开发环境使用 JDK 25）
- Maven 3.6+
- 阿里云百炼 API Key（[开通地址](https://bailian.console.aliyun.com/)，开通后获取 sk-xxx）

### 启动

```bash
# 1. 配置大模型 Key（阿里云百炼，OpenAI 兼容模式）
export DASHSCOPE_API_KEY=sk-xxxx

# 2. 打包
mvn package -DskipTests

# 3. 启动（H2 数据库自动建表，无需额外配置）
java -jar target/rag-knowledge-qa-1.0.0.jar

# 或直接用 Maven 运行
mvn spring-boot:run
```

启动后访问：
- API 地址：http://localhost:8080
- H2 控制台：http://localhost:8080/h2-console（JDBC URL: `jdbc:h2:file:./data/ragdb`，用户名: sa，密码空）

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
| GET | `/api/chat/messages?sessionId=1` | 某会话的历史消息 |

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
├── docs/
│   └── sample-faq.txt          # 样例知识库文档（14条后端规范）
├── scripts/
│   └── test-api.sh             # 接口冒烟测试脚本
├── src/main/java/com/chenxuekun/rag
│   ├── config/AiConfig.java    # 模型与向量库装配
│   ├── controller/
│   │   ├── KnowledgeBaseController.java
│   │   ├── ChatController.java  # SSE 流式问答
│   │   └── GlobalExceptionHandler.java
│   ├── service/
│   │   ├── IngestService.java  # 文档解析→切分→向量化→入库
│   │   └── RagService.java     # 检索→Prompt→流式生成
│   ├── entity/  mapper/
│   └── RagKnowledgeQaApplication.java
├── src/main/resources/
│   ├── application.yml         # 配置文件
│   └── schema.sql              # H2 建表脚本（启动自动执行）
└── pom.xml
```

## 生产部署

当前为零外部依赖的开发配置（H2 文件库 + 内存向量库）。生产环境切换步骤：

1. **业务库换 MySQL/PostgreSQL**：修改 `application.yml` 的 `spring.datasource` 配置，删除 `spring.sql.init.mode`
2. **向量库换 PgVector**：pom.xml 加 `langchain4j-pgvector` 依赖，`AiConfig.embeddingStore()` 改为 `PgVectorEmbeddingStore`
3. **API Key 管理**：通过环境变量 `DASHSCOPE_API_KEY` 注入，不硬编码

## 面试高频考点（提前备好答案）

1. **为什么用内存向量库而不是 Milvus/Pinecone？**（开发演示阶段数据量小、零依赖快速验证；生产切 PgVector 或专用向量库，按数据量级决定）
2. **切分策略怎么定的？chunk 太大/太小会怎样？**（500/50 折中：太大稀释相关性、太小丢失上下文；overlap 防止语义被截断）
3. **怎么减少模型幻觉？**（检索结果注入 + "仅依据资料回答"约束 + score 阈值过滤 + 资料不足兜底话术）
4. **SSE 和 WebSocket 的取舍？**（单向推送场景 SSE 更轻，天然支持 HTTP 生态）
5. **向量检索为什么可能召回不相关内容？怎么优化？**（Embedding 语义鸿沟 → 调 top-k/score、混合检索、重排序）
