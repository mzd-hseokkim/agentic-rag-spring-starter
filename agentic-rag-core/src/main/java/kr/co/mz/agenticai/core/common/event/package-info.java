/**
 * Domain events emitted by the Agentic RAG pipeline.
 *
 * <p>{@link kr.co.mz.agenticai.core.common.event.RagEvent} is an open
 * interface so consumers may publish their own event types through
 * {@link kr.co.mz.agenticai.core.common.spi.RagEventPublisher}. Category
 * interfaces ({@code IngestionEvent}, {@code RetrievalEvent}, {@code LlmEvent})
 * are provided for convenience only and are not sealed.
 */
package kr.co.mz.agenticai.core.common.event;
