package com.chenxuekun.rag.controller;

import com.chenxuekun.rag.entity.KbDocument;
import com.chenxuekun.rag.entity.KnowledgeBase;
import com.chenxuekun.rag.mapper.KnowledgeBaseMapper;
import com.chenxuekun.rag.service.IngestService;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/kb")
@RequiredArgsConstructor
public class KnowledgeBaseController {

    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final IngestService ingestService;

    @Data
    public static class CreateKbRequest {
        @NotBlank(message = "知识库名称不能为空")
        private String name;
        private String description;
    }

    @PostMapping
    public KnowledgeBase create(@RequestBody CreateKbRequest req) {
        KnowledgeBase kb = new KnowledgeBase();
        kb.setName(req.getName());
        kb.setDescription(req.getDescription());
        kb.setCreateTime(LocalDateTime.now());
        knowledgeBaseMapper.insert(kb);
        return kb;
    }

    @GetMapping("/list")
    public List<KnowledgeBase> list() {
        return knowledgeBaseMapper.selectList(null);
    }

    /** 上传文档：解析 → 切分 → 向量化 → 入库 */
    @PostMapping("/{kbId}/document")
    public KbDocument uploadDocument(@PathVariable Long kbId, @RequestParam("file") MultipartFile file) {
        return ingestService.ingest(kbId, file);
    }

    @GetMapping("/{kbId}/documents")
    public List<KbDocument> documents(@PathVariable Long kbId) {
        return ingestService.listDocuments(kbId);
    }
}
