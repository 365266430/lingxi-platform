package com.lingxi.common.util;

/**
 * 工具返回结果解包：Spring AI 默认的 ToolCallResultConverter 会对 String 返回值再做一次
 * JSON 序列化（首尾加引号、换行转义为字面 \n），导致 SSE 事件 / 会话记录 / Mock 剧本
 * 引用工具结果时出现转义文本。此处统一识别并还原为原始字符串。
 */
public final class ToolResultUnwrapper {

    private ToolResultUnwrapper() {
    }

    public static String unwrap(String raw) {
        if (raw == null || raw.length() < 2) {
            return raw;
        }
        String s = raw.trim();
        // 迭代剥离多层 JSON 字符串包装（最多 3 层，防重复序列化）
        for (int i = 0; i < 3 && isJsonStringWrapped(s); i++) {
            s = unwrapOnce(s);
        }
        return s;
    }

    private static boolean isJsonStringWrapped(String s) {
        return s.length() >= 2 && s.charAt(0) == '"' && s.charAt(s.length() - 1) == '"';
    }

    private static String unwrapOnce(String s) {
        String inner = s.substring(1, s.length() - 1);
        StringBuilder sb = new StringBuilder(inner.length());
        for (int i = 0; i < inner.length(); i++) {
            char c = inner.charAt(i);
            if (c == '\\' && i + 1 < inner.length()) {
                char next = inner.charAt(++i);
                switch (next) {
                    case 'n' -> sb.append('\n');
                    case 't' -> sb.append('\t');
                    case 'r' -> sb.append('\r');
                    case '"' -> sb.append('"');
                    case '\\' -> sb.append('\\');
                    case '/' -> sb.append('/');
                    case 'u' -> {
                        if (i + 4 < inner.length()) {
                            try {
                                sb.append((char) Integer.parseInt(inner.substring(i + 1, i + 5), 16));
                                i += 4;
                            } catch (NumberFormatException e) {
                                sb.append('\\').append('u');
                            }
                        } else {
                            sb.append('\\').append('u');
                        }
                    }
                    default -> sb.append('\\').append(next);
                }
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
