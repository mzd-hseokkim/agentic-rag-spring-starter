package kr.co.mz.agenticai.core.retrieval.evaluate;

import java.util.List;
import kr.co.mz.agenticai.core.common.spi.RetrievalEvaluator;
import kr.co.mz.agenticai.core.common.spi.RetrieverRouter.Query;
import kr.co.mz.agenticai.core.retrieval.RetrievalMetadata;
import org.springframework.ai.document.Document;

/**
 * {@link RetrievalEvaluator} that rejects results whose top-1 fused score
 * falls below a configurable threshold.
 *
 * <p>Disabled by default ({@code agentic-rag.retrieval.evaluator.enabled=false}).
 * Enable via properties and provide this bean, or replace it with a custom one.
 *
 * <p>Score used: {@link RetrievalMetadata#FUSED_SCORE} metadata key on the
 * first (highest-ranked) document. If the key is absent, the document is
 * treated as having score 0.0.
 */
public final class ScoreThresholdEvaluator implements RetrievalEvaluator {

    private final double minScore;

    /**
     * @param minScore minimum acceptable fused score in [0.0, 1.0]; candidates
     *                 whose top-1 score is below this value trigger a RETRY decision
     */
    public ScoreThresholdEvaluator(double minScore) {
        if (minScore < 0.0 || minScore > 1.0) {
            throw new IllegalArgumentException("minScore must be in [0.0, 1.0], was " + minScore);
        }
        this.minScore = minScore;
    }

    @Override
    public Decision evaluate(Query query, List<Document> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return Decision.of(Action.RETRY, 0.0, "no candidates returned");
        }

        double top1Score = extractFusedScore(candidates.get(0));
        if (top1Score >= minScore) {
            return Decision.of(Action.ACCEPT, top1Score);
        }
        return Decision.of(
                Action.RETRY,
                top1Score,
                "top1 score " + top1Score + " < threshold " + minScore);
    }

    private static double extractFusedScore(Document document) {
        Object raw = document.getMetadata().get(RetrievalMetadata.FUSED_SCORE);
        if (raw instanceof Number n) {
            return n.doubleValue();
        }
        return 0.0;
    }
}
