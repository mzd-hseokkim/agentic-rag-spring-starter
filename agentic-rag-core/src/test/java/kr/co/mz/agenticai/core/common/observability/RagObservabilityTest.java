package kr.co.mz.agenticai.core.common.observability;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.ObservationRegistry;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Verifies attribute key constants and that {@link RagObservability#start} returns
 * NOOP for null / noop registries and a live observation otherwise.
 */
class RagObservabilityTest {

    @Test
    void spanNameConstantsAreStable() {
        assertThat(RagObservability.SPAN_RETRIEVAL).isEqualTo("rag.retrieval");
        assertThat(RagObservability.SPAN_RETRIEVAL_VECTOR).isEqualTo("rag.retrieval.vector");
        assertThat(RagObservability.SPAN_RETRIEVAL_BM25).isEqualTo("rag.retrieval.bm25");
        assertThat(RagObservability.SPAN_RETRIEVAL_FUSION).isEqualTo("rag.retrieval.fusion");
        assertThat(RagObservability.SPAN_RETRIEVAL_RERANK).isEqualTo("rag.retrieval.rerank");
        assertThat(RagObservability.SPAN_LLM_CALL).isEqualTo("rag.llm.call");
        assertThat(RagObservability.SPAN_FACTCHECK).isEqualTo("rag.factcheck");
    }

    @Test
    void attributeKeyConstantsAreStable() {
        assertThat(RagObservability.ATTR_RETRIEVER_ID).isEqualTo("rag.retriever.id");
        assertThat(RagObservability.ATTR_RETRIEVAL_K).isEqualTo("rag.retrieval.k");
        assertThat(RagObservability.ATTR_RETRIEVAL_HITS).isEqualTo("rag.retrieval.hits");
        assertThat(RagObservability.ATTR_FUSION_STRATEGY).isEqualTo("rag.fusion.strategy");
        assertThat(RagObservability.ATTR_RERANKER_CLASS).isEqualTo("rag.reranker.class");
        assertThat(RagObservability.ATTR_LLM_PROVIDER).isEqualTo("rag.llm.provider");
        assertThat(RagObservability.ATTR_LLM_MODEL_CLASS).isEqualTo("rag.llm.model_class");
        assertThat(RagObservability.ATTR_FACTCHECK_VERDICT).isEqualTo("rag.factcheck.verdict");
        assertThat(RagObservability.ATTR_FACTCHECK_CONFIDENCE).isEqualTo("rag.factcheck.confidence");
        assertThat(RagObservability.ATTR_CORRELATION_ID).isEqualTo("rag.correlation_id");
        assertThat(RagObservability.EVENT_LLM_TOKEN).isEqualTo("rag.llm.token");
    }

    @Test
    void startReturnsNoopForNullRegistry() {
        Observation obs = RagObservability.start("rag.test", null);
        assertThat(obs).isSameAs(Observation.NOOP);
    }

    @Test
    void startReturnsNoopForNoopRegistry() {
        Observation obs = RagObservability.start("rag.test", ObservationRegistry.NOOP);
        assertThat(obs).isSameAs(Observation.NOOP);
    }

    @Test
    void startReturnsLiveObservationWhenRegistryHasHandler() {
        List<String> started = new ArrayList<>();
        List<String> stopped = new ArrayList<>();

        var registry = ObservationRegistry.create();
        registry.observationConfig().observationHandler(new ObservationHandler<>() {
            @Override
            public void onStart(Observation.Context ctx) { started.add(ctx.getName()); }
            @Override
            public void onStop(Observation.Context ctx) { stopped.add(ctx.getName()); }
            @Override
            public boolean supportsContext(Observation.Context ctx) { return true; }
        });

        Observation obs = RagObservability.start("rag.test.span", registry);
        assertThat(obs).isNotSameAs(Observation.NOOP);
        obs.stop();

        assertThat(started).containsExactly("rag.test.span");
        assertThat(stopped).containsExactly("rag.test.span");
    }
}
