package kr.co.mz.agenticai.core.retrieval;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.tracing.Tracer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import kr.co.mz.agenticai.core.common.event.RetrievalEvent;
import kr.co.mz.agenticai.core.common.observability.RagBaggage;
import kr.co.mz.agenticai.core.common.observability.RagObservability;
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
    private final ObservationRegistry observationRegistry;
    private final Tracer tracer;

    public HybridRetrieverRouter(
            List<DocumentSource> sources,
            ResultFusion fusion,
            Reranker reranker,
            RetrievalEvaluator evaluator,
            QueryTransformer queryTransformer,
            QueryExpander queryExpander,
            RagEventPublisher events,
            int overscanFactor) {
        this(sources, fusion, reranker, evaluator, queryTransformer, queryExpander, events,
                overscanFactor, ObservationRegistry.NOOP, null);
    }

    public HybridRetrieverRouter(
            List<DocumentSource> sources,
            ResultFusion fusion,
            Reranker reranker,
            RetrievalEvaluator evaluator,
            QueryTransformer queryTransformer,
            QueryExpander queryExpander,
            RagEventPublisher events,
            int overscanFactor,
            ObservationRegistry observationRegistry) {
        this(sources, fusion, reranker, evaluator, queryTransformer, queryExpander, events,
                overscanFactor, observationRegistry, null);
    }

    public HybridRetrieverRouter(
            List<DocumentSource> sources,
            ResultFusion fusion,
            Reranker reranker,
            RetrievalEvaluator evaluator,
            QueryTransformer queryTransformer,
            QueryExpander queryExpander,
            RagEventPublisher events,
            int overscanFactor,
            ObservationRegistry observationRegistry,
            Tracer tracer) {
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
        this.observationRegistry = observationRegistry != null ? observationRegistry : ObservationRegistry.NOOP;
        this.tracer = tracer;
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
        // Reuse the correlation-id propagated via baggage by the caller (e.g. DefaultAgenticRagClient);
        // fall back to a new UUID when tracing is absent or not yet set.
        String baggageId = RagBaggage.get(tracer);
        String correlationId = baggageId != null ? baggageId : UUID.randomUUID().toString();

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
                Observation sourceObs = RagObservability.start(
                        sourceSpanName(source.name()), observationRegistry);
                sourceObs.lowCardinalityKeyValue(RagObservability.ATTR_RETRIEVER_ID, source.name());
                sourceObs.lowCardinalityKeyValue(RagObservability.ATTR_RETRIEVAL_K,
                        String.valueOf(perSourceTopK));
                sourceObs.highCardinalityKeyValue(RagObservability.ATTR_CORRELATION_ID, correlationId);
                List<Document> hits;
                try {
                    hits = source.search(q.text(), perSourceTopK, routerQuery.metadataFilters());
                    sourceObs.highCardinalityKeyValue(RagObservability.ATTR_RETRIEVAL_HITS,
                            String.valueOf(hits == null ? 0 : hits.size()));
                } finally {
                    sourceObs.stop();
                }
                if (hits != null && !hits.isEmpty()) {
                    rankedLists.add(hits);
                }
            }
        }

        Observation fusionObs = RagObservability.start(RagObservability.SPAN_RETRIEVAL_FUSION, observationRegistry);
        fusionObs.lowCardinalityKeyValue(RagObservability.ATTR_FUSION_STRATEGY,
                fusion.getClass().getSimpleName());
        fusionObs.highCardinalityKeyValue(RagObservability.ATTR_CORRELATION_ID, correlationId);
        List<Document> fused;
        try {
            fused = fusion.fuse(rankedLists, routerQuery.topK() * Math.max(2, overscanFactor));
        } finally {
            fusionObs.stop();
        }
        events.publish(new RetrievalEvent.RetrievalCompleted(
                routerQuery.text(), fused.size(), "hybrid",
                System.currentTimeMillis() - started,
                Instant.now(), correlationId));

        long rerankStarted = System.currentTimeMillis();
        Observation rerankObs = RagObservability.start(RagObservability.SPAN_RETRIEVAL_RERANK, observationRegistry);
        rerankObs.lowCardinalityKeyValue(RagObservability.ATTR_RERANKER_CLASS,
                reranker.getClass().getSimpleName());
        rerankObs.highCardinalityKeyValue(RagObservability.ATTR_CORRELATION_ID, correlationId);
        List<Document> reranked;
        try {
            reranked = reranker.rerank(routerQuery.text(), fused, routerQuery.topK());
        } finally {
            rerankObs.stop();
        }
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

    private static String sourceSpanName(String sourceName) {
        return switch (sourceName) {
            case "vector" -> RagObservability.SPAN_RETRIEVAL_VECTOR;
            case "bm25" -> RagObservability.SPAN_RETRIEVAL_BM25;
            default -> RagObservability.SPAN_RETRIEVAL + "." + sourceName;
        };
    }
}
