package kr.co.mz.agenticai.core.common.spi;

import java.util.List;
import java.util.Map;
import kr.co.mz.agenticai.core.common.spi.RetrieverRouter.Query;
import org.springframework.ai.document.Document;

/**
 * SPI for evaluating retrieval quality and deciding the next action.
 *
 * <p>Implementations inspect the query and ranked candidates, then return a
 * {@link Decision} that tells the caller whether to accept, retry, fall back,
 * or abstain. The actual branching policy (retry loop, fallback call, etc.)
 * is the caller's responsibility — this SPI only provides the verdict.
 */
public interface RetrievalEvaluator {

    /** Evaluates {@code candidates} with respect to {@code query}. */
    Decision evaluate(Query query, List<Document> candidates);

    /**
     * The evaluator's verdict for a single retrieval result set.
     *
     * @param action     what the caller should do next
     * @param score      evaluator's own confidence in [0.0, 1.0]; not the document score
     * @param reason     human-readable explanation; may be null
     * @param attributes extension map for implementation-specific metadata
     */
    record Decision(
            Action action,
            double score,
            String reason,
            Map<String, Object> attributes) {

        public Decision {
            if (score < 0.0 || score > 1.0) {
                throw new IllegalArgumentException("score must be in [0.0, 1.0], was " + score);
            }
            attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
        }

        /** Convenience factory — no reason, empty attributes. */
        public static Decision of(Action action, double score) {
            return new Decision(action, score, null, Map.of());
        }

        /** Convenience factory — with reason, empty attributes. */
        public static Decision of(Action action, double score, String reason) {
            return new Decision(action, score, reason, Map.of());
        }
    }

    /**
     * Actions the caller may take based on the evaluator's decision.
     *
     * <ul>
     *   <li>{@link #ACCEPT} — results are good enough; proceed to generation.</li>
     *   <li>{@link #RETRY}  — re-query with a transformed or expanded query.</li>
     *   <li>{@link #FALLBACK} — switch to a different retrieval strategy or corpus.</li>
     *   <li>{@link #ABSTAIN} — results are too poor; suppress generation entirely.</li>
     * </ul>
     */
    enum Action {
        ACCEPT,
        RETRY,
        FALLBACK,
        ABSTAIN
    }
}
