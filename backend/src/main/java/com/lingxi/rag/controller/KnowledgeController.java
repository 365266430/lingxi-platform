package com.lingxi.rag.controller;

import com.lingxi.common.api.Result;
import com.lingxi.common.exception.BizException;
import com.lingxi.config.RabbitConfig;
import com.lingxi.rag.KbDocument;
import com.lingxi.rag.KbDocumentMapper;
import com.lingxi.rag.KbSpace;
import com.lingxi.rag.KbSpaceMapper;
import com.lingxi.rag.pipeline.DocIngestMessage;
import com.lingxi.rag.service.FileStorageService;
import com.lingxi.rag.service.IngestionService;
import com.lingxi.rag.service.RetrievalService;
import com.lingxi.security.SecurityUtils;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Tag(name = "知识库")
@Slf4j
@RestController
@RequestMapping("/api/kb")
@RequiredArgsConstructor
public class KnowledgeController {

    private final KbSpaceMapper spaceMapper;
    private final KbDocumentMapper documentMapper;
    private final FileStorageService fileStorageService;
    private final IngestionService ingestionService;
    private final RetrievalService retrievalService;
    private final RabbitTemplate rabbitTemplate;

    @Operation(summary = "创建知识空间")
    @PostMapping("/spaces")
    public Result<KbSpace> createSpace(@RequestBody Map<String, String> body) {
        KbSpace space = new KbSpace();
        space.setName(required(body.get("name"), "空间名称不能为空"));
        space.setDescription(body.get("description"));
        space.setOwnerId(SecurityUtils.currentUserId());
        space.setDocCount(0);
        spaceMapper.insert(space);
        return Result.ok(space);
    }

    @Operation(summary = "知识空间列表")
    @GetMapping("/spaces")
    public Result<List<KbSpace>> spaces() {
        return Result.ok(spaceMapper.selectList(
                new LambdaQueryWrapper<KbSpace>().orderByDesc(KbSpace::getCreatedAt)));
    }

    @Operation(summary = "上传文档（异步摄取）")
    @PostMapping("/documents/upload")
    public Result<KbDocument> upload(@RequestParam Long spaceId, @RequestParam("file") MultipartFile file)
            throws IOException {
        KbSpace space = spaceMapper.selectById(spaceId);
        if (space == null) {
            throw BizException.notFound("知识空间");
        }
        if (file.isEmpty()) {
            throw new BizException("文件为空");
        }
        String filename = file.getOriginalFilename() == null ? "unnamed" : file.getOriginalFilename();
        byte[] content = file.getBytes();

        KbDocument doc = new KbDocument();
        doc.setSpaceId(spaceId);
        doc.setFilename(filename);
        doc.setContentType(file.getContentType());
        doc.setSizeBytes((long) content.length);
        doc.setStatus("PROCESSING");
        doc.setSource("UPLOAD");
        doc.setUploaderId(SecurityUtils.currentUserId());
        documentMapper.insert(doc);

        try {
            String objectKey = fileStorageService.upload(doc.getId(), filename, content, file.getContentType());
            doc.setObjectKey(objectKey);
            KbDocument update = new KbDocument();
            update.setId(doc.getId());
            update.setObjectKey(objectKey);
            documentMapper.updateById(update);
        } catch (Exception e) {
            markFailed(doc.getId(), e.getMessage());
            throw e;
        }

        try {
            rabbitTemplate.convertAndSend(RabbitConfig.DOC_EXCHANGE, RabbitConfig.DOC_ROUTING_KEY,
                    new DocIngestMessage(doc.getId(), doc.getObjectKey(), filename));
        } catch (AmqpException e) {
            // 消息队列不可用时降级为同步摄取，保证功能可用
            log.warn("RabbitMQ 不可用，降级为同步摄取 docId={}", doc.getId(), e);
            try {
                ingestionService.ingest(doc.getId(), content, filename);
            } catch (Exception ingestError) {
                markFailed(doc.getId(), ingestError.getMessage());
            }
        }
        return Result.ok(documentMapper.selectById(doc.getId()));
    }

