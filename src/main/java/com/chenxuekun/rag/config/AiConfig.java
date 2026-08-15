package com.chenxuekun.rag.config;

import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.time.Duration;

/**
 * AI 能力装配：对话模型 / 流式对话模型 / 向量模型 / 向量存储 / 切分器
 * <p>
 * 默认部署：PostgreSQL + pgvector 持久化向量（docker compose up -d 一键起库）
 * 演示模式：--spring.profiles.active=h2 切内存向量库 + H2 文件库（零外部依赖）
 */
@Configuration
public class AiConfig {

    @Bean
    public ChatModel chatModel(
            @Value("${rag.dashscope.base-url}") String baseUrl,
            @Value("${rag.dashscope.api-key}") String apiKey,
            @Value("${rag.dashscope.chat-model}") String chatModel) {
        return OpenAiChatModel.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .modelName(chatModel)
                .temperature(0.3)          // 知识问答场景：低温度，减少发散
                .timeout(Duration.ofSeconds(60))
                .build();
    }

    @Bean
    public StreamingChatModel streamingChatModel(
            @Value("${rag.dashscope.base-url}") String baseUrl,
            @Value("${rag.dashscope.api-key}") String apiKey,
            @Value("${rag.dashscope.chat-model}") String chatModel) {
        return OpenAiStreamingChatModel.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .modelName(chatModel)
                .temperature(0.3)
                .timeout(Duration.ofSeconds(60))
                .build();
    }

    @Bean
    public EmbeddingModel embeddingModel(
            @Value("${rag.dashscope.base-url}") String baseUrl,
            @Value("${rag.dashscope.api-key}") String apiKey,
            @Value("${rag.dashscope.embedding-model}") String embeddingModel) {
        return OpenAiEmbeddingModel.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .modelName(embeddingModel)
                .build();
    }

    /**
     * 向量持久化：PostgreSQL + pgvector（默认）。
     * useIndex：IVFFlat 近似最近邻索引，写入时自动建表建索引。
     * indexListSize：IVFFlat 倒排聚类的 list 数，经验值 rows/1000（个人项目数据量小，取下限 100）。
     * metadata（kbId）以 JSONB 存储，检索时在库端完成过滤，不拉全量到内存。
     */
    @Bean
    @ConditionalOnProperty(name = "rag.store.type", havingValue = "pgvector", matchIfMissing = true)
    public EmbeddingStore<TextSegment> pgVectorEmbeddingStore(
            DataSource dataSource,
            @Value("${rag.store.dimension}") int dimension) {
        // datasourceBuilder：复用 Spring 连接池；metadata 走默认 JSONB 模式，kbId 过滤在库端完成
        return PgVectorEmbeddingStore.datasourceBuilder()
                .datasource(dataSource)
                .dimension(dimension)
                .table("kb_chunk_vector")
                .useIndex(true)
                .indexListSize(100)
                .createTable(true)
                .build();
    }

    /**
     * 内存向量库：h2 演示模式专用，重启即清空。
     */
    @Bean
    @ConditionalOnProperty(name = "rag.store.type", havingValue = "memory")
    public EmbeddingStore<TextSegment> inMemoryEmbeddingStore() {
        return new InMemoryEmbeddingStore<>();
    }

    @Bean
    public DocumentSplitter documentSplitter() {
        // 递归切分：优先按段落，其次句子；500字符/段，50字符重叠防止语义截断
        // 阿里云模型不被 jtokkit 识别，用字符数代替 token 数
        return DocumentSplitters.recursive(500, 50);
    }
}