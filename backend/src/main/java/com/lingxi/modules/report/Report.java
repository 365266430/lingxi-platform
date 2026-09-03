package com.lingxi.modules.report;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 生成报告。
 */
@Data
@TableName("rpt_report")
public class Report {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String title;

    @com.baomidou.mybatisplus.annotation.TableField("content")
    private String content;

    private Long sessionId;

    private Long creatorId;

    private LocalDateTime createdAt;
}