    @Operation(summary = "文档列表")
    @GetMapping("/documents")
    public Result<List<KbDocument>> documents(@RequestParam(required = false) Long spaceId) {
        return Result.ok(documentMapper.selectList(new LambdaQueryWrapper<KbDocument>()
                .eq(spaceId != null, KbDocument::getSpaceId, spaceId)
                .orderByDesc(KbDocument::getCreatedAt)));
    }

    @Operation(summary = "删除文档（含向量与对象存储）")
    @DeleteMapping("/documents/{id}")
    public Result<Void> deleteDocument(@PathVariable Long id) {
        KbDocument doc = documentMapper.selectById(id);
        if (doc == null) {
            throw BizException.notFound("文档");
        }
        ingestionService.deleteVectors(doc);
        if (doc.getObjectKey() != null) {
            fileStorageService.delete(doc.getObjectKey());
        }
        documentMapper.deleteById(id);
        return Result.ok();
    }

    @Operation(summary = "重新解析文档（清空旧向量后重灌）")
    @PostMapping("/documents/{id}/reingest")
    public Result<KbDocument> reingest(@PathVariable Long id) {
        KbDocument doc = documentMapper.selectById(id);
        if (doc == null) {
            throw BizException.notFound("文档");
        }
        if (doc.getObjectKey() == null) {
            throw new BizException("该文档没有对象存储原件（内置种子文档不支持重新解析）");
        }
        doc.setStatus("PROCESSING");
        doc.setErrorMessage(null);
        documentMapper.updateById(doc);
        try {
            byte[] content = fileStorageService.download(doc.getObjectKey());
            rabbitTemplate.convertAndSend(RabbitConfig.DOC_EXCHANGE, RabbitConfig.DOC_ROUTING_KEY,
                    new DocIngestMessage(doc.getId(), doc.getObjectKey(), doc.getFilename()));
        } catch (AmqpException e) {
            log.warn("重新解析：MQ 不可用，降级同步摄取 docId={}", doc.getId(), e);
            try {
                byte[] content = fileStorageService.download(doc.getObjectKey());
                ingestionService.ingest(doc.getId(), content, doc.getFilename());
            } catch (Exception ingestError) {
                markFailed(doc.getId(), ingestError.getMessage());
            }
        }
        return Result.ok(documentMapper.selectById(id));
    }

    @Operation(summary = "下载文档")
    @GetMapping("/documents/{id}/download")
    public ResponseEntity<byte[]> download(@PathVariable Long id) {
        KbDocument doc = documentMapper.selectById(id);
        if (doc == null) {
            throw BizException.notFound("文档");
        }
        byte[] bytes = fileStorageService.download(doc.getObjectKey());
        String encoded = URLEncoder.encode(doc.getFilename(), StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encoded)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(bytes);
    }

    @Operation(summary = "检索测试")
    @PostMapping("/search")
    public Result<Map<String, Object>> search(@Valid @RequestBody SearchReq req) {
        List<RetrievalService.Hit> hits = retrievalService.retrieve(req.getSpaceId(), req.getQuery(),
                req.getTopK() == null ? 4 : req.getTopK());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("markdown", retrievalService.retrieveMarkdown(req.getSpaceId(), req.getQuery(),
                req.getTopK() == null ? 4 : req.getTopK()));
        result.put("hits", hits);
        return Result.ok(result);
    }

    private void markFailed(Long documentId, String message) {
        KbDocument update = new KbDocument();
        update.setId(documentId);
        update.setStatus("FAILED");
        update.setErrorMessage(message);
        update.setUpdatedAt(LocalDateTime.now());
        documentMapper.updateById(update);
    }

    private String required(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new BizException(message);
        }
        return value;
    }
}
