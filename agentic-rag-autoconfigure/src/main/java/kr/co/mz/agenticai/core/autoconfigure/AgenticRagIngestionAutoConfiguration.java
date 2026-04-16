package kr.co.mz.agenticai.core.autoconfigure;

import java.util.List;
import kr.co.mz.agenticai.core.common.IngestionPipeline;
import kr.co.mz.agenticai.core.common.exception.IngestionException;
import kr.co.mz.agenticai.core.common.spi.ChunkSink;
import kr.co.mz.agenticai.core.common.spi.ChunkingStrategy;
import kr.co.mz.agenticai.core.common.spi.DocumentReader;
import kr.co.mz.agenticai.core.common.spi.RagEventPublisher;
import kr.co.mz.agenticai.core.ingestion.DefaultIngestionPipeline;
import kr.co.mz.agenticai.core.ingestion.chunking.FixedSizeChunkingStrategy;
import kr.co.mz.agenticai.core.ingestion.chunking.MarkdownHeadingChunkingStrategy;
import kr.co.mz.agenticai.core.ingestion.chunking.RecursiveCharacterChunkingStrategy;
import kr.co.mz.agenticai.core.ingestion.chunking.SemanticChunkingStrategy;
import kr.co.mz.agenticai.core.ingestion.normalize.TextNormalizer;
import kr.co.mz.agenticai.core.ingestion.reader.MarkdownDocumentReader;
import kr.co.mz.agenticai.core.ingestion.reader.PdfDocumentReader;
import kr.co.mz.agenticai.core.ingestion.sink.VectorStoreChunkSink;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

@AutoConfiguration(after = AgenticRagCoreAutoConfiguration.class)
@ConditionalOnProperty(
        name = {"agentic-rag.enabled", "agentic-rag.ingestion.enabled"},
        matchIfMissing = true)
public class AgenticRagIngestionAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public TextNormalizer textNormalizer(AgenticRagProperties props) {
        return new TextNormalizer(props.getIngestion().isNormalizeUnicode());
    }

    @Bean(name = "markdownDocumentReader")
    @ConditionalOnMissingBean(name = "markdownDocumentReader")
    public DocumentReader markdownDocumentReader() {
        return new MarkdownDocumentReader();
    }

    @Bean(name = "pdfDocumentReader")
    @ConditionalOnMissingBean(name = "pdfDocumentReader")
    public DocumentReader pdfDocumentReader() {
        return new PdfDocumentReader();
    }

    @Bean(name = "fixedSizeChunkingStrategy")
    @ConditionalOnMissingBean(name = "fixedSizeChunkingStrategy")
    public ChunkingStrategy fixedSizeChunkingStrategy(AgenticRagProperties props) {
        var c = props.getIngestion().getChunking().getFixedSize();
        return new FixedSizeChunkingStrategy(c.getMaxChars(), c.getOverlap());
    }

    @Bean(name = "recursiveCharacterChunkingStrategy")
    @ConditionalOnMissingBean(name = "recursiveCharacterChunkingStrategy")
    public ChunkingStrategy recursiveCharacterChunkingStrategy(AgenticRagProperties props) {
        int maxChars = props.getIngestion().getChunking().getRecursive().getMaxChars();
        if (props.getLanguage() == AgenticRagProperties.Language.KO) {
            return RecursiveCharacterChunkingStrategy.forKorean(maxChars);
        }
        return new RecursiveCharacterChunkingStrategy();
    }

    @Bean(name = "markdownHeadingChunkingStrategy")
    @ConditionalOnMissingBean(name = "markdownHeadingChunkingStrategy")
    public ChunkingStrategy markdownHeadingChunkingStrategy(AgenticRagProperties props) {
        int level = props.getIngestion().getChunking().getMarkdownHeading().getMaxLevel();
        return new MarkdownHeadingChunkingStrategy(level);
    }

    @Bean(name = "semanticChunkingStrategy")
    @ConditionalOnMissingBean(name = "semanticChunkingStrategy")
    @ConditionalOnBean(EmbeddingModel.class)
    @ConditionalOnProperty(prefix = "agentic-rag.ingestion.chunking.semantic", name = "enabled",
            havingValue = "true")
    public ChunkingStrategy semanticChunkingStrategy(
            EmbeddingModel embeddingModel, AgenticRagProperties props) {
        var c = props.getIngestion().getChunking().getSemantic();
        return new SemanticChunkingStrategy(embeddingModel, c.getThresholdPercentile(), c.getMaxChunkChars());
    }

    @Bean(name = "vectorStoreChunkSink")
    @ConditionalOnMissingBean(name = "vectorStoreChunkSink")
    @ConditionalOnBean(VectorStore.class)
    public ChunkSink vectorStoreChunkSink(VectorStore vectorStore) {
        return new VectorStoreChunkSink(vectorStore);
    }

    @Bean
    @ConditionalOnMissingBean
    public IngestionPipeline ingestionPipeline(
            List<DocumentReader> readers,
            List<ChunkingStrategy> strategies,
            AgenticRagProperties props,
            TextNormalizer normalizer,
            List<ChunkSink> sinks,
            RagEventPublisher events) {
        ChunkingStrategy defaultStrategy = pickDefault(strategies, props);
        return new DefaultIngestionPipeline(readers, strategies, defaultStrategy, normalizer, sinks, events);
    }

    private static ChunkingStrategy pickDefault(List<ChunkingStrategy> strategies, AgenticRagProperties props) {
        String override = props.getIngestion().getChunking().getDefaultStrategy();
        if (override != null && !override.isBlank()) {
            return strategies.stream()
                    .filter(s -> s.name().equals(override))
                    .findFirst()
                    .orElseThrow(() -> new IngestionException(
                            "Configured default strategy not found: " + override));
        }
        return strategies.stream()
                .filter(s -> RecursiveCharacterChunkingStrategy.CANONICAL_NAME.equals(s.name()))
                .findFirst()
                .orElseGet(() -> strategies.stream().findFirst()
                        .orElseThrow(() -> new IngestionException("No ChunkingStrategy beans registered")));
    }
}
