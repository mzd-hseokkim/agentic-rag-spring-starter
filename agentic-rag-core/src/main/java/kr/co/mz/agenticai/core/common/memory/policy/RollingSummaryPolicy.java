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
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;

/**
 * When total token count exceeds {@code tokenBudget}, compresses the oldest
 * {@code summarizeFraction} of the history into a single summary string that
 * is appended to the system prompt. The most recent {@code recentTurns} turns
 * are always kept verbatim.
 *
 * <p>The summary is not cached between calls — each {@link #apply} invocation
 * re-evaluates token budget. Summary LLM calls are synchronous; async
 * pre-summarization is a Phase 2 concern.
 */
public final class RollingSummaryPolicy implements MemoryPolicy {

    private static final double SAFETY_MARGIN = 0.85;
    private static final String SUMMARY_PROMPT_TEMPLATE = """
            You are a conversation summarizer. Summarize the following conversation exchanges \
            concisely in the same language as the conversation. Preserve key facts, decisions, \
            and context. Output only the summary text, no preamble.

            %s""";
    private static final String SUMMARY_SEPARATOR = "\n\n[Conversation summary so far]\n";

    public static final int DEFAULT_TOKEN_BUDGET = 4000;
    public static final double DEFAULT_SUMMARIZE_FRACTION = 0.5;
    public static final int DEFAULT_RECENT_TURNS = 6;

    private final ChatModel chatModel;
    private final TokenCounter tokenCounter;
    private final int tokenBudget;
    private final double summarizeFraction;
    private final int recentTurns;

    public RollingSummaryPolicy(ChatModel chatModel, TokenCounter tokenCounter) {
        this(chatModel, tokenCounter, DEFAULT_TOKEN_BUDGET, DEFAULT_SUMMARIZE_FRACTION, DEFAULT_RECENT_TURNS);
    }

    public RollingSummaryPolicy(
            ChatModel chatModel,
            TokenCounter tokenCounter,
            int tokenBudget,
            double summarizeFraction,
            int recentTurns) {
        this.chatModel = Objects.requireNonNull(chatModel, "chatModel");
        this.tokenCounter = Objects.requireNonNull(tokenCounter, "tokenCounter");
        if (tokenBudget <= 0) {
            throw new IllegalArgumentException("tokenBudget must be > 0");
        }
        if (summarizeFraction <= 0 || summarizeFraction >= 1) {
            throw new IllegalArgumentException("summarizeFraction must be in (0, 1)");
        }
        if (recentTurns < 0) {
            throw new IllegalArgumentException("recentTurns must be >= 0");
        }
        this.tokenBudget = tokenBudget;
        this.summarizeFraction = summarizeFraction;
        this.recentTurns = recentTurns;
    }

    @Override
    public List<Message> apply(List<MemoryRecord> history, String query, String systemPrompt) {
        if (history == null || history.isEmpty()) {
            return List.of();
        }

        int effectiveBudget = (int) (tokenBudget * SAFETY_MARGIN);
        int totalTokens = history.stream().mapToInt(r -> tokenCounter.count(r.content())).sum();

        if (totalTokens <= effectiveBudget) {
            return toMessages(history);
        }

        // Split: compress a prefix, keep the rest verbatim.
        // summarizeFraction caps how many messages may be compressed in one pass.
        // recentTurns provides a secondary verbatim floor — if recentTurns leaves fewer
        // messages to compress than the fraction allows, recentTurns wins (Math.min).
        // e.g. size=20, recentTurns=4 → candidate=16; summarizeFraction=0.5 → cap=10 → compress 10.
        // e.g. size=20, recentTurns=14 → candidate=6; fraction=0.5 → cap=10 → compress 6 (recentTurns wins).
        int splitAt = Math.max(0, history.size() - recentTurns);
        int compressCount = (int) Math.ceil(history.size() * summarizeFraction);
        splitAt = Math.min(splitAt, compressCount);
        if (splitAt == 0) {
            // Nothing to compress — return all as-is (budget still exceeded but nothing we can do).
            return toMessages(history);
        }

        List<MemoryRecord> toSummarize = history.subList(0, splitAt);
        List<MemoryRecord> verbatim = history.subList(splitAt, history.size());

        String summaryText = summarize(toSummarize);
        String augmentedSystem = (systemPrompt == null || systemPrompt.isBlank())
                ? SUMMARY_SEPARATOR.stripLeading() + summaryText
                : systemPrompt + SUMMARY_SEPARATOR + summaryText;

        List<Message> result = new ArrayList<>();
        result.add(new SystemMessage(augmentedSystem));
        result.addAll(toMessages(verbatim));
        return result;
    }

    private String summarize(List<MemoryRecord> records) {
        StringBuilder sb = new StringBuilder();
        for (MemoryRecord r : records) {
            sb.append(r.role().name()).append(": ").append(r.content()).append('\n');
        }
        String prompt = String.format(SUMMARY_PROMPT_TEMPLATE, sb);
        String result = chatModel.call(new Prompt(new UserMessage(prompt)))
                .getResult().getOutput().getText();
        return result == null ? "" : result.strip();
    }

    private static List<Message> toMessages(List<MemoryRecord> records) {
        List<Message> out = new ArrayList<>(records.size());
        for (MemoryRecord rec : records) {
            Message m = toMessage(rec);
            if (m != null) {
                out.add(m);
            }
        }
        return out;
    }

    private static Message toMessage(MemoryRecord rec) {
        return switch (rec.role()) {
            case USER -> new UserMessage(rec.content());
            case ASSISTANT -> new AssistantMessage(rec.content());
            case SYSTEM -> new SystemMessage(rec.content());
            case TOOL -> null;
        };
    }

    public int getTokenBudget() { return tokenBudget; }
    public int getRecentTurns() { return recentTurns; }
    public double getSummarizeFraction() { return summarizeFraction; }
}
