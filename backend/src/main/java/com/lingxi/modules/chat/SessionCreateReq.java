package com.lingxi.modules.chat;

import lombok.Data;

@Data
public class SessionCreateReq {

    private String title;

    /** supervisor | knowledge_qa | ops_diagnosis | data_analysis | report */
    private String agentType = "supervisor";
}
