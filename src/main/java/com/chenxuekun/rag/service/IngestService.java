package com.chenxuekun.rag.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chenxuekun.rag.entity.KbDocument;
import com.chenxuekun.rag.mapper.KbDocumentMapper;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.parser.apache.tika.ApacheTikaDocumentParser;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 文档入库流水线：解析(Apache Tika) → 切分(递归500/50) → 向量化 → 写入内存向量库
 * metadata 携带 kbId，检索时按知识库隔离。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IngestService {

    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> embeddingStore;
    private final DocumentSplitter documentSplitter;
    private final KbDocumentMapper kbDocumentMapper;

    public KbDocument ingest(Long kbId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("上传文件不能为空");
        }
        KbDocument doc = new KbDocument();
        doc.setKbId(kbId);
        doc.setFileName(file.getOriginalFilename());
        doc.setStatus("PROCESSING");
        doc.setCreateTime(LocalDateTime.now());
        kbDocumentMapper.insert(doc);

        try (InputStream in = file.getInputStream()) {
            // 1. 解析：Tika 自动识别 PDF/DOCX/TXT
            Document document = new ApacheTikaDocumentParser().parse(in);

            // 2. 切分 + metadata 标记归属知识库
            List<TextSegment> segments = documentSplitter.split(document);
            segments.forEach(s -> {
                s.metadata().put("kbId", String.valueOf(kbId));
                s.metadata().put("docId", String.valueOf(doc.getId()));
                s.metadata().put("source", file.getOriginalFilename());
            });

            // 3. 向量化 + 4. 入库内存向量库
            List<Embedding> embeddings = embeddingModel.embedAll(segments).content();
            embeddingStore.addAll(embeddings, segments);

            doc.setChunkCount(segments.size());
            doc.setStatus("DONE");
            kbDocumentMapper.updateById(doc);
            log.info("文档入库完成 kbId={}, file={}, chunks={}", kbId, file.getOriginalFilename(), segments.size());
            return doc;
        } catch (Exception e) {
            doc.setStatus("FAILED");
            kbDocumentMapper.updateById(doc);
            log.error("文档入库失败 kbId={}, file={}", kbId, file.getOriginalFilename(), e);
            throw new IllegalStateException("文档解析或向量化失败: " + e.getMessage(), e);
        }
    }

    public List<KbDocument> listDocuments(Long kbId) {
        return kbDocumentMapper.selectList(
                new LambdaQueryWrapper<KbDocument>().eq(KbDocument::getKbId, kbId)
                        .orderByDesc(KbDocument::getCreateTime));
    }
}
