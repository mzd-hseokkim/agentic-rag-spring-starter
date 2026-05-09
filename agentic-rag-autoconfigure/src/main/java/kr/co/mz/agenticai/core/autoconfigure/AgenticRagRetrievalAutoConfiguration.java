package kr.co.mz.agenticai.core.autoconfigure;

import io.micrometer.observation.ObservationRegistry;
import java.util.List;
import kr.co.mz.agenticai.core.common.spi.ChunkSink;
import kr.co.mz.agenticai.core.common.spi.CrossEncoderScorer;
import kr.co.mz.agenticai.core.common.spi.DocumentSource;
import kr.co.mz.agenticai.core.common.spi.RagEventPublisher;
import kr.co.mz.agenticai.core.common.spi.Reranker;
import kr.co.mz.agenticai.core.common.spi.RetrievalEvaluator;
import kr.co.mz.agenticai.core.common.spi.RetrieverRouter;
import kr.co.mz.agenticai.core.retrieval.evaluate.PassThroughRetrievalEvaluator;
import kr.co.mz.agenticai.core.retrieval.evaluate.ScoreThresholdEvaluator;
import kr.co.mz.agenticai.core.retrieval.HybridRetrieverRouter;
import kr.co.mz.agenticai.core.retrieval.bm25.Bm25ChunkSink;
import kr.co.mz.agenticai.core.retrieval.bm25.KoreanAnalyzers;
import kr.co.mz.agenticai.core.retrieval.bm25.LuceneBm25Index;
import kr.co.mz.agenticai.core.retrieval.fusion.ReciprocalRankFusion;
import kr.co.mz.agenticai.core.retrieval.fusion.ResultFusion;
import kr.co.mz.agenticai.core.retrieval.query.HydeQueryTransformer;
import kr.co.mz.agenticai.core.retrieval.query.MultiQueryExpander;
import kr.co.mz.agenticai.core.retrieval.query.RewriteQueryTransformer;
import kr.co.mz.agenticai.core.retrieval.rerank.CrossEncoderReranker;
import kr.co.mz.agenticai.core.retrieval.rerank.NoopReranker;
import kr.co.mz.agenticai.core.retrieval.source.Bm25DocumentSource;
import kr.co.mz.agenticai.core.retrieval.source.VectorStoreDocumentSource;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.rag.preretrieval.query.expansion.QueryExpander;
import org.springframework.ai.rag.preretrieval.query.transformation.QueryTransformer;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

@AutoConfiguration(after = AgenticRagCoreAutoConfiguration.class)
@ConditionalOnProperty(
        name = {"agentic-rag.enabled", "agentic-rag.retrieval.enabled"},
        matchIfMissing = true)
public class AgenticRagRetrievalAutoConfiguration {

