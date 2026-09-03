package com.lingxi.rag;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 知识文档。
 */
@Data
@TableName("kb_document")
public class KbDocument {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long spaceId;

    private String filename;

    /** MinIO 对象键 */
    private String objectKey;

    private String contentType;

    private Long sizeBytes;

    /** PROCESSING | COMPLETED | FAILED */
    private String status;

    private Integer chunkCount;

    private String errorMessage;

    /** UPLOAD | SEED */
    private String source;

    private Long uploaderId;

    @TableLogic
    @JsonIgnore
    private Integer deleted;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
