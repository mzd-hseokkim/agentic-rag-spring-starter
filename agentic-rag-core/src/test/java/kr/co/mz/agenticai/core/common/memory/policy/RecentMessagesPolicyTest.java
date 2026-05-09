package kr.co.mz.agenticai.core.common.memory.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.stream.IntStream;
import kr.co.mz.agenticai.core.common.memory.MemoryRecord;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.MessageType;

class RecentMessagesPolicyTest {

    private static List<MemoryRecord> history(int count) {
        return IntStream.range(0, count)
                .mapToObj(i -> (i % 2 == 0)
                        ? MemoryRecord.user("u" + i)
                        : MemoryRecord.assistant("a" + i))
                .toList();
    }

    @Test
    void returnsEmptyForNullHistory() {
        var policy = new RecentMessagesPolicy(5);
        assertThat(policy.apply(null, "q", "sys")).isEmpty();
    }

    @Test
    void returnsEmptyForEmptyHistory() {
        var policy = new RecentMessagesPolicy(5);
        assertThat(policy.apply(List.of(), "q", "sys")).isEmpty();
    }

    @Test
    void returnsEmptyWhenWindowSizeIsZero() {
        var policy = new RecentMessagesPolicy(0);
        assertThat(policy.apply(history(4), "q", "sys")).isEmpty();
    }

    @Test
    void rejectsNegativeWindowSize() {
        assertThatThrownBy(() -> new RecentMessagesPolicy(-1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void keepsAllMessagesWhenHistoryFitsInWindow() {
        var policy = new RecentMessagesPolicy(10);
        var result = policy.apply(history(4), "q", "sys");
        assertThat(result).hasSize(4);
    }

    @Test
    void trimsOldestWhenHistoryExceedsWindow() {
        var policy = new RecentMessagesPolicy(3);
        List<MemoryRecord> hist = history(6);
        var result = policy.apply(hist, "q", "sys");
        assertThat(result).hasSize(3);
        // last 3 entries: u4, a5, u6? Actually indices 3,4,5 → a3, u4, a5
        assertThat(result.get(0).getText()).isEqualTo("a3");
        assertThat(result.get(1).getText()).isEqualTo("u4");
        assertThat(result.get(2).getText()).isEqualTo("a5");
    }

    @Test
    void toolMessagesAreFilteredOut() {
        var hist = List.of(
                MemoryRecord.user("q"),
                new MemoryRecord(MemoryRecord.Role.TOOL, "tool-output", java.time.Instant.now()),
                MemoryRecord.assistant("a")
        );
        var policy = new RecentMessagesPolicy(10);
        var result = policy.apply(hist, "q", "sys");
        assertThat(result).hasSize(2);
        assertThat(result.stream().map(m -> m.getMessageType()).toList())
                .containsExactly(MessageType.USER, MessageType.ASSISTANT);
    }

    @Test
    void defaultWindowSizeIs10() {
        assertThat(new RecentMessagesPolicy().getWindowSize()).isEqualTo(10);
    }
}
