package kr.co.mz.agenticai.core.common.event;

import java.time.Instant;

/** Marker interface for events emitted during tool invocation. */
public interface ToolInvocationEvent extends RagEvent {

    /** Maximum length of {@code argsSummary} to avoid PII/path leakage. */
    int ARGS_SUMMARY_MAX_LENGTH = 250;

    /**
     * Published immediately before a tool method is invoked.
     *
     * @param toolName      simple name of the tool being called
     * @param argsSummary   truncated string representation of arguments
     * @param truncated     {@code true} when {@code argsSummary} was cut at
     *                      {@value #ARGS_SUMMARY_MAX_LENGTH} characters
     * @param startedAt     wall-clock instant used as a shared identifier
     *                      with the paired {@link Completed} event
     * @param timestamp     when this event was produced
     * @param correlationId links to the originating pipeline operation
     */
    record Started(
            String toolName,
            String argsSummary,
            boolean truncated,
            Instant startedAt,
            Instant timestamp,
            String correlationId)
            implements ToolInvocationEvent {}

    /**
     * Published after a tool method returns or throws.
     *
     * @param toolName      simple name of the tool that was called
     * @param argsSummary   truncated string representation of arguments
     * @param truncated     {@code true} when {@code argsSummary} was cut
     * @param startedAt     matches {@link Started#startedAt()} for correlation
     * @param durationMillis wall-clock time from invocation to completion
     * @param error         {@link Throwable#getMessage()} if the tool threw;
     *                      {@code null} on success
     * @param timestamp     when this event was produced
     * @param correlationId links to the originating pipeline operation
     */
    record Completed(
            String toolName,
            String argsSummary,
            boolean truncated,
            Instant startedAt,
            long durationMillis,
            String error,
            Instant timestamp,
            String correlationId)
            implements ToolInvocationEvent {}
}
