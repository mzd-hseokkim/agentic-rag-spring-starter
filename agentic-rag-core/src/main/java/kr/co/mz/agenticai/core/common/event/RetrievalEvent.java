package kr.co.mz.agenticai.core.common.event;

import java.time.Instant;
import java.util.List;

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
}
