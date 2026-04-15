package kr.co.mz.agenticai.core.retrieval.query;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import kr.co.mz.agenticai.core.common.exception.RetrievalException;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.preretrieval.query.expansion.QueryExpander;

/**
 * Expands a query into multiple paraphrases to broaden recall. The original
 * query is included in the output when {@code includeOriginal} is {@code true}.
 */
public final class MultiQueryExpander implements QueryExpander {

    private final ChatModel chatModel;
    private final String promptTemplate;
    private final int numberOfQueries;
    private final boolean includeOriginal;

    public MultiQueryExpander(ChatModel chatModel) {
        this(chatModel, KoreanQueryPrompts.MULTI_QUERY, 3, true);
    }

    public MultiQueryExpander(
            ChatModel chatModel, String promptTemplate,
            int numberOfQueries, boolean includeOriginal) {
        this.chatModel = Objects.requireNonNull(chatModel, "chatModel");
        this.promptTemplate = Objects.requireNonNull(promptTemplate, "promptTemplate");
        if (numberOfQueries < 1) {
            throw new IllegalArgumentException("numberOfQueries must be >= 1");
        }
        if (!promptTemplate.contains("{query}") || !promptTemplate.contains("{number}")) {
            throw new IllegalArgumentException(
                    "promptTemplate must contain both {query} and {number} placeholders");
        }
        this.numberOfQueries = numberOfQueries;
        this.includeOriginal = includeOriginal;
    }

    @Override
    public List<Query> expand(Query query) {
        if (query == null || query.text() == null || query.text().isBlank()) {
            return List.of();
        }
        String rendered = promptTemplate
                .replace("{query}", query.text())
                .replace("{number}", String.valueOf(numberOfQueries));

        String raw;
        try {
            ChatResponse response = chatModel.call(new Prompt(rendered));
            raw = response.getResult().getOutput().getText();
        } catch (RuntimeException e) {
            throw new RetrievalException("Multi-query expansion chat call failed", e);
        }
        if (raw == null || raw.isBlank()) {
            return includeOriginal ? List.of(query) : List.of();
        }

        List<Query> out = new ArrayList<>();
        if (includeOriginal) {
            out.add(query);
        }
        Arrays.stream(raw.split("\\R"))
                .map(String::trim)
                .filter(line -> !line.isBlank())
                .map(MultiQueryExpander::stripBulletPrefix)
                .limit(numberOfQueries)
                .forEach(text -> out.add(query.mutate().text(text).build()));
        return out;
    }

    /** Strip common leading markers like "1. ", "- ", "• ". */
    private static String stripBulletPrefix(String line) {
        return line.replaceFirst("^\\s*(?:[0-9]+[.)]|[-*•])\\s+", "").trim();
    }
}
