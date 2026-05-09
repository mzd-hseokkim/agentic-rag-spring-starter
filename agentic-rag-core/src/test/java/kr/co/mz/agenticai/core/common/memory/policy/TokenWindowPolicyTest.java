package kr.co.mz.agenticai.core.common.memory.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import kr.co.mz.agenticai.core.common.memory.MemoryRecord;
import kr.co.mz.agenticai.core.common.spi.TokenCounter;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.MessageType;

class TokenWindowPolicyTest {

    /** Simple counter: 1 token per character. */
    private static final TokenCounter CHAR_COUNTER = text -> text == null ? 0 : text.length();

    @Test
    void returnsEmptyForNullOrEmptyHistory() {
        var policy = new TokenWindowPolicy(1000, CHAR_COUNTER);
        assertThat(policy.apply(null, "q", "sys")).isEmpty();
        assertThat(policy.apply(List.of(), "q", "sys")).isEmpty();
    }

    @Test
    void rejectsNonPositiveBudget() {
        assertThatThrownBy(() -> new TokenWindowPolicy(0, CHAR_COUNTER))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TokenWindowPolicy(-1, CHAR_COUNTER))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNullTokenCounter() {
        assertThatThrownBy(() -> new TokenWindowPolicy(100, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void keepsAllMessagesWhenTotalCostWithinBudget() {
        // budget=1000, safety=0.85 → effective=850
        // each message = 2 chars = 2 tokens, 3 messages = 6 tokens → all fit
        var hist = List.of(
                MemoryRecord.user("ab"),
                MemoryRecord.assistant("cd"),
                MemoryRecord.user("ef")
        );
        var policy = new TokenWindowPolicy(1000, CHAR_COUNTER);
        assertThat(policy.apply(hist, "q", "sys")).hasSize(3);
    }

    @Test
    void dropsOldestMessagesWhenBudgetExceeded() {
        // budget=5, safety=0.85 → effective=4 tokens
        // messages: "abc"(3), "de"(2), "fg"(2) — total=7
        // newest-to-oldest: "fg"(2)=2<=4, "de"(2)=4<=4, "abc"(3) → 4+3=7 > 4 → stop
        // kept: "de","fg"
        var hist = List.of(
                MemoryRecord.user("abc"),
                MemoryRecord.assistant("de"),
                MemoryRecord.user("fg")
        );
        var policy = new TokenWindowPolicy(5, CHAR_COUNTER);
        var result = policy.apply(hist, "q", "sys");
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getText()).isEqualTo("de");
        assertThat(result.get(1).getText()).isEqualTo("fg");
    }

    @Test
    void toolMessagesAreFilteredOut() {
        var hist = List.of(
                MemoryRecord.user("q"),
                new MemoryRecord(MemoryRecord.Role.TOOL, "tool", java.time.Instant.now()),
                MemoryRecord.assistant("a")
        );
        var policy = new TokenWindowPolicy(1000, CHAR_COUNTER);
        var result = policy.apply(hist, "q", "sys");
        assertThat(result.stream().map(m -> m.getMessageType()).toList())
                .containsExactly(MessageType.USER, MessageType.ASSISTANT);
    }

    @Test
    void defaultBudgetIs2000() {
        assertThat(new TokenWindowPolicy(CHAR_COUNTER).getTokenBudget()).isEqualTo(2000);
    }
}
