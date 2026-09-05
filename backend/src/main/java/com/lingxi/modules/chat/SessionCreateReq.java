package com.lingxi.modules.chat;

import lombok.Data;

@Data
public class SessionCreateReq {

    private String title;

    /** Agent 编码；缺省使用 supervisor */
    private String agentType = "supervisor";
}
