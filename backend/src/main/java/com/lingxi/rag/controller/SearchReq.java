package com.lingxi.rag.controller;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SearchReq {

    /** 为空时跨全部空间检索 */
    private Long spaceId;

    @NotBlank(message = "查询内容不能为空")
    private String query;

    private Integer topK = 4;
}
