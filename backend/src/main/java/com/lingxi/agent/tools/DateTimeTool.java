package com.lingxi.agent.tools;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * 时间工具。
 */
@Component
public class DateTimeTool {

    @Tool(name = "get_current_time", description = "获取当前服务器日期时间（Asia/Shanghai）与星期")
    public String now() {
        LocalDateTime now = LocalDateTime.now(ZoneId.of("Asia/Shanghai"));
        String week = switch (now.getDayOfWeek()) {
            case MONDAY -> "星期一";
            case TUESDAY -> "星期二";
            case WEDNESDAY -> "星期三";
            case THURSDAY -> "星期四";
            case FRIDAY -> "星期五";
            case SATURDAY -> "星期六";
            case SUNDAY -> "星期日";
        };
        return "当前时间：" + now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + "（" + week + "）";
    }
}
