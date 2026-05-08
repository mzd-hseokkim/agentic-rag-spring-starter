package kr.co.mz.agenticai.core.retrieval;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import kr.co.mz.agenticai.core.common.event.RetrievalEvent;
import kr.co.mz.agenticai.core.common.spi.DocumentSource;
import kr.co.mz.agenticai.core.common.spi.RagEventPublisher;
import kr.co.mz.agenticai.core.common.spi.Reranker;
import kr.co.mz.agenticai.core.common.spi.RetrievalEvaluator;
import kr.co.mz.agenticai.core.common.spi.RetrieverRouter;
import kr.co.mz.agenticai.core.retrieval.fusion.ResultFusion;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.preretrieval.query.expansion.QueryExpander;
import org.springframework.ai.rag.preretrieval.query.transformation.QueryTransformer;

/**
 * Default {@link RetrieverRouter} — runs every registered
 * {@link DocumentSource} (BM25 + vector + ...) for the transformed query,
 * fuses their rankings, then reranks.
 *
 * <p>Flow:
 * <pre>
 *   Query
 *     → (optional) QueryTransformer  ("RAG이 뭐야?" → rewritten / HyDE draft)
 *     → (optional) QueryExpander     (1 → N variants)
 *     → for each variant × each DocumentSource → candidate list
 *     → ResultFusion (RRF by default)
 *     → Reranker (Noop by default)
 *     → top-K final
 * </pre>
 *
 * <p>Every stage publishes a {@link RetrievalEvent} through the supplied
 * {@link RagEventPublisher}.
 */
public final class HybridRetrieverRouter implements RetrieverRouter {

    private final List<DocumentSource> sources;
    private final ResultFusion fusion;
    private final Reranker reranker;
    private final RetrievalEvaluator evaluator;
    private final QueryTransformer queryTransformer;
    private final QueryExpander queryExpander;
    private final RagEventPublisher events;
    private final int overscanFactor;

    public HybridRetrieverRouter(
            List<DocumentSource> sources,
            ResultFusion fusion,
            Reranker reranker,
            RetrievalEvaluator evaluator,
            QueryTransformer queryTransformer,
            QueryExpander queryExpander,
            RagEventPublisher events,
            int overscanFactor) {
        if (sources == null || sources.isEmpty()) {
            throw new IllegalArgumentException("At least one DocumentSource is required");
        }
        if (overscanFactor < 1) {
            throw new IllegalArgumentException("overscanFactor must be >= 1, was " + overscanFactor);
        }
        this.sources = List.copyOf(sources);
        this.fusion = Objects.requireNonNull(fusion, "fusion");
        this.reranker = Objects.requireNonNull(reranker, "reranker");
        this.evaluator = Objects.requireNonNull(evaluator, "evaluator");
        this.queryTransformer = queryTransformer;
        this.queryExpander = queryExpander;
        this.events = Objects.requireNonNull(events, "events");
        this.overscanFactor = overscanFactor;
    }

    @Override
    public List<Document> retrieve(Query routerQuery) {
        return doRetrieve(routerQuery).documents();
    }

    /**
     * Retrieves documents and exposes the {@link RetrievalEvaluator}'s verdict
     * as a {@link RetrievalOutcome}. Callers that want to branch on the decision
     * (RETRY / FALLBACK / ABSTAIN) should use this method instead of
     * {@link #retrieve}.
     */
    public RetrievalOutcome retrieveWithDecision(Query query) {
        Objects.requireNonNull(query, "query");
        return doRetrieve(query);
    }

    private RetrievalOutcome doRetrieve(Query routerQuery) {
        Objects.requireNonNull(routerQuery, "query");
        long started = System.currentTimeMillis();
        String correlationId = UUID.randomUUID().toString();

        events.publish(new RetrievalEvent.QueryReceived(
                routerQuery.text(), null, Instant.now(), correlationId));

        org.springframework.ai.rag.Query transformed = new org.springframework.ai.rag.Query(routerQuery.text());
        if (queryTransformer != null) {
            transformed = queryTransformer.transform(transformed);
            events.publish(new RetrievalEvent.QueryTransformed(
                    routerQuery.text(),
                    List.of(transformed.text()),
                    queryTransformer.getClass().getSimpleName(),
                    Instant.now(),
                    correlationId));
        }

        List<org.springframework.ai.rag.Query> queries =
                queryExpander != null ? queryExpander.expand(transformed) : List.of(transformed);
        if (queryExpander != null) {
            events.publish(new RetrievalEvent.QueryTransformed(
                    routerQuery.text(),
                    queries.stream().map(org.springframework.ai.rag.Query::text).toList(),
                    queryExpander.getClass().getSimpleName(),
                    Instant.now(),
                    correlationId));
        }

        int perSourceTopK = Math.max(1, routerQuery.topK() * overscanFactor);
        List<List<Document>> rankedLists = new ArrayList<>();
        for (var q : queries) {
            for (DocumentSource source : sources) {
                List<Document> hits = source.search(q.text(), perSourceTopK, routerQuery.metadataFilters());
                if (hits != null && !hits.isEmpty()) {
                    rankedLists.add(hits);
                }
            }
        }

        List<Document> fused = fusion.fuse(rankedLists, routerQuery.topK() * Math.max(2, overscanFactor));
        events.publish(new RetrievalEvent.RetrievalCompleted(
                routerQuery.text(), fused.size(), "hybrid",
                System.currentTimeMillis() - started,
                Instant.now(), correlationId));

        long rerankStarted = System.currentTimeMillis();
        List<Document> reranked = reranker.rerank(routerQuery.text(), fused, routerQuery.topK());
        events.publish(new RetrievalEvent.RerankCompleted(
                fused.size(), reranked.size(),
                reranker.getClass().getSimpleName(),
                System.currentTimeMillis() - rerankStarted,
                Instant.now(), correlationId));

        long evalStarted = System.currentTimeMillis();
        RetrievalEvaluator.Decision decision = evaluator.evaluate(routerQuery, reranked);
        events.publish(new RetrievalEvent.EvaluationCompleted(
                decision.action(),
                decision.score(),
                evaluator.getClass().getSimpleName(),
                System.currentTimeMillis() - evalStarted,
                Instant.now(),
                correlationId));

        return new RetrievalOutcome(reranked, decision);
    }
}
