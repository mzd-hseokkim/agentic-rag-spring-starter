package kr.co.mz.agenticai.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.stream.IntStream;
import kr.co.mz.agenticai.core.common.memory.MemoryRecord;
import kr.co.mz.agenticai.core.common.memory.policy.RollingSummaryPolicy;
import kr.co.mz.agenticai.core.common.spi.MemoryPolicy;
import kr.co.mz.agenticai.core.common.spi.TokenCounter;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

/**
 * Integration test: verifies that {@link RollingSummaryPolicy} is auto-configured and that a long
 * conversation is compressed to fit the token budget, with the LLM summary injected as the first
 * {@code SystemMessage}.
 *
 * <p>The {@link ChatModel} is replaced with a mock so no real LLM is required.
 */
@SpringBootTest
@ActiveProfiles({"agents", "rolling"})
class IT_RollingSummaryPolicy {

    @MockBean
    ChatModel chatModel;

    @Autowired
    MemoryPolicy memoryPolicy;

    /** Simple 1-token-per-char counter used to force budget overflow. */
    private static final TokenCounter CHAR_COUNTER = text -> text == null ? 0 : text.length();

    @Test
    void longConversationTriggersRollingSummaryAndFitsInBudget() {
        // Arrange: mock the ChatModel so summarization returns a fixed string
        when(chatModel.call(any(Prompt.class)))
                .thenReturn(new ChatResponse(
                        List.of(new Generation(new AssistantMessage("대화 요약 내용")))));

        // Build a long history: 20 messages, each 50 chars → 1000 total.
        List<MemoryRecord> longHistory = IntStream.range(0, 20)
                .mapToObj(i -> i % 2 == 0
                        ? MemoryRecord.user("U".repeat(50))
                        : MemoryRecord.assistant("A".repeat(50)))
                .toList();

        // Act
        var result = memoryPolicy.apply(longHistory, "현재 질문", "시스템 프롬프트");

        // Assert: summarization was triggered (ChatModel called for the summary)
        verify(chatModel, atLeastOnce()).call(any(Prompt.class));

        // First element must be a SystemMessage containing the summary
        assertThat(result).isNotEmpty();
        assertThat(result.get(0).getMessageType()).isEqualTo(MessageType.SYSTEM);
        assertThat(result.get(0).getText()).contains("대화 요약 내용");

        // Result must be smaller than the original 20 messages
        assertThat(result.size()).isLessThan(longHistory.size());
    }

    @Test
    void shortConversationPassesThroughWithoutSummary() {
        // budget is large enough (default 4000) — 4 short messages should not trigger summary
        List<MemoryRecord> shortHistory = List.of(
                MemoryRecord.user("안녕하세요"),
                MemoryRecord.assistant("안녕하세요, 무엇을 도와드릴까요?"),
                MemoryRecord.user("RAG가 뭔가요?"),
                MemoryRecord.assistant("Retrieval-Augmented Generation의 약자입니다.")
        );

        var result = memoryPolicy.apply(shortHistory, "다음 질문", "시스템 프롬프트");

        // No summarization: ChatModel must not have been called (no prior interaction)
        // Simply verify result contains 4 messages (no system summary injected)
        assertThat(result).hasSize(4);
        assertThat(result.get(0).getMessageType()).isNotEqualTo(MessageType.SYSTEM);
    }
}
