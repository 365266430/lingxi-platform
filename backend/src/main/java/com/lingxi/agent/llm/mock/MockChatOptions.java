package com.lingxi.agent.llm.mock;

import org.springframework.ai.chat.prompt.ChatOptions;

import java.util.List;

/**
 * Mock 模型的默认选项。
 */
public class MockChatOptions implements ChatOptions {

    @Override
    public String getModel() {
        return MockChatModel.MODEL_ID;
    }

    @Override
    public Double getFrequencyPenalty() {
        return null;
    }

    @Override
    public Integer getMaxTokens() {
        return null;
    }

    @Override
    public Double getPresencePenalty() {
        return null;
    }

    @Override
    public List<String> getStopSequences() {
        return null;
    }

    @Override
    public Double getTemperature() {
        return 0.3;
    }

    @Override
    public Integer getTopK() {
        return null;
    }

    @Override
    public Double getTopP() {
        return null;
    }

    @Override
    public ChatOptions copy() {
        return new MockChatOptions();
    }
}
