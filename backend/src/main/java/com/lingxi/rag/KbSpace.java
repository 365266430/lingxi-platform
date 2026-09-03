package com.lingxi.rag;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 知识空间。
 */
@Data
@TableName("kb_space")
public class KbSpace {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String name;

    private String description;

    private Long ownerId;

    private Integer docCount;

    private LocalDateTime createdAt;
}
