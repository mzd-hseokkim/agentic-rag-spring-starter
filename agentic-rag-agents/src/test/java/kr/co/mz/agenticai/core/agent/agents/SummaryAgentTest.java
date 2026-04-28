package kr.co.mz.agenticai.core.agent.agents;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.function.Function;
import kr.co.mz.agenticai.core.common.AgentContext;
import kr.co.mz.agenticai.core.common.RagRequest;
import kr.co.mz.agenticai.core.common.memory.InMemoryMemoryStore;
import kr.co.mz.agenticai.core.common.memory.MemoryRecord;
import kr.co.mz.agenticai.core.common.spi.MemoryStore;
import kr.co.mz.agenticai.core.common.spi.ToolProvider;
import kr.co.mz.agenticai.core.common.tool.EmptyToolProvider;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;

class SummaryAgentTest {

    private static ChatResponse respondWith(String text) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
    }

    private static AgentContext contextWith(String query, String sessionId) {
        RagRequest req = RagRequest.builder().query(query).sessionId(sessionId).build();
        return new AgentContext(req, "corr-1");
    }

    @Test
    void runsWithoutToolsOrMemoryByDefault() {
        ChatModel chat = mock(ChatModel.class);
        when(chat.call(any(Prompt.class))).thenReturn(respondWith("응답"));

        var agent = new SummaryAgent(chat);
        AgentContext ctx = contextWith("질문", null);
        agent.execute(ctx);

        ArgumentCaptor<Prompt> captor = ArgumentCaptor.forClass(Prompt.class);
        verify(chat).call(captor.capture());
        Prompt prompt = captor.getValue();
        assertThat(prompt.getInstructions()).extracting(Message::getMessageType)
                .containsExactly(MessageType.SYSTEM, MessageType.USER);
        assertThat(prompt.getOptions()).isNull();
        assertThat(ctx.answer()).isEqualTo("응답");
    }

    @Test
    void attachesToolCallingOptionsWhenProviderYieldsTools() {
        ChatModel chat = mock(ChatModel.class);
        when(chat.call(any(Prompt.class))).thenReturn(respondWith("툴 응답"));
        ToolCallback callback = mock(ToolCallback.class);
        ToolProvider tools = () -> List.of(callback);

        var agent = new SummaryAgent(chat, tools, null);
        agent.execute(contextWith("질문", null));

        ArgumentCaptor<Prompt> captor = ArgumentCaptor.forClass(Prompt.class);
        verify(chat).call(captor.capture());
        var options = captor.getValue().getOptions();
        assertThat(options).isInstanceOf(ToolCallingChatOptions.class);
        assertThat(((ToolCallingChatOptions) options).getToolCallbacks()).containsExactly(callback);
    }

    @Test
    void skipsToolOptionsWhenProviderReturnsEmpty() {
        ChatModel chat = mock(ChatModel.class);
        when(chat.call(any(Prompt.class))).thenReturn(respondWith("응답"));

        var agent = new SummaryAgent(chat, new EmptyToolProvider(), null);
        agent.execute(contextWith("질문", null));

        ArgumentCaptor<Prompt> captor = ArgumentCaptor.forClass(Prompt.class);
        verify(chat).call(captor.capture());
        assertThat(captor.getValue().getOptions()).isNull();
    }

    @Test
    void prefixesHistoryWhenSessionAndMemoryPresent() {
        ChatModel chat = mock(ChatModel.class);
        when(chat.call(any(Prompt.class))).thenReturn(respondWith("새 답변"));
        MemoryStore store = new InMemoryMemoryStore();
        store.append("s1", MemoryRecord.user("이전 질문"));
        store.append("s1", MemoryRecord.assistant("이전 답"));

        var agent = new SummaryAgent(chat, null, store);
        agent.execute(contextWith("새 질문", "s1"));

        ArgumentCaptor<Prompt> captor = ArgumentCaptor.forClass(Prompt.class);
        verify(chat).call(captor.capture());
        List<MessageType> types = captor.getValue().getInstructions().stream()
                .map(Message::getMessageType).toList();
        assertThat(types).containsExactly(
                MessageType.SYSTEM,
                MessageType.USER,
                MessageType.ASSISTANT,
                MessageType.USER);

        assertThat(store.history("s1", 10))
                .extracting(MemoryRecord::content)
                .containsExactly("이전 질문", "이전 답", "새 질문", "새 답변");
    }

    @Test
    void skipsMemoryWhenSessionIdMissing() {
        ChatModel chat = mock(ChatModel.class);
        when(chat.call(any(Prompt.class))).thenReturn(respondWith("응답"));
        MemoryStore store = mock(MemoryStore.class);

        var agent = new SummaryAgent(chat, null, store);
        agent.execute(contextWith("질문", null));

        verifyNoInteractions(store);
    }

    @Test
    void nullAnswerIsNormalisedToEmpty() {
        ChatModel chat = mock(ChatModel.class);
        AssistantMessage msg = mock(AssistantMessage.class);
        when(msg.getText()).thenReturn(null);
        when(chat.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of(new Generation(msg))));

        var agent = new SummaryAgent(chat);
        AgentContext ctx = contextWith("질문", null);
        agent.execute(ctx);

        assertThat(ctx.answer()).isEqualTo("");
    }
}
