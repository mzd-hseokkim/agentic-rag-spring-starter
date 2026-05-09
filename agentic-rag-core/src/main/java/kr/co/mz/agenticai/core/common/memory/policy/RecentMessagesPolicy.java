package kr.co.mz.agenticai.core.common.memory.policy;

import java.util.ArrayList;
import java.util.List;
import kr.co.mz.agenticai.core.common.memory.MemoryRecord;
import kr.co.mz.agenticai.core.common.spi.MemoryPolicy;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;

/**
 * Sliding-window policy that keeps the most recent {@code windowSize} messages.
 * Equivalent to the legacy {@code historyLimit} behaviour; registered as the
 * default {@link MemoryPolicy} bean.
 */
public final class RecentMessagesPolicy implements MemoryPolicy {

    public static final int DEFAULT_WINDOW_SIZE = 10;

    private final int windowSize;

    public RecentMessagesPolicy() {
        this(DEFAULT_WINDOW_SIZE);
    }

    public RecentMessagesPolicy(int windowSize) {
        if (windowSize < 0) {
            throw new IllegalArgumentException("windowSize must be >= 0");
        }
        this.windowSize = windowSize;
    }

    @Override
    public List<Message> apply(List<MemoryRecord> history, String query, String systemPrompt) {
        if (windowSize == 0 || history == null || history.isEmpty()) {
            return List.of();
        }
        int from = Math.max(0, history.size() - windowSize);
        List<MemoryRecord> window = history.subList(from, history.size());
        List<Message> result = new ArrayList<>(window.size());
        for (MemoryRecord rec : window) {
            Message m = toMessage(rec);
            if (m != null) {
                result.add(m);
            }
        }
        return result;
    }

    private static Message toMessage(MemoryRecord rec) {
        return switch (rec.role()) {
            case USER -> new UserMessage(rec.content());
            case ASSISTANT -> new AssistantMessage(rec.content());
            case SYSTEM -> new SystemMessage(rec.content());
            case TOOL -> null;
        };
    }

    public int getWindowSize() {
        return windowSize;
    }
}
