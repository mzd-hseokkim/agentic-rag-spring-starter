package kr.co.mz.agenticai.core.common.event;

import java.time.Instant;

/** Marker interface for events emitted during document ingestion. */
public interface IngestionEvent extends RagEvent {

    record DocumentRead(
            String documentId, String source, int contentLength,
            Instant timestamp, String correlationId)
            implements IngestionEvent {}

    record DocumentChunked(
            String documentId, int chunkCount, String strategy,
            Instant timestamp, String correlationId)
            implements IngestionEvent {}

    record ChunkEmbedded(
            String documentId, String chunkId, int dimensions,
            Instant timestamp, String correlationId)
            implements IngestionEvent {}

    record IngestionCompleted(
            String documentId, int chunkCount,
            Instant timestamp, String correlationId)
            implements IngestionEvent {}

    record IngestionFailed(
            String documentId, String errorMessage, String errorType,
            Instant timestamp, String correlationId)
            implements IngestionEvent {}
}
