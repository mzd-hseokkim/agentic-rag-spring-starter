package kr.co.mz.agenticai.core.retrieval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import kr.co.mz.agenticai.core.common.event.RagEvent;
import kr.co.mz.agenticai.core.common.event.RetrievalEvent;
import kr.co.mz.agenticai.core.common.spi.DocumentSource;
import kr.co.mz.agenticai.core.common.spi.RagEventPublisher;
import kr.co.mz.agenticai.core.common.spi.RetrievalEvaluator.Action;
import kr.co.mz.agenticai.core.common.spi.Reranker;
import kr.co.mz.agenticai.core.common.spi.RetrieverRouter;
import kr.co.mz.agenticai.core.retrieval.evaluate.PassThroughRetrievalEvaluator;
import kr.co.mz.agenticai.core.retrieval.evaluate.ScoreThresholdEvaluator;
import kr.co.mz.agenticai.core.retrieval.fusion.ReciprocalRankFusion;
import kr.co.mz.agenticai.core.retrieval.rerank.NoopReranker;
import kr.co.mz.agenticai.core.retrieval.RetrievalMetadata;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.preretrieval.query.expansion.QueryExpander;
import org.springframework.ai.rag.preretrieval.query.transformation.QueryTransformer;

class HybridRetrieverRouterTest {

    private final CapturingPublisher events = new CapturingPublisher();
    private final PassThroughRetrievalEvaluator passThrough = new PassThroughRetrievalEvaluator();

    @Test
    void rejectsEmptySourcesAndBadOverscan() {
        var emptySources = List.<kr.co.mz.agenticai.core.common.spi.DocumentSource>of();
        var fusion = new ReciprocalRankFusion();
        var reranker = new NoopReranker();
        assertThatThrownBy(() -> new HybridRetrieverRouter(
                emptySources, fusion, reranker, passThrough, null, null, events, 3))
                .isInstanceOf(IllegalArgumentException.class);

        var oneSource = List.of(constSource("a", new Document("d", "x", Map.of())));
        assertThatThrownBy(() -> new HybridRetrieverRouter(
                oneSource, fusion, reranker, passThrough, null, null, events, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void singleSourceFusedAndReranked() {
        Document a = new Document("a", "alpha", Map.of());
        Document b = new Document("b", "beta", Map.of());

        var router = new HybridRetrieverRouter(
                List.of(constSource("only", a, b)),
                new ReciprocalRankFusion(), new NoopReranker(), passThrough,
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
                new ReciprocalRankFusion(), new NoopReranker(), passThrough,
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
                new ReciprocalRankFusion(), new NoopReranker(), passThrough,
                null, null, events, 3);

        List<Document> hits = router.retrieve(RetrieverRouter.Query.of("q", 1));

        assertThat(hits).hasSize(1);
    }

    @Test
    void publishesQueryTransformedForBothTransformerAndExpander() {
        Document hit = new Document("a", "alpha", Map.of());

        var router = new HybridRetrieverRouter(
                List.of(constSource("only", hit)),
                new ReciprocalRankFusion(), new NoopReranker(), passThrough,
                new StubTransformer(), new StubExpander(), events, 3);

        router.retrieve(RetrieverRouter.Query.of("원본", 2));

        List<RetrievalEvent.QueryTransformed> transforms = events.events.stream()
                .filter(RetrievalEvent.QueryTransformed.class::isInstance)
                .map(RetrievalEvent.QueryTransformed.class::cast)
                .toList();
        assertThat(transforms).hasSize(2);
        assertThat(transforms.get(0).originalQuery()).isEqualTo("원본");
        assertThat(transforms.get(0).transformedQueries()).containsExactly("정제된 질문");
        assertThat(transforms.get(0).transformer()).isEqualTo("StubTransformer");
        assertThat(transforms.get(1).originalQuery()).isEqualTo("원본");
        assertThat(transforms.get(1).transformedQueries())
                .containsExactly("정제된 질문", "변형 1", "변형 2");
        assertThat(transforms.get(1).transformer()).isEqualTo("StubExpander");
    }

    private static final class StubTransformer implements QueryTransformer {
        @Override public Query transform(Query q) { return q.mutate().text("정제된 질문").build(); }
    }

    private static final class StubExpander implements QueryExpander {
        @Override public List<Query> expand(Query q) {
            return List.of(q,
                    q.mutate().text("변형 1").build(),
                    q.mutate().text("변형 2").build());
        }
    }

    @Test
    void emptySourceResultsAreSkipped() {
        var router = new HybridRetrieverRouter(
                List.of(constSource("empty"), constSource("only", new Document("a", "alpha", Map.of()))),
                new ReciprocalRankFusion(), new NoopReranker(), passThrough,
                null, null, events, 3);

        List<Document> hits = router.retrieve(RetrieverRouter.Query.of("q", 5));
        assertThat(hits).hasSize(1);
    }

    // T4: evaluator is called exactly once; EvaluationCompleted event is published
    @Test
    void evaluatorCalledOnceAndEventPublished() {
        Document doc = new Document("a", "alpha", Map.of(RetrievalMetadata.FUSED_SCORE, 0.8));
        PassThroughRetrievalEvaluator evaluatorSpy = spy(new PassThroughRetrievalEvaluator());

        var router = new HybridRetrieverRouter(
                List.of(constSource("only", doc)),
                new ReciprocalRankFusion(), new NoopReranker(), evaluatorSpy,
                null, null, events, 3);

        router.retrieveWithDecision(RetrieverRouter.Query.of("q", 1));

        verify(evaluatorSpy, times(1)).evaluate(any(), any());
        assertThat(events.events)
                .hasAtLeastOneElementOfType(RetrievalEvent.EvaluationCompleted.class);
    }

    // T6: retrieveWithDecision returns RetrievalOutcome with Decision; retrieve returns same docs
    @Test
    void retrieveWithDecisionContainsDecisionAndDocuments() {
        Document doc = new Document("a", "alpha", Map.of());

        var router = new HybridRetrieverRouter(
                List.of(constSource("only", doc)),
                new ReciprocalRankFusion(), new NoopReranker(), passThrough,
                null, null, events, 3);

        RetrieverRouter.Query query = RetrieverRouter.Query.of("q", 1);
        RetrievalOutcome outcome = router.retrieveWithDecision(query);
        List<Document> retrieveDocs = router.retrieve(query);

        assertThat(outcome.decision().action()).isEqualTo(Action.ACCEPT);
        assertThat(outcome.documents()).extracting(Document::getId).containsExactly("a");
        assertThat(retrieveDocs).extracting(Document::getId).containsExactly("a");
    }

    // T6b: ScoreThresholdEvaluator below-threshold path via retrieveWithDecision
    @Test
    void retrieveWithDecisionReturnsRetryWhenScoreBelowThreshold() {
        // RRF score for single-source single-doc ≈ 1/(60+1) which is below 0.5
        Document doc = new Document("b", "beta", Map.of());
        var evaluator = new ScoreThresholdEvaluator(0.5);

        var router = new HybridRetrieverRouter(
                List.of(constSource("only", doc)),
                new ReciprocalRankFusion(), new NoopReranker(), evaluator,
                null, null, events, 3);

        RetrievalOutcome outcome = router.retrieveWithDecision(RetrieverRouter.Query.of("q", 1));

        assertThat(outcome.decision().action()).isEqualTo(Action.RETRY);
        assertThat(outcome.documents()).extracting(Document::getId).containsExactly("b");
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
