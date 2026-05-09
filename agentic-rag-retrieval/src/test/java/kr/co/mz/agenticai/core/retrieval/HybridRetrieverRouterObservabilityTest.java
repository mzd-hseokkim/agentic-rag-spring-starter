package kr.co.mz.agenticai.core.retrieval;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.ObservationRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import kr.co.mz.agenticai.core.common.observability.RagObservability;
import kr.co.mz.agenticai.core.common.spi.DocumentSource;
import kr.co.mz.agenticai.core.common.spi.RagEventPublisher;
import kr.co.mz.agenticai.core.common.spi.RetrieverRouter;
import kr.co.mz.agenticai.core.retrieval.evaluate.PassThroughRetrievalEvaluator;
import kr.co.mz.agenticai.core.retrieval.fusion.ReciprocalRankFusion;
import kr.co.mz.agenticai.core.retrieval.rerank.NoopReranker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

/**
 * T1 — Retrieval path emits source, fusion, and rerank spans with expected attributes.
 */
class HybridRetrieverRouterObservabilityTest {

    private final List<String> startedSpans = new ArrayList<>();
    private final List<String> stoppedSpans = new ArrayList<>();
    private final Map<String, String> capturedAttrs = new java.util.LinkedHashMap<>();

    private ObservationRegistry registry;

    @BeforeEach
    void setUp() {
        registry = ObservationRegistry.create();
        registry.observationConfig().observationHandler(new ObservationHandler<>() {
            @Override
            public void onStart(Observation.Context ctx) {
                startedSpans.add(ctx.getName());
            }
            @Override
            public void onStop(Observation.Context ctx) {
                stoppedSpans.add(ctx.getName());
                ctx.getLowCardinalityKeyValues().forEach(kv ->
                        capturedAttrs.put(ctx.getName() + ":" + kv.getKey(), kv.getValue()));
                ctx.getHighCardinalityKeyValues().forEach(kv ->
                        capturedAttrs.put(ctx.getName() + ":" + kv.getKey(), kv.getValue()));
            }
            @Override
            public boolean supportsContext(Observation.Context ctx) { return true; }
        });
    }

    @Test
    void singleSourceRetrievalEmitsSourceFusionAndRerankSpans() {
        Document doc = new Document("d1", "content", Map.of());
        DocumentSource src = new DocumentSource() {
            @Override public String name() { return "vector"; }
            @Override public List<Document> search(String q, int k, Map<String, Object> f) { return List.of(doc); }
        };

        var router = new HybridRetrieverRouter(
                List.of(src),
                new ReciprocalRankFusion(), new NoopReranker(),
                new PassThroughRetrievalEvaluator(),
                null, null, noopPublisher(), 1, registry);

        router.retrieve(RetrieverRouter.Query.of("test query", 1));

        // T1: source span
        assertThat(startedSpans).contains(RagObservability.SPAN_RETRIEVAL_VECTOR);
        // fusion span
        assertThat(startedSpans).contains(RagObservability.SPAN_RETRIEVAL_FUSION);
        // rerank span
        assertThat(startedSpans).contains(RagObservability.SPAN_RETRIEVAL_RERANK);

        // all spans stopped
        assertThat(stoppedSpans).containsAll(startedSpans);
    }

    @Test
    void sourceSpanCarriesRetrieverIdAndKAttributes() {
        Document doc = new Document("d2", "text", Map.of());
        DocumentSource src = new DocumentSource() {
            @Override public String name() { return "bm25"; }
            @Override public List<Document> search(String q, int k, Map<String, Object> f) { return List.of(doc); }
        };

        var router = new HybridRetrieverRouter(
                List.of(src),
                new ReciprocalRankFusion(), new NoopReranker(),
                new PassThroughRetrievalEvaluator(),
                null, null, noopPublisher(), 2, registry);

        router.retrieve(RetrieverRouter.Query.of("q", 1));

        assertThat(capturedAttrs)
                .containsEntry(RagObservability.SPAN_RETRIEVAL_BM25 + ":" + RagObservability.ATTR_RETRIEVER_ID, "bm25");
        assertThat(capturedAttrs)
                .containsKey(RagObservability.SPAN_RETRIEVAL_BM25 + ":" + RagObservability.ATTR_RETRIEVAL_K);
    }

    @Test
    void fusionSpanCarriesFusionStrategyAttribute() {
        Document doc = new Document("d3", "c", Map.of());
        DocumentSource src = new DocumentSource() {
            @Override public String name() { return "vector"; }
            @Override public List<Document> search(String q, int k, Map<String, Object> f) { return List.of(doc); }
        };

        var router = new HybridRetrieverRouter(
                List.of(src),
                new ReciprocalRankFusion(), new NoopReranker(),
                new PassThroughRetrievalEvaluator(),
                null, null, noopPublisher(), 1, registry);

        router.retrieve(RetrieverRouter.Query.of("q", 1));

        assertThat(capturedAttrs)
                .containsKey(RagObservability.SPAN_RETRIEVAL_FUSION + ":" + RagObservability.ATTR_FUSION_STRATEGY);
    }

    @Test
    void rerankSpanCarriesRerankerClassAttribute() {
        Document doc = new Document("d4", "c", Map.of());
        DocumentSource src = new DocumentSource() {
            @Override public String name() { return "vector"; }
            @Override public List<Document> search(String q, int k, Map<String, Object> f) { return List.of(doc); }
        };

        var router = new HybridRetrieverRouter(
                List.of(src),
                new ReciprocalRankFusion(), new NoopReranker(),
                new PassThroughRetrievalEvaluator(),
                null, null, noopPublisher(), 1, registry);

        router.retrieve(RetrieverRouter.Query.of("q", 1));

        assertThat(capturedAttrs)
                .containsKey(RagObservability.SPAN_RETRIEVAL_RERANK + ":" + RagObservability.ATTR_RERANKER_CLASS);
    }

    @Test
    void noopRegistryDoesNotThrow() {
        Document doc = new Document("d5", "c", Map.of());
        DocumentSource src = new DocumentSource() {
            @Override public String name() { return "vector"; }
            @Override public List<Document> search(String q, int k, Map<String, Object> f) { return List.of(doc); }
        };

        var router = new HybridRetrieverRouter(
                List.of(src),
                new ReciprocalRankFusion(), new NoopReranker(),
                new PassThroughRetrievalEvaluator(),
                null, null, noopPublisher(), 1, ObservationRegistry.NOOP);

        // must not throw
        List<Document> result = router.retrieve(RetrieverRouter.Query.of("q", 1));
        assertThat(result).isNotEmpty();
    }

    private static RagEventPublisher noopPublisher() {
        return event -> {};
    }
}
