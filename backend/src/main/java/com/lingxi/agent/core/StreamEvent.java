package com.lingxi.agent.core;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * SSE 流事件。type: message / tool_call / tool_result / done / error
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StreamEvent {

    private String type;
    private Map<String, Object> data;
    private Long sessionId;
    private String messageId;
    private Integer round;
    private long timestamp;

    public static StreamEvent of(String type, Map<String, Object> data, Long sessionId, String messageId, Integer round) {
        return new StreamEvent(type, data, sessionId, messageId, round, System.currentTimeMillis());
    }
}
