package com.lingxi.modules.chat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ChatStreamReq {

    @NotBlank(message = "消息内容不能为空")
    @Size(max = 8000, message = "消息最长 8000 字符")
    private String content;
}
