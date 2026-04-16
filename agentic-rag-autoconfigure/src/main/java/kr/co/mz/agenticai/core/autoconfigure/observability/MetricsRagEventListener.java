package kr.co.mz.agenticai.core.autoconfigure.observability;

import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.TimeUnit;
import kr.co.mz.agenticai.core.agent.orchestrator.SequentialAgentOrchestrator;
import kr.co.mz.agenticai.core.common.event.FactCheckEvent;
import kr.co.mz.agenticai.core.common.event.IngestionEvent;
import kr.co.mz.agenticai.core.common.event.LlmEvent;
import kr.co.mz.agenticai.core.common.event.RetrievalEvent;
import org.springframework.context.event.EventListener;

/**
 * Records Micrometer metrics from {@code RagEvent}s. Listener methods are
 * keyed on concrete event subtypes; Spring delivers each event to the
 * matching method.
 */
public class MetricsRagEventListener {

    private static final String M_INGEST_OK = "agentic.rag.ingestion.completed";
    private static final String M_INGEST_FAIL = "agentic.rag.ingestion.failed";
    private static final String M_INGEST_CHUNKS = "agentic.rag.ingestion.chunks";
    private static final String M_RETRIEVAL_DURATION = "agentic.rag.retrieval.duration";
    private static final String M_RETRIEVAL_HITS = "agentic.rag.retrieval.hits";
    private static final String M_RERANK_DURATION = "agentic.rag.rerank.duration";
    private static final String M_LLM_DURATION = "agentic.rag.llm.duration";
    private static final String M_LLM_TOKENS = "agentic.rag.llm.tokens";
    private static final String M_FACTCHECK_PASS = "agentic.rag.factcheck.passed";
    private static final String M_FACTCHECK_FAIL = "agentic.rag.factcheck.failed";
    private static final String M_AGENT_RUN_ITERS = "agentic.rag.agent.run.iterations";
    private static final String M_AGENT_RUN_FAIL = "agentic.rag.agent.run.failed";

    private static final String TAG_PROVIDER = "provider";
    private static final String TAG_MODEL = "model";

    private final MeterRegistry registry;

    public MetricsRagEventListener(MeterRegistry registry) {
        this.registry = registry;
    }

    @EventListener
    public void onIngestionCompleted(IngestionEvent.IngestionCompleted event) {
        registry.counter(M_INGEST_OK).increment();
        registry.summary(M_INGEST_CHUNKS).record(event.chunkCount());
    }

    @EventListener
    public void onIngestionFailed(IngestionEvent.IngestionFailed event) {
        registry.counter(M_INGEST_FAIL, "error", nullSafe(event.errorType())).increment();
    }

    @EventListener
    public void onRetrievalCompleted(RetrievalEvent.RetrievalCompleted event) {
        registry.timer(M_RETRIEVAL_DURATION, "retriever", nullSafe(event.retriever()))
                .record(event.elapsedMillis(), TimeUnit.MILLISECONDS);
        registry.summary(M_RETRIEVAL_HITS, "retriever", nullSafe(event.retriever()))
                .record(event.hitCount());
    }

    @EventListener
    public void onRerankCompleted(RetrievalEvent.RerankCompleted event) {
        registry.timer(M_RERANK_DURATION, "reranker", nullSafe(event.reranker()))
                .record(event.elapsedMillis(), TimeUnit.MILLISECONDS);
    }

    @EventListener
    public void onLlmResponded(LlmEvent.LlmResponded event) {
        registry.timer(M_LLM_DURATION,
                        TAG_PROVIDER, nullSafe(event.provider()),
                        TAG_MODEL, nullSafe(event.model()))
                .record(event.elapsedMillis(), TimeUnit.MILLISECONDS);
        if (event.completionTokens() > 0) {
            registry.summary(M_LLM_TOKENS,
                            TAG_PROVIDER, nullSafe(event.provider()),
                            TAG_MODEL, nullSafe(event.model()),
                            "kind", "completion")
                    .record(event.completionTokens());
        }
    }

    @EventListener
    public void onLlmTokenStreamed(LlmEvent.LlmTokenStreamed event) {
        registry.counter(M_LLM_TOKENS,
                        TAG_PROVIDER, nullSafe(event.provider()),
                        TAG_MODEL, nullSafe(event.model()),
                        "kind", "streamed")
                .increment();
    }

    @EventListener
    public void onFactCheckPassed(FactCheckEvent.FactCheckPassed event) {
        registry.counter(M_FACTCHECK_PASS).increment();
    }

    @EventListener
    public void onFactCheckFailed(FactCheckEvent.FactCheckFailed event) {
        registry.counter(M_FACTCHECK_FAIL).increment();
    }

    @EventListener
    public void onAgentRunCompleted(SequentialAgentOrchestrator.AgentRunCompleted event) {
        registry.summary(M_AGENT_RUN_ITERS,
                "grounded", String.valueOf(event.grounded())).record(event.iterations());
    }

    @EventListener
    public void onAgentRunFailed(SequentialAgentOrchestrator.AgentRunFailed event) {
        registry.counter(M_AGENT_RUN_FAIL, "agent", nullSafe(event.agentName())).increment();
    }

    private static String nullSafe(String s) {
        return s == null ? "unknown" : s;
    }
}
