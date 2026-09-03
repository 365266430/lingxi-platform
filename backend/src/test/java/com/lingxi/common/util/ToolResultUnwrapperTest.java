package com.lingxi.common.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 工具结果解包测试（修复"工具返回值被默认转换器二次 JSON 序列化"缺陷）。
 */
class ToolResultUnwrapperTest {

    @Test
    @DisplayName("剥离 JSON 字符串包装并还原换行")
    void unwrapJsonEscapedString() {
        // 模拟 Spring AI 默认转换器的输出：首尾引号 + 字面 \n
        String raw = "\"| 服务 | 告警数 |\\n|---|---|\\n| api-gateway | 1 |\"";
        String unwrapped = ToolResultUnwrapper.unwrap(raw);
        assertThat(unwrapped).isEqualTo("| 服务 | 告警数 |\n|---|---|\n| api-gateway | 1 |");
    }

    @Test
    @DisplayName("多层包装迭代剥离")
    void unwrapNested() {
        String raw = "\"\\\"内容\\\"\"";
        assertThat(ToolResultUnwrapper.unwrap(raw)).isEqualTo("内容");
    }

    @Test
    @DisplayName("Unicode 转义还原为字符")
    void unwrapUnicode() {
        assertThat(ToolResultUnwrapper.unwrap("\"\\u4e2d\\u6587\"")).isEqualTo("中文");
    }

    @Test
    @DisplayName("普通文本与 null 原样返回")
    void passthrough() {
        assertThat(ToolResultUnwrapper.unwrap("普通文本\n真实换行")).isEqualTo("普通文本\n真实换行");
        assertThat(ToolResultUnwrapper.unwrap(null)).isNull();
        assertThat(ToolResultUnwrapper.unwrap("")).isEmpty();
    }

    @Test
    @DisplayName("转义的引号与反斜杠正确还原")
    void unwrapQuotesAndBackslash() {
        assertThat(ToolResultUnwrapper.unwrap("\"a\\\"b\\\\c\"")).isEqualTo("a\"b\\c");
    }
}
