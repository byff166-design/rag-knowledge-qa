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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * AI 能力装配：对话模型 / 流式对话模型 / 向量模型 / 向量存储 / 切分器
 * <p>
 * 默认部署：H2 + 内存向量库（零外部依赖，一条 mvn 命令即跑）
 * 生产部署：可换 PostgreSQL + pgvector（参考 docker-compose.yml 和 README）
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
     * 内存向量库：开发/演示场景足够，重启即清空。
     * 生产可换 PgVectorEmbeddingStore（pom 里加 langchain4j-pgvector，配置改 host/port）。
     */
    @Bean
    public EmbeddingStore<TextSegment> embeddingStore() {
        return new InMemoryEmbeddingStore<>();
    }

    @Bean
    public DocumentSplitter documentSplitter() {
        // 递归切分：优先按段落，其次句子；500字符/段，50字符重叠防止语义截断
        // 阿里云模型不被 jtokkit 识别，用字符数代替 token 数
        return DocumentSplitters.recursive(500, 50);
    }
}