package kr.co.mz.agenticai.core.common.observability;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;

/**
 * Attribute key constants and span name constants for RAG domain observations.
 *
 * <p>All attribute keys use the {@code rag.*} prefix to avoid collision with
 * OpenTelemetry semantic conventions that are not yet defined for RAG.
 */
public final class RagObservability {

    // Span names
    public static final String SPAN_RETRIEVAL = "rag.retrieval";
    public static final String SPAN_RETRIEVAL_VECTOR = "rag.retrieval.vector";
    public static final String SPAN_RETRIEVAL_BM25 = "rag.retrieval.bm25";
    public static final String SPAN_RETRIEVAL_FUSION = "rag.retrieval.fusion";
    public static final String SPAN_RETRIEVAL_RERANK = "rag.retrieval.rerank";
    public static final String SPAN_AGENT_RUN = "rag.agent.run";
    public static final String SPAN_AGENT_PLANNER = "rag.agent.planner";
    public static final String SPAN_AGENT_SYNTHESIZER = "rag.agent.synthesizer";
    public static final String SPAN_LLM_CALL = "rag.llm.call";
    public static final String SPAN_FACTCHECK = "rag.factcheck";

    // Attribute keys — retrieval
    /** Canonical name of the retriever (e.g. {@code vector}, {@code bm25}). */
    public static final String ATTR_RETRIEVER_ID = "rag.retriever.id";
    /** Requested top-K for this retrieval. */
    public static final String ATTR_RETRIEVAL_K = "rag.retrieval.k";
    /** Number of hits returned by this retriever. */
    public static final String ATTR_RETRIEVAL_HITS = "rag.retrieval.hits";
    /** Top score among returned hits (when available). */
    public static final String ATTR_RETRIEVAL_TOP_SCORE = "rag.retrieval.top_score";

    // Attribute keys — fusion
    /** Name of the fusion strategy applied (e.g. {@code rrf}, {@code weighted_sum}). */
    public static final String ATTR_FUSION_STRATEGY = "rag.fusion.strategy";

    // Attribute keys — rerank
    /** Simple class name of the {@link kr.co.mz.agenticai.core.common.spi.Reranker} used. */
    public static final String ATTR_RERANKER_CLASS = "rag.reranker.class";

    // Attribute keys — LLM
    /** Provider identifier (e.g. {@code spring-ai}). */
    public static final String ATTR_LLM_PROVIDER = "rag.llm.provider";
    /** Simple class name of the {@link org.springframework.ai.chat.model.ChatModel}. */
    public static final String ATTR_LLM_MODEL_CLASS = "rag.llm.model_class";
    /** Span event name emitted for each streamed token. */
    public static final String EVENT_LLM_TOKEN = "rag.llm.token";

    // Attribute keys — factcheck
    /** Verdict of the fact-checker: {@code grounded} or {@code ungrounded}. */
    public static final String ATTR_FACTCHECK_VERDICT = "rag.factcheck.verdict";
    /** Confidence score returned by the fact-checker (0.0–1.0). */
    public static final String ATTR_FACTCHECK_CONFIDENCE = "rag.factcheck.confidence";

    // Attribute keys — correlation
    /** Correlation ID propagated across the entire query path. */
    public static final String ATTR_CORRELATION_ID = "rag.correlation_id";

    private RagObservability() {}

    /**
     * Starts a new {@link Observation} with the given name. Returns a no-op
     * observation when {@code registry} is {@code null} or the registry has no
     * active handler (OTel not on classpath).
     */
    public static Observation start(String name, ObservationRegistry registry) {
        if (registry == null || registry.isNoop()) {
            return Observation.NOOP;
        }
        return Observation.start(name, registry);
    }
}
