package com.chenxuekun.rag.controller;

import com.chenxuekun.rag.entity.ChatMessage;
import com.chenxuekun.rag.entity.ChatSession;
import com.chenxuekun.rag.service.RagService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final RagService ragService;

    @Data
    public static class ChatRequest {
        private String question;
        /** 不传则新建会话 */
        private Long sessionId;
        private String title;
    }

    /** SSE 流式问答：POST /api/chat?kbId=1 */
    @PostMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chat(@RequestParam Long kbId, @RequestBody ChatRequest req) {
        Long sessionId = req.getSessionId() != null
                ? req.getSessionId()
                : ragService.createSession(kbId, req.getTitle()).getId();

        ragService.saveMessage(sessionId, "user", req.getQuestion());

        SseEmitter emitter = ragService.chatStream(sessionId, kbId, req.getQuestion());
        // 会话 id 先推给前端，便于后续查询历史
        try {
            emitter.send(SseEmitter.event().name("session").data(sessionId));
        } catch (Exception ignored) {
        }
        return emitter;
    }

    @GetMapping("/sessions")
    public List<ChatSession> sessions(@RequestParam Long kbId) {
        return ragService.listSessions(kbId);
    }

    @GetMapping("/messages")
    public List<ChatMessage> messages(@RequestParam Long sessionId) {
        return ragService.listMessages(sessionId);
    }
}
