package kr.co.mz.agenticai.core.common.event;

import java.time.Instant;
import java.util.List;
import kr.co.mz.agenticai.core.common.spi.RetrievalEvaluator.Action;

/** Marker interface for events emitted during retrieval. */
public interface RetrievalEvent extends RagEvent {

    record QueryReceived(
            String query, String sessionId,
            Instant timestamp, String correlationId)
            implements RetrievalEvent {}

    record QueryTransformed(
            String originalQuery, List<String> transformedQueries, String transformer,
            Instant timestamp, String correlationId)
            implements RetrievalEvent {}

    record RetrievalCompleted(
            String query, int hitCount, String retriever, long elapsedMillis,
            Instant timestamp, String correlationId)
            implements RetrievalEvent {}

    record RerankCompleted(
            int candidateCount, int keptCount, String reranker, long elapsedMillis,
            Instant timestamp, String correlationId)
            implements RetrievalEvent {}

    /**
     * Published after a {@link kr.co.mz.agenticai.core.common.spi.RetrievalEvaluator}
     * has judged the retrieval candidates.
     *
     * @param action       the evaluator's verdict
     * @param score        evaluator confidence in [0.0, 1.0]
     * @param evaluator    simple class name of the evaluator implementation
     * @param elapsedMillis wall-clock time spent in the evaluator
     * @param timestamp    when the event was created
     * @param correlationId links to the originating query
     */
    record EvaluationCompleted(
            Action action,
            double score,
            String evaluator,
            long elapsedMillis,
            Instant timestamp,
            String correlationId)
            implements RetrievalEvent {}
}
