package com.lingxi.rag.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 文本分块单元测试。
 */
class ChunkingServiceTest {

    private final ChunkingService chunkingService = new ChunkingService();

    @Test
    @DisplayName("短文本不分块")
    void shortTextSingleChunk() {
        List<String> chunks = chunkingService.chunk("只有一段很短的运维文档内容。");
        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0)).contains("运维文档");
    }

    @Test
    @DisplayName("空文本返回空列表")
    void blankText() {
        assertThat(chunkingService.chunk("  ")).isEmpty();
        assertThat(chunkingService.chunk(null)).isEmpty();
    }

    @Test
    @DisplayName("长文本按目标长度分块且保留重叠")
    void longTextMultiChunkWithOverlap() {
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= 60; i++) {
            sb.append("第").append(i).append("步：检查系统运行状态并记录指标数值，确认是否存在异常波动。\n");
        }
        List<String> chunks = chunkingService.chunk(sb.toString(), 400, 100);
        assertThat(chunks.size()).isGreaterThanOrEqualTo(5);
        assertThat(chunks.get(0).length()).isLessThanOrEqualTo(420);
        // 相邻块存在重叠（第 N 块尾部内容出现在第 N+1 块开头）
        String tail = chunks.get(0).strip();
        tail = tail.substring(Math.max(0, tail.length() - 60)).strip();
        assertThat(chunks.get(1)).contains(tail.substring(0, Math.min(40, tail.length())));
    }

    @Test
    @DisplayName("无换行的超长单段被句子边界二次切分")
    void oversizeSentenceSplit() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 80; i++) {
            sb.append("这是一个用于测试的完整句子，编号").append(i).append("。");
        }
        List<String> chunks = chunkingService.chunk(sb.toString(), 300, 50);
        assertThat(chunks.size()).isGreaterThanOrEqualTo(5);
        assertThat(chunks).allSatisfy(c -> assertThat(c.length()).isLessThanOrEqualTo(360));
    }
}
