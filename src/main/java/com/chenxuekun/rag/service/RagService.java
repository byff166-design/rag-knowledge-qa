package com.chenxuekun.rag.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chenxuekun.rag.entity.ChatMessage;
import com.chenxuekun.rag.entity.ChatSession;
import com.chenxuekun.rag.mapper.ChatMessageMapper;
import com.chenxuekun.rag.mapper.ChatSessionMapper;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.filter.MetadataFilterBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 检索增强问答：问题向量化 → 内存向量库相似度检索(kbId过滤) → 组装 Prompt → 流式生成
 * 强约束：仅依据检索资料回答，资料不足时明确返回"未找到相关依据"。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RagService {

    private static final String SYSTEM_PROMPT = """
            你是企业知识库问答助手。请严格遵守：
            1. 仅依据【参考资料】回答用户问题，不要编造；
            2. 如果参考资料不足以回答，直接回复"未找到相关依据"，不要猜测；
            3. 回答末尾不需要提及参考资料本身。
            """;

    private final EmbeddingModel embeddingModel;
    private final StreamingChatModel streamingChatModel;
    private final EmbeddingStore<TextSegment> embeddingStore;
    private final ChatSessionMapper chatSessionMapper;
    private final ChatMessageMapper chatMessageMapper;

    @Value("${rag.retrieve.max-results}")
    private int maxResults;

    @Value("${rag.retrieve.min-score}")
    private double minScore;

    private final ExecutorService executor = Executors.newCachedThreadPool();

    /** 流式问答，结果通过 SSE 逐 token 推送 */
    public SseEmitter chatStream(Long sessionId, Long kbId, String question) {
        if (question == null || question.isBlank()) {
            throw new IllegalArgumentException("问题不能为空");
        }

        SseEmitter emitter = new SseEmitter(120_000L);

        // 1. 检索相关片段
        List<String> references = retrieve(kbId, question);

        // 2. 无相关资料：直接兜底，不调用模型（省 token 且杜绝幻觉）
        if (references.isEmpty()) {
            executor.execute(() -> {
                try {
                    emitter.send(SseEmitter.event().data("未找到相关依据"));
                    saveMessage(sessionId, "assistant", "未找到相关依据");
                    emitter.send(SseEmitter.event().name("done").data("[DONE]"));
                    emitter.complete();
                } catch (IOException ignored) {
                }
            });
            return emitter;
        }

        // 3. 组装 Prompt（系统约束 + 参考资料注入 + 用户问题）
        ChatRequest request = ChatRequest.builder()
                .messages(List.of(
                        SystemMessage.from(SYSTEM_PROMPT),
                        UserMessage.from(buildPrompt(question, references))))
                .build();

        StringBuilder answer = new StringBuilder();

        // 4. 流式生成 + SSE 推送
        executor.execute(() -> streamingChatModel.chat(request, new StreamingChatResponseHandler() {
            @Override
            public void onPartialResponse(String token) {
                answer.append(token);
                try {
                    emitter.send(SseEmitter.event().data(token));
                } catch (IOException e) {
                    emitter.completeWithError(e);
                }
            }

            @Override
            public void onCompleteResponse(ChatResponse completeResponse) {
                saveMessage(sessionId, "assistant", answer.toString());
                try {
                    emitter.send(SseEmitter.event().name("done").data("[DONE]"));
                    emitter.complete();
                } catch (IOException ignored) {
                }
            }

            @Override
            public void onError(Throwable error) {
                log.error("流式生成失败 sessionId={}", sessionId, error);
                emitter.completeWithError(error);
            }
        }));
        return emitter;
    }

    /** 相似度检索：按 kbId 过滤 + score 阈值 */
    private List<String> retrieve(Long kbId, String question) {
        Embedding questionEmbedding = embeddingModel.embed(question).content();
        Filter kbFilter = MetadataFilterBuilder.metadataKey("kbId").isEqualTo(String.valueOf(kbId));

        return embeddingStore.search(EmbeddingSearchRequest.builder()
                        .queryEmbedding(questionEmbedding)
                        .maxResults(maxResults)
                        .minScore(minScore)
                        .filter(kbFilter)
                        .build())
                .matches()
                .stream()
                .map(m -> m.embedded().text())
                .toList();
    }

    private String buildPrompt(String question, List<String> references) {
        StringBuilder sb = new StringBuilder("【参考资料】\n");
        for (int i = 0; i < references.size(); i++) {
            sb.append(i + 1).append(". ").append(references.get(i)).append("\n");
        }
        sb.append("\n【问题】\n").append(question);
        return sb.toString();
    }

    /** 创建会话 */
    public ChatSession createSession(Long kbId, String title) {
        ChatSession session = new ChatSession();
        session.setKbId(kbId);
        session.setTitle(title == null || title.isBlank()
                ? "会话-" + LocalDateTime.now().toLocalTime().withNano(0)
                : title);
        session.setCreateTime(LocalDateTime.now());
        chatSessionMapper.insert(session);
        return session;
    }

    public void saveMessage(Long sessionId, String role, String content) {
        ChatMessage msg = new ChatMessage();
        msg.setSessionId(sessionId);
        msg.setRole(role);
        msg.setContent(content);
        msg.setCreateTime(LocalDateTime.now());
        chatMessageMapper.insert(msg);
    }

    public List<ChatSession> listSessions(Long kbId) {
        return chatSessionMapper.selectList(
                new LambdaQueryWrapper<ChatSession>().eq(ChatSession::getKbId, kbId)
                        .orderByDesc(ChatSession::getCreateTime));
    }

    public List<ChatMessage> listMessages(Long sessionId) {
        return chatMessageMapper.selectList(
                new LambdaQueryWrapper<ChatMessage>().eq(ChatMessage::getSessionId, sessionId)
                        .orderByAsc(ChatMessage::getCreateTime));
    }
}
