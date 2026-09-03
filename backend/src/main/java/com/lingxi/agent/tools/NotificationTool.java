package com.lingxi.agent.tools;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * 通知工具（演示模式：写日志模拟发送到值班渠道）。
 */
@Slf4j
@Component
public class NotificationTool {

    @Tool(name = "send_notification",
            description = "向值班渠道发送通知（演示模式：记录日志模拟发送，用于升级告警、通知负责人等场景）")
    public String sendNotification(
            @ToolParam(description = "通知标题") String title,
            @ToolParam(description = "通知正文") String message) {
        log.info("[SIMULATED-NOTIFY] title={} message={}", title, message);
        return "（模拟）通知已发送至值班渠道：《" + title + "》";
    }
}
