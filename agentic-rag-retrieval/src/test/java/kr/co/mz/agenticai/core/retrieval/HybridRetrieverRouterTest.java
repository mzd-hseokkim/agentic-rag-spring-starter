package kr.co.mz.agenticai.core.retrieval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import kr.co.mz.agenticai.core.common.event.RagEvent;
import kr.co.mz.agenticai.core.common.event.RetrievalEvent;
import kr.co.mz.agenticai.core.common.spi.DocumentSource;
import kr.co.mz.agenticai.core.common.spi.RagEventPublisher;
import kr.co.mz.agenticai.core.common.spi.Reranker;
import kr.co.mz.agenticai.core.common.spi.RetrieverRouter;
import kr.co.mz.agenticai.core.retrieval.fusion.ReciprocalRankFusion;
import kr.co.mz.agenticai.core.retrieval.rerank.NoopReranker;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

class HybridRetrieverRouterTest {

    private final CapturingPublisher events = new CapturingPublisher();

    @Test
    void rejectsEmptySourcesAndBadOverscan() {
        var emptySources = List.<kr.co.mz.agenticai.core.common.spi.DocumentSource>of();
        var fusion = new ReciprocalRankFusion();
        var reranker = new NoopReranker();
        assertThatThrownBy(() -> new HybridRetrieverRouter(
                emptySources, fusion, reranker, null, null, events, 3))
                .isInstanceOf(IllegalArgumentException.class);

        var oneSource = List.of(constSource("a", new Document("d", "x", Map.of())));
        assertThatThrownBy(() -> new HybridRetrieverRouter(
                oneSource, fusion, reranker, null, null, events, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void singleSourceFusedAndReranked() {
        Document a = new Document("a", "alpha", Map.of());
        Document b = new Document("b", "beta", Map.of());

        var router = new HybridRetrieverRouter(
                List.of(constSource("only", a, b)),
                new ReciprocalRankFusion(), new NoopReranker(),
                null, null, events, 3);

        List<Document> hits = router.retrieve(RetrieverRouter.Query.of("anything", 2));

        assertThat(hits).extracting(Document::getId).containsExactly("a", "b");
        // Events: QueryReceived, RetrievalCompleted, RerankCompleted
        assertThat(events.events)
                .hasAtLeastOneElementOfType(RetrievalEvent.QueryReceived.class)
                .hasAtLeastOneElementOfType(RetrievalEvent.RetrievalCompleted.class)
                .hasAtLeastOneElementOfType(RetrievalEvent.RerankCompleted.class);
    }

    @Test
    void overlapInTwoSourcesRanksHighestAfterRrf() {
        Document a = new Document("a", "alpha", Map.of());
        Document b = new Document("b", "beta", Map.of());
        Document c = new Document("c", "gamma", Map.of());

        // src1: a, b      src2: c, a   → a appears in both → wins under RRF
        var router = new HybridRetrieverRouter(
                List.of(constSource("v", a, b), constSource("k", c, a)),
                new ReciprocalRankFusion(), new NoopReranker(),
                null, null, events, 3);

        List<Document> hits = router.retrieve(RetrieverRouter.Query.of("q", 3));

        assertThat(hits.get(0).getId()).isEqualTo("a");
    }

    @Test
    void rerankerTruncatesToTopK() {
        Document a = new Document("a", "alpha", Map.of());
        Document b = new Document("b", "beta", Map.of());
        Document c = new Document("c", "gamma", Map.of());

        var router = new HybridRetrieverRouter(
                List.of(constSource("only", a, b, c)),
                new ReciprocalRankFusion(), new NoopReranker(),
                null, null, events, 3);

        List<Document> hits = router.retrieve(RetrieverRouter.Query.of("q", 1));

        assertThat(hits).hasSize(1);
    }

    @Test
    void emptySourceResultsAreSkipped() {
        var router = new HybridRetrieverRouter(
                List.of(constSource("empty"), constSource("only", new Document("a", "alpha", Map.of()))),
                new ReciprocalRankFusion(), new NoopReranker(),
                null, null, events, 3);

        List<Document> hits = router.retrieve(RetrieverRouter.Query.of("q", 5));
        assertThat(hits).hasSize(1);
    }

    private static DocumentSource constSource(String name, Document... docs) {
        return new DocumentSource() {
            @Override public String name() { return name; }
            @Override public List<Document> search(String query, int topK, Map<String, Object> filters) {
                return List.of(docs);
            }
        };
    }

    private static final class CapturingPublisher implements RagEventPublisher {
        final List<RagEvent> events = new ArrayList<>();
        @Override public void publish(RagEvent event) { events.add(event); }
    }

    /** Reranker import sanity — keeps the dependency explicit. */
    @SuppressWarnings("unused")
    private static final Reranker UNUSED = new NoopReranker();
}
