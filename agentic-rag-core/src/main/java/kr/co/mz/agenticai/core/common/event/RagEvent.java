package kr.co.mz.agenticai.core.common.event;

import java.time.Instant;

/**
 * Base type for all events emitted by the Agentic RAG pipeline. Not sealed:
 * library users may define and publish their own event types through
 * {@link kr.co.mz.agenticai.core.common.spi.RagEventPublisher}.
 */
public interface RagEvent {

    /** When the event was produced. */
    Instant timestamp();

    /**
     * Correlation identifier that groups events belonging to the same
     * logical operation (ingestion of one document, one RAG query, ...).
     * May be {@code null} when the publisher cannot supply one.
     */
    String correlationId();
}
