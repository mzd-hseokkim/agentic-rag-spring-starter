package kr.co.mz.agenticai.core.retrieval.query;

import java.util.Objects;
import kr.co.mz.agenticai.core.common.exception.RetrievalException;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.preretrieval.query.transformation.QueryTransformer;

/**
 * Hypothetical Document Embeddings (HyDE) transformer.
 *
 * <p>Given a user query, asks the LLM to draft a short hypothetical answer
 * and substitutes the query text with that draft. The resulting text is used
 * as the search query against the vector store — dense-retrieval quality
 * often improves because the hypothetical answer matches relevant passages
 * more directly than the original question.
 */
public final class HydeQueryTransformer implements QueryTransformer {

    private final ChatModel chatModel;
    private final String promptTemplate;

    public HydeQueryTransformer(ChatModel chatModel) {
        this(chatModel, KoreanQueryPrompts.HYDE);
    }

    public HydeQueryTransformer(ChatModel chatModel, String promptTemplate) {
        this.chatModel = Objects.requireNonNull(chatModel, "chatModel");
        this.promptTemplate = Objects.requireNonNull(promptTemplate, "promptTemplate");
        if (!promptTemplate.contains("{query}")) {
            throw new IllegalArgumentException("promptTemplate must contain the {query} placeholder");
        }
    }

    @Override
    public Query transform(Query query) {
        if (query == null) {
            return null;
        }
        String rendered = promptTemplate.replace("{query}", query.text());
        String hypothetical;
        try {
            ChatResponse response = chatModel.call(new Prompt(rendered));
            hypothetical = response.getResult().getOutput().getText();
        } catch (RuntimeException e) {
            throw new RetrievalException("HyDE chat call failed", e);
        }
        if (hypothetical == null || hypothetical.isBlank()) {
            return query;
        }
        return query.mutate().text(hypothetical.trim()).build();
    }
}