    @Bean(name = "bm25Analyzer")
    @ConditionalOnMissingBean(name = "bm25Analyzer")
    public Analyzer bm25Analyzer(AgenticRagProperties props) {
        return switch (props.getLanguage()) {
            case KO -> KoreanAnalyzers.standard();
            case EN -> new StandardAnalyzer();
        };
    }

    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "agentic-rag.retrieval.bm25", name = "enabled", matchIfMissing = true)
    public LuceneBm25Index luceneBm25Index(Analyzer bm25Analyzer) {
        return new LuceneBm25Index(bm25Analyzer);
    }

    @Bean
    @ConditionalOnMissingBean(name = "bm25ChunkSink")
    @ConditionalOnProperty(prefix = "agentic-rag.retrieval.bm25", name = "enabled", matchIfMissing = true)
    public ChunkSink bm25ChunkSink(LuceneBm25Index index) {
        return new Bm25ChunkSink(index);
    }

    @Bean
    @ConditionalOnMissingBean
    public ResultFusion resultFusion() {
        return new ReciprocalRankFusion();
    }

    @Bean
    @ConditionalOnMissingBean
    public Reranker reranker(ObjectProvider<CrossEncoderScorer> scorerProvider) {
        CrossEncoderScorer scorer = scorerProvider.getIfAvailable();
        return scorer != null ? new CrossEncoderReranker(scorer) : new NoopReranker();
    }

    // NOTE: no @ConditionalOnBean(ChatModel.class) on query transformers —
    // Spring AI model auto-configs may register ChatModel AFTER our conditions
    // are evaluated. We rely on constructor injection instead.
    @Bean(name = "hydeQueryTransformer")
    @ConditionalOnMissingBean(name = "hydeQueryTransformer")
    @ConditionalOnProperty(prefix = "agentic-rag.retrieval.query.hyde", name = "enabled",
            havingValue = "true")
    public HydeQueryTransformer hydeQueryTransformer(ChatModel chatModel) {
        return new HydeQueryTransformer(chatModel);
    }

    @Bean(name = "rewriteQueryTransformer")
    @ConditionalOnMissingBean(name = "rewriteQueryTransformer")
    @ConditionalOnProperty(prefix = "agentic-rag.retrieval.query.rewrite", name = "enabled",
            havingValue = "true")
    public RewriteQueryTransformer rewriteQueryTransformer(ChatModel chatModel) {
        return new RewriteQueryTransformer(chatModel);
    }

    @Bean(name = "multiQueryExpander")
    @ConditionalOnMissingBean(name = "multiQueryExpander")
    @ConditionalOnProperty(prefix = "agentic-rag.retrieval.query.multi-query", name = "enabled",
            havingValue = "true")
    public MultiQueryExpander multiQueryExpander(ChatModel chatModel, AgenticRagProperties props) {
        var cfg = props.getRetrieval().getQuery().getMultiQuery();
        return new MultiQueryExpander(chatModel,
                kr.co.mz.agenticai.core.retrieval.query.KoreanQueryPrompts.MULTI_QUERY,
                cfg.getCount(), cfg.isIncludeOriginal());
    }

    @Bean(name = "bm25DocumentSource")
    @ConditionalOnMissingBean(name = "bm25DocumentSource")
    @ConditionalOnBean(LuceneBm25Index.class)
    public DocumentSource bm25DocumentSource(LuceneBm25Index index) {
        return new Bm25DocumentSource(index);
    }

    @Bean(name = "vectorStoreDocumentSource")
    @ConditionalOnMissingBean(name = "vectorStoreDocumentSource")
    @ConditionalOnBean(VectorStore.class)
    public DocumentSource vectorStoreDocumentSource(VectorStore vectorStore) {
        return new VectorStoreDocumentSource(vectorStore);
    }

    @Bean
    @ConditionalOnMissingBean(RetrievalEvaluator.class)
    public RetrievalEvaluator retrievalEvaluator(AgenticRagProperties props) {
        var cfg = props.getRetrieval().getEvaluator();
        if (cfg.isEnabled() && "score-threshold".equals(cfg.getStrategy())) {
            return new ScoreThresholdEvaluator(cfg.getMinScore());
        }
        return new PassThroughRetrievalEvaluator();
    }

    @Bean
    @ConditionalOnMissingBean(RetrieverRouter.class)
    @ConditionalOnBean(DocumentSource.class)
    public RetrieverRouter retrieverRouter(
            List<DocumentSource> sources,
            ResultFusion fusion,
            Reranker reranker,
            RetrievalEvaluator evaluator,
            ObjectProvider<QueryTransformer> queryTransformer,
            ObjectProvider<QueryExpander> queryExpander,
            RagEventPublisher events,
            ObjectProvider<ObservationRegistry> observationRegistry,
            AgenticRagProperties props) {
        return new HybridRetrieverRouter(
                sources, fusion, reranker,
                evaluator,
                queryTransformer.getIfAvailable(),
                queryExpander.getIfAvailable(),
                events,
                props.getRetrieval().getOverscanFactor(),
                observationRegistry.getIfAvailable(),
                null);
    }
}
