/**
 * Micrometer-based metrics: a single Spring {@code @EventListener} taps the
 * {@code RagEvent} stream produced by every pipeline stage and records
 * counters / timers / distribution summaries.
 *
 * <p>Works automatically with the default
 * {@code ApplicationEventRagEventPublisher}. When a custom publisher is
 * registered, fan events out to {@code ApplicationEventPublisher} as well
 * to keep the metrics taps active.
 */
package kr.co.mz.agenticai.core.autoconfigure.observability;
