package kr.co.mz.agenticai.core.common.observability;

import io.micrometer.tracing.BaggageInScope;
import io.micrometer.tracing.Tracer;

/**
 * Helpers for propagating the RAG correlation-id as OTel baggage.
 *
 * <p>All methods are no-ops when a {@link Tracer} is not available (OTel not
 * on the classpath or tracing disabled), so callers never need to null-check.
 */
public final class RagBaggage {

    public static final String CORRELATION_ID_KEY = "rag-correlation-id";

    private RagBaggage() {}

    /**
     * Sets the correlation-id as baggage in the current trace context.
     *
     * @return an {@link AutoCloseable} scope — close when the logical unit
     *         of work ends (or use try-with-resources). Returns a no-op scope
     *         when {@code tracer} is {@code null}.
     */
    @SuppressWarnings("deprecation")
    public static AutoCloseable set(Tracer tracer, String correlationId) {
        if (tracer == null || correlationId == null) {
            return () -> {};
        }
        // createBaggage/set are deprecated in micrometer-tracing 1.4.x but remain the
        // only cross-bridge (OTel + Brave) API for baggage propagation at this version.
        BaggageInScope scope = tracer.createBaggage(CORRELATION_ID_KEY).set(correlationId).makeCurrent();
        return scope;
    }

    /**
     * Reads the correlation-id from baggage in the current trace context.
     *
     * @return the value, or {@code null} if not present or tracing is absent.
     */
    public static String get(Tracer tracer) {
        if (tracer == null) {
            return null;
        }
        io.micrometer.tracing.Baggage baggage = tracer.getBaggage(CORRELATION_ID_KEY);
        return baggage == null ? null : baggage.get();
    }
}
