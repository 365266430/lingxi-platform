package com.lingxi.agent.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingxi.common.exception.BizException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentDefinitionServiceTest {

    @Test
    void fallsBackToBuiltInDefinitionsWhenDatabaseIsEmpty() {
        AgentDefinitionMapper mapper = mock(AgentDefinitionMapper.class);
        when(mapper.selectList(any())).thenReturn(List.of());
        when(mapper.selectCount(null)).thenReturn(0L);
        AgentDefinitionService service = new AgentDefinitionService(mapper, new ObjectMapper());

        assertThat(service.listEnabled()).extracting(AgentDefinition::getCode)
                .containsExactly("supervisor", "knowledge_qa", "ops_diagnosis", "data_analysis", "report");
    }

    @Test
    void disabledConfiguredAgentCannotRun() {
        AgentDefinitionMapper mapper = mock(AgentDefinitionMapper.class);
        AgentDefinition disabled = new AgentDefinition();
        disabled.setCode("support");
        disabled.setName("客服助手");
        disabled.setEnabled(0);
        when(mapper.selectOne(any())).thenReturn(disabled);
        AgentDefinitionService service = new AgentDefinitionService(mapper, new ObjectMapper());

        assertThatThrownBy(() -> service.requireRunnable("support"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("已停用");
    }

    @Test
    void invalidToolConfigurationIsRejected() {
        AgentDefinitionMapper mapper = mock(AgentDefinitionMapper.class);
        AgentDefinitionService service = new AgentDefinitionService(mapper, new ObjectMapper());
        AgentDefinition input = new AgentDefinition();
        input.setCode("support");
        input.setName("客服助手");
        input.setSystemPrompt("回答客服问题");
        input.setToolsJson("[\"delete_everything\"]");

        assertThatThrownBy(() -> service.create(input, 1L))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("不支持的工具");
    }
}
