package kr.co.mz.agenticai.core.common.memory.policy;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import kr.co.mz.agenticai.core.common.memory.MemoryRecord;
import kr.co.mz.agenticai.core.common.spi.MemoryPolicy;
import kr.co.mz.agenticai.core.common.spi.TokenCounter;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;

/**
 * Drops the oldest messages until the total estimated token count falls within
 * {@code tokenBudget}. Only the history portion is trimmed; the caller is
 * responsible for supplying the system prompt separately (e.g. via
 * {@code Prompt} constructor or {@code SystemMessage} prepended outside this
 * policy).
 *
 * <p><strong>SYSTEM history records are subject to trimming.</strong> If a
 * {@code SYSTEM}-role record appears in the history list and the budget is
 * tight, it will be dropped along with other oldest entries. Callers that
 * need SYSTEM history to survive must either pin it outside the history list
 * or use {@link RollingSummaryPolicy}, which always prepends a
 * {@code SystemMessage} carrying the (possibly summarised) context.
 *
 * <p>A safety margin of 0.85 is applied to the supplied budget to account for
 * heuristic inaccuracy.
 */
public final class TokenWindowPolicy implements MemoryPolicy {

    /** Applied to the raw budget to guard against heuristic undercount. */
    private static final double SAFETY_MARGIN = 0.85;

    public static final int DEFAULT_TOKEN_BUDGET = 2000;

    private final int tokenBudget;
    private final TokenCounter tokenCounter;

    public TokenWindowPolicy(TokenCounter tokenCounter) {
        this(DEFAULT_TOKEN_BUDGET, tokenCounter);
    }

    public TokenWindowPolicy(int tokenBudget, TokenCounter tokenCounter) {
        if (tokenBudget <= 0) {
            throw new IllegalArgumentException("tokenBudget must be > 0");
        }
        this.tokenBudget = tokenBudget;
        this.tokenCounter = Objects.requireNonNull(tokenCounter, "tokenCounter");
    }

    @Override
    public List<Message> apply(List<MemoryRecord> history, String query, String systemPrompt) {
        if (history == null || history.isEmpty()) {
            return List.of();
        }
        int effectiveBudget = (int) (tokenBudget * SAFETY_MARGIN);

        // Walk newest-to-oldest, accumulate until budget exhausted.
        int total = 0;
        int keepFrom = history.size();
        for (int i = history.size() - 1; i >= 0; i--) {
            int cost = tokenCounter.count(history.get(i).content());
            if (total + cost > effectiveBudget) {
                break;
            }
            total += cost;
            keepFrom = i;
        }

        List<MemoryRecord> window = history.subList(keepFrom, history.size());
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

    public int getTokenBudget() {
        return tokenBudget;
    }
}
