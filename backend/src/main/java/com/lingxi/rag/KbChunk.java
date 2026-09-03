package com.lingxi.rag;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 知识分块（向量以 vectorId 存于 Qdrant，原文留档便于审计与重建）。
 */
@Data
@TableName("kb_chunk")
public class KbChunk {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long documentId;

    private Long spaceId;

    private Integer chunkIndex;

    private String content;

    private String vectorId;

    private LocalDateTime createdAt;
}
