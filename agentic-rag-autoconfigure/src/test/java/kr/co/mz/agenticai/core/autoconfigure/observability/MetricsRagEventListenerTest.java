package kr.co.mz.agenticai.core.autoconfigure.observability;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import kr.co.mz.agenticai.core.agent.orchestrator.SequentialAgentOrchestrator;
import kr.co.mz.agenticai.core.common.event.FactCheckEvent;
import kr.co.mz.agenticai.core.common.event.IngestionEvent;
import kr.co.mz.agenticai.core.common.event.LlmEvent;
import kr.co.mz.agenticai.core.common.event.RetrievalEvent;
import org.junit.jupiter.api.Test;

class MetricsRagEventListenerTest {

    private final MeterRegistry registry = new SimpleMeterRegistry();
    private final MetricsRagEventListener listener = new MetricsRagEventListener(registry);

    @Test
    void ingestionMetricsRecorded() {
        listener.onIngestionCompleted(new IngestionEvent.IngestionCompleted(
                "doc-1", 7, Instant.now(), "corr-1"));
        listener.onIngestionFailed(new IngestionEvent.IngestionFailed(
                "doc-2", "boom", "RuntimeException", Instant.now(), "corr-2"));

        assertThat(registry.counter("agentic.rag.ingestion.completed").count()).isEqualTo(1.0);
        assertThat(registry.counter("agentic.rag.ingestion.failed", "error", "RuntimeException").count())
                .isEqualTo(1.0);
        assertThat(registry.summary("agentic.rag.ingestion.chunks").totalAmount()).isEqualTo(7.0);
    }

    @Test
    void retrievalAndRerankMetricsRecorded() {
        listener.onRetrievalCompleted(new RetrievalEvent.RetrievalCompleted(
                "q", 5, "hybrid", 42L, Instant.now(), "c1"));
        listener.onRerankCompleted(new RetrievalEvent.RerankCompleted(
                10, 5, "NoopReranker", 3L, Instant.now(), "c1"));

        assertThat(registry.timer("agentic.rag.retrieval.duration", "retriever", "hybrid").count())
                .isEqualTo(1L);
        assertThat(registry.summary("agentic.rag.retrieval.hits", "retriever", "hybrid").totalAmount())
                .isEqualTo(5.0);
        assertThat(registry.timer("agentic.rag.rerank.duration", "reranker", "NoopReranker").count())
                .isEqualTo(1L);
    }

    @Test
    void llmDurationAndStreamedTokensRecorded() {
        listener.onLlmResponded(new LlmEvent.LlmResponded(
                "ollama", "gpt-oss:20b", 50L, 120L, 1500L, Instant.now(), "c1"));
        listener.onLlmTokenStreamed(new LlmEvent.LlmTokenStreamed(
                "ollama", "gpt-oss:20b", "안녕", Instant.now(), "c1"));
        listener.onLlmTokenStreamed(new LlmEvent.LlmTokenStreamed(
                "ollama", "gpt-oss:20b", " 세계", Instant.now(), "c1"));

        assertThat(registry.timer("agentic.rag.llm.duration",
                "provider", "ollama", "model", "gpt-oss:20b").count()).isEqualTo(1L);
        assertThat(registry.counter("agentic.rag.llm.tokens",
                "provider", "ollama", "model", "gpt-oss:20b", "kind", "streamed").count())
                .isEqualTo(2.0);
        assertThat(registry.summary("agentic.rag.llm.tokens",
                "provider", "ollama", "model", "gpt-oss:20b", "kind", "completion").totalAmount())
                .isEqualTo(120.0);
        assertThat(registry.summary("agentic.rag.llm.tokens",
                "provider", "ollama", "model", "gpt-oss:20b", "kind", "prompt").totalAmount())
                .isEqualTo(50.0);
        assertThat(registry.summary("agentic.rag.llm.tokens",
                "provider", "ollama", "model", "gpt-oss:20b", "kind", "total").totalAmount())
                .isEqualTo(170.0);
    }

    @Test
    void factCheckCountersIncrement() {
        listener.onFactCheckPassed(new FactCheckEvent.FactCheckPassed(
                "answer summary", 0.9, 2, 50L, Instant.now(), "c1"));
        listener.onFactCheckFailed(new FactCheckEvent.FactCheckFailed(
                "answer summary", 0.2, "ungrounded", 60L, Instant.now(), "c2"));

        assertThat(registry.counter("agentic.rag.factcheck.passed").count()).isEqualTo(1.0);
        assertThat(registry.counter("agentic.rag.factcheck.failed").count()).isEqualTo(1.0);
    }

    @Test
    void agentRunIterationsAndFailures() {
        listener.onAgentRunCompleted(new SequentialAgentOrchestrator.AgentRunCompleted(
                "c1", 2, true, Instant.now()));
        listener.onAgentRunFailed(new SequentialAgentOrchestrator.AgentRunFailed(
                "c2", "summary", "RuntimeException: boom", Instant.now()));

        assertThat(registry.summary("agentic.rag.agent.run.iterations", "grounded", "true")
                .totalAmount()).isEqualTo(2.0);
        assertThat(registry.counter("agentic.rag.agent.run.failed", "agent", "summary").count())
                .isEqualTo(1.0);
    }
}
