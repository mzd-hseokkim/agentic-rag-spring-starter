package kr.co.mz.agenticai.core.retrieval.rerank;

import java.util.Comparator;
import java.util.List;
import java.util.stream.IntStream;
import kr.co.mz.agenticai.core.common.spi.CrossEncoderScorer;
import kr.co.mz.agenticai.core.common.spi.Reranker;
import org.springframework.ai.document.Document;

/**
 * {@link Reranker} that reorders candidates using scores produced by a
 * {@link CrossEncoderScorer}. The scorer receives the query and the full
 * candidate list, and must return one score per document in the same order;
 * higher scores rank higher.
 */
public final class CrossEncoderReranker implements Reranker {

    private final CrossEncoderScorer scorer;

    public CrossEncoderReranker(CrossEncoderScorer scorer) {
        this.scorer = scorer;
    }

    @Override
    public List<Document> rerank(String query, List<Document> candidates, int topK) {
        if (candidates == null || candidates.isEmpty() || topK <= 0) {
            return List.of();
        }
        List<Float> scores = scorer.score(query, candidates);
        if (scores == null || scores.size() != candidates.size()) {
            throw new IllegalStateException(
                    "CrossEncoderScorer must return exactly one score per document (got "
                            + (scores == null ? "null" : scores.size())
                            + " for " + candidates.size() + " docs)");
        }
        return IntStream.range(0, candidates.size())
                .mapToObj(i -> new Scored(candidates.get(i), scores.get(i)))
                .sorted(Comparator.comparingDouble(Scored::score).reversed())
                .limit(topK)
                .map(Scored::doc)
                .toList();
    }

    private record Scored(Document doc, float score) {}
}
