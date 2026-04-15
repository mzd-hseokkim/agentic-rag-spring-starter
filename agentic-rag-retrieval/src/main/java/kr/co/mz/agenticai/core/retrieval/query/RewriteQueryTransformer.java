package kr.co.mz.agenticai.core.retrieval.query;

import java.util.Objects;
import kr.co.mz.agenticai.core.common.exception.RetrievalException;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.preretrieval.query.transformation.QueryTransformer;

/**
 * Rewrites a user query into a more specific, keyword-rich reformulation
 * suitable for dense or lexical retrieval. Uses the Korean prompt by
 * default; override {@code promptTemplate} for other languages.
 */
public final class RewriteQueryTransformer implements QueryTransformer {

    private final ChatModel chatModel;
    private final String promptTemplate;

    public RewriteQueryTransformer(ChatModel chatModel) {
        this(chatModel, KoreanQueryPrompts.REWRITE);
    }

    public RewriteQueryTransformer(ChatModel chatModel, String promptTemplate) {
        this.chatModel = Objects.requireNonNull(chatModel, "chatModel");
        this.promptTemplate = Objects.requireNonNull(promptTemplate, "promptTemplate");
        if (!promptTemplate.contains("{query}")) {
            throw new IllegalArgumentException("promptTemplate must contain the {query} placeholder");
        }
    }

    @Override
    public Query transform(Query query) {
        if (query == null || query.text() == null || query.text().isBlank()) {
            return query;
        }
        String rendered = promptTemplate.replace("{query}", query.text());
        String rewritten;
        try {
            ChatResponse response = chatModel.call(new Prompt(rendered));
            rewritten = response.getResult().getOutput().getText();
        } catch (RuntimeException e) {
            throw new RetrievalException("Rewrite chat call failed", e);
        }
        if (rewritten == null || rewritten.isBlank()) {
            return query;
        }
        return query.mutate().text(rewritten.trim()).build();
    }
}
