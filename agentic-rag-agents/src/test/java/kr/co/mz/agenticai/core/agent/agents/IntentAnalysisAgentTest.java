package kr.co.mz.agenticai.core.agent.agents;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import kr.co.mz.agenticai.core.common.AgentContext;
import kr.co.mz.agenticai.core.common.RagRequest;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

class IntentAnalysisAgentTest {

    private static ChatResponse respondWith(String text) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
    }

    private static AgentContext contextWith(String query) {
        return new AgentContext(RagRequest.of(query), "corr-1");
    }

    @Test
    void classifiesKnownIntent() {
        ChatModel chat = mock(ChatModel.class);
        when(chat.call(any(Prompt.class))).thenReturn(respondWith("conversational"));

        var agent = new IntentAnalysisAgent(chat);
        AgentContext ctx = contextWith("안녕");
        agent.execute(ctx);

        assertThat(ctx.intent()).isEqualTo("conversational");
        assertThat(ctx.trace()).containsExactly(IntentAnalysisAgent.CANONICAL_NAME);
    }

    @Test
    void unknownResponseDefaultsToFactual() {
        ChatModel chat = mock(ChatModel.class);
        when(chat.call(any(Prompt.class))).thenReturn(respondWith("nonsense"));

        var agent = new IntentAnalysisAgent(chat);
        AgentContext ctx = contextWith("질문");
        agent.execute(ctx);

        assertThat(ctx.intent()).isEqualTo("factual");
    }

    @Test
    void normalisesUppercaseAndPunctuation() {
        ChatModel chat = mock(ChatModel.class);
        when(chat.call(any(Prompt.class))).thenReturn(respondWith("FACTUAL."));

        var agent = new IntentAnalysisAgent(chat);
        AgentContext ctx = contextWith("질문");
        agent.execute(ctx);

        assertThat(ctx.intent()).isEqualTo("factual");
    }

    @Test
    void skipsWhenIntentAlreadySet() {
        ChatModel chat = mock(ChatModel.class);
        var agent = new IntentAnalysisAgent(chat);
        AgentContext ctx = contextWith("질문");
        ctx.setIntent("conversational");

        agent.execute(ctx);

        assertThat(ctx.intent()).isEqualTo("conversational");
        assertThat(ctx.trace()).isEmpty();
        verifyNoInteractions(chat);
    }

    @Test
    void nullResponseDefaultsToFactual() {
        ChatModel chat = mock(ChatModel.class);
        Generation g = new Generation(new AssistantMessage((String) null));
        when(chat.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of(g)));

        var agent = new IntentAnalysisAgent(chat);
        AgentContext ctx = contextWith("질문");
        agent.execute(ctx);

        assertThat(ctx.intent()).isEqualTo("factual");
    }
}
