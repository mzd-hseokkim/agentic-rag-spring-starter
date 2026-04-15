package kr.co.mz.agenticai.core.common.event;

import java.time.Instant;

/** Marker interface for events emitted during fact-checking. */
public interface FactCheckEvent extends RagEvent {

    record FactCheckPassed(
            String answerSummary, double confidence, int citationCount,
            long elapsedMillis, Instant timestamp, String correlationId)
            implements FactCheckEvent {}

    record FactCheckFailed(
            String answerSummary, double confidence, String reason,
            long elapsedMillis, Instant timestamp, String correlationId)
            implements FactCheckEvent {}
}
