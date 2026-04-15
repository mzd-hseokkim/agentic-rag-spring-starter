package kr.co.mz.agenticai.core.retrieval.rerank;

import java.util.List;
import kr.co.mz.agenticai.core.common.spi.Reranker;
import org.springframework.ai.document.Document;

/**
 * Default {@link Reranker}: preserves the input order and truncates to
 * {@code topK}. Replace with a cross-encoder or LLM-based reranker by
 * registering a different bean of type {@link Reranker}.
 */
public final class NoopReranker implements Reranker {

    @Override
    public List<Document> rerank(String query, List<Document> candidates, int topK) {
        if (candidates == null || candidates.isEmpty() || topK <= 0) {
            return List.of();
        }
        if (candidates.size() <= topK) {
            return List.copyOf(candidates);
        }
        return List.copyOf(candidates.subList(0, topK));
    }
}
