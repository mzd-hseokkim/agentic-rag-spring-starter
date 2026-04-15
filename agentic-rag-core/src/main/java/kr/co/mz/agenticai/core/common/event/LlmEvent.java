package kr.co.mz.agenticai.core.common.event;

import java.time.Instant;

/** Marker interface for events emitted during LLM interaction. */
public interface LlmEvent extends RagEvent {

    record LlmRequested(
            String provider, String model, int promptLength,
            Instant timestamp, String correlationId)
            implements LlmEvent {}

    record LlmResponded(
            String provider, String model,
            long promptTokens, long completionTokens, long elapsedMillis,
            Instant timestamp, String correlationId)
            implements LlmEvent {}

    /**
     * Emitted for each streamed token (or text delta). This event fires at
     * high frequency; publisher implementations must be non-blocking.
     */
    record LlmTokenStreamed(
            String provider, String model, String token,
            Instant timestamp, String correlationId)
            implements LlmEvent {}
}
