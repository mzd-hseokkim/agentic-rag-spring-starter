package kr.co.mz.agenticai.core.common.memory.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.stream.IntStream;
import kr.co.mz.agenticai.core.common.memory.MemoryRecord;
import kr.co.mz.agenticai.core.common.spi.TokenCounter;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

class RollingSummaryPolicyTest {

    private static final TokenCounter CHAR_COUNTER = text -> text == null ? 0 : text.length();

    private static ChatModel chatModelReturning(String summary) {
        ChatModel model = mock(ChatModel.class);
        when(model.call(any(Prompt.class)))
                .thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage(summary)))));
        return model;
    }

    private static List<MemoryRecord> turns(int count) {
        return IntStream.range(0, count)
                .mapToObj(i -> i % 2 == 0
                        ? MemoryRecord.user("userMsg" + i)
                        : MemoryRecord.assistant("assistantMsg" + i))
                .toList();
    }

    @Test
    void returnsEmptyForNullOrEmptyHistory() {
        var policy = new RollingSummaryPolicy(chatModelReturning("x"), CHAR_COUNTER);
        assertThat(policy.apply(null, "q", "sys")).isEmpty();
        assertThat(policy.apply(List.of(), "q", "sys")).isEmpty();
    }

    @Test
    void rejectsInvalidConstructorArgs() {
        ChatModel model = chatModelReturning("x");
        assertThatThrownBy(() -> new RollingSummaryPolicy(null, CHAR_COUNTER))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new RollingSummaryPolicy(model, null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new RollingSummaryPolicy(model, CHAR_COUNTER, 0, 0.5, 4))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RollingSummaryPolicy(model, CHAR_COUNTER, 100, 0.0, 4))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RollingSummaryPolicy(model, CHAR_COUNTER, 100, 1.0, 4))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RollingSummaryPolicy(model, CHAR_COUNTER, 100, 0.5, -1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void noSummarizationWhenBudgetNotExceeded() {
        // budget=10000, safety=0.85 → effective=8500; all messages fit
        ChatModel model = mock(ChatModel.class);
        var policy = new RollingSummaryPolicy(model, CHAR_COUNTER, 10000, 0.5, 4);
        var result = policy.apply(turns(6), "q", "sys");
        verify(model, never()).call(any(Prompt.class));
        // no SystemMessage injected — just the 6 raw messages (TOOL filtered: none here)
        assertThat(result).hasSize(6);
        assertThat(result.get(0).getMessageType()).isNotEqualTo(MessageType.SYSTEM);
    }

    @Test
    void summarizesOldestPortionWhenBudgetExceeded() {
        // budget=10, effective=8; 8 turns of "userMsgN"/"assistantMsgN" (8-9 chars each) → easily exceed 8
        ChatModel model = chatModelReturning("요약된 내용");
        // recentTurns=2, summarizeFraction=0.5
        var policy = new RollingSummaryPolicy(model, CHAR_COUNTER, 10, 0.5, 2);
        List<MemoryRecord> hist = turns(6); // 6 messages
        var result = policy.apply(hist, "q", "sys");

        // Should have called LLM for summary
        verify(model).call(any(Prompt.class));

        // First message should be a SystemMessage with summary appended
        assertThat(result.get(0).getMessageType()).isEqualTo(MessageType.SYSTEM);
        assertThat(result.get(0).getText()).contains("요약된 내용");
    }

    @Test
    void summaryAppendsToExistingSystemPrompt() {
        ChatModel model = chatModelReturning("summary text");
        var policy = new RollingSummaryPolicy(model, CHAR_COUNTER, 10, 0.5, 2);
        var result = policy.apply(turns(6), "q", "My system prompt");
        assertThat(result.get(0).getText())
                .startsWith("My system prompt")
                .contains("summary text");
    }

    @Test
    void summaryUsedWhenSystemPromptIsBlank() {
        ChatModel model = chatModelReturning("compact summary");
        var policy = new RollingSummaryPolicy(model, CHAR_COUNTER, 10, 0.5, 2);
        var result = policy.apply(turns(6), "q", "");
        assertThat(result.get(0).getMessageType()).isEqualTo(MessageType.SYSTEM);
        assertThat(result.get(0).getText()).contains("compact summary");
    }

    @Test
    void summarizeFractionCapsPrefixSizeWhenItIsLowerThanRecentTurnsCandidate() {
        ChatModel model = chatModelReturning("요약");
        // size=6, recentTurns=2 → candidate splitAt = 6-2 = 4
        // summarizeFraction=0.5 → compressCount = ceil(6*0.5) = 3
        // splitAt = min(4, 3) = 3  →  summarize [0,1,2], verbatim [3,4,5]
        // fraction is the binding constraint here; 3 messages stay verbatim (> recentTurns=2)
        var policy = new RollingSummaryPolicy(model, CHAR_COUNTER, 10, 0.5, 2);
        List<MemoryRecord> hist = turns(6); // indices 0..5
        var result = policy.apply(hist, "q", "sys");

        // result: [SystemMessage(summary), assistantMsg3, userMsg4, assistantMsg5] = 4 items
        assertThat(result).hasSize(4);
        assertThat(result.get(0).getMessageType()).isEqualTo(MessageType.SYSTEM);
        assertThat(result.get(1).getMessageType()).isEqualTo(MessageType.ASSISTANT);
        assertThat(result.get(2).getMessageType()).isEqualTo(MessageType.USER);
        assertThat(result.get(3).getMessageType()).isEqualTo(MessageType.ASSISTANT);
    }

    @Test
    void e2eLongConversationFitsInBudgetAfterSummary() {
        // 20 messages, each 50 chars. budget=200, effective=170; total=1000 >> 170 → summarize.
        // splitAt = min(20 - recentTurns(4), ceil(20 * 0.5)) = min(16, 10) = 10
        // → summarize [0..9], verbatim [10..19] = 10 messages
        // result = SystemMessage(summary) + 10 verbatim = 11 items
        TokenCounter counter = text -> text == null ? 0 : text.length();
        ChatModel model = chatModelReturning("S"); // 1-char summary

        var policy = new RollingSummaryPolicy(model, counter, 200, 0.5, 4);

        List<MemoryRecord> longHistory = IntStream.range(0, 20)
                .mapToObj(i -> i % 2 == 0
                        ? MemoryRecord.user("U".repeat(50))
                        : MemoryRecord.assistant("A".repeat(50)))
                .toList();

        var result = policy.apply(longHistory, "query", "system");

        // Summarization triggered
        verify(model).call(any(Prompt.class));
        // System + 10 verbatim = 11 messages
        assertThat(result).hasSize(11);
        assertThat(result.get(0).getMessageType()).isEqualTo(MessageType.SYSTEM);
        assertThat(result.get(0).getText()).contains("S"); // summary appended
    }
}
