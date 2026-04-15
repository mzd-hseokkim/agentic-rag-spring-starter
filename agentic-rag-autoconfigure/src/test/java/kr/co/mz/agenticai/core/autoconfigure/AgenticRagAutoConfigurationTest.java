package kr.co.mz.agenticai.core.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.List;
import kr.co.mz.agenticai.core.common.IngestionPipeline;
import kr.co.mz.agenticai.core.common.event.ApplicationEventRagEventPublisher;
import kr.co.mz.agenticai.core.common.event.RagEvent;
import kr.co.mz.agenticai.core.common.spi.ChunkSink;
import kr.co.mz.agenticai.core.common.spi.ChunkingStrategy;
import kr.co.mz.agenticai.core.common.spi.DocumentReader;
import kr.co.mz.agenticai.core.common.spi.RagEventPublisher;
import kr.co.mz.agenticai.core.common.spi.Reranker;
import kr.co.mz.agenticai.core.retrieval.bm25.LuceneBm25Index;
import kr.co.mz.agenticai.core.retrieval.fusion.ResultFusion;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.ko.KoreanAnalyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

class AgenticRagAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    AgenticRagCoreAutoConfiguration.class,
                    AgenticRagIngestionAutoConfiguration.class,
                    AgenticRagRetrievalAutoConfiguration.class));

    @Test
    void registersAllExpectedBeansByDefault() {
        runner.run(ctx -> {
            assertThat(ctx).hasSingleBean(RagEventPublisher.class);
            assertThat(ctx).hasSingleBean(IngestionPipeline.class);
            assertThat(ctx).hasSingleBean(LuceneBm25Index.class);
            assertThat(ctx).hasSingleBean(ResultFusion.class);
            assertThat(ctx).hasSingleBean(Reranker.class);
            assertThat(ctx).hasBean("markdownDocumentReader");
            assertThat(ctx).hasBean("pdfDocumentReader");
            assertThat(ctx).hasBean("fixedSizeChunkingStrategy");
            assertThat(ctx).hasBean("recursiveCharacterChunkingStrategy");
            assertThat(ctx).hasBean("markdownHeadingChunkingStrategy");
            assertThat(ctx).hasBean("bm25ChunkSink");
        });
    }

    @Test
    void koreanLanguageWiresKoreanAnalyzer() {
        runner.run(ctx -> {
            Analyzer a = ctx.getBean(Analyzer.class);
            assertThat(a).isInstanceOf(KoreanAnalyzer.class);
        });
    }

    @Test
    void englishLanguageWiresStandardAnalyzer() {
        runner.withPropertyValues("agentic-rag.language=en").run(ctx -> {
            Analyzer a = ctx.getBean(Analyzer.class);
            assertThat(a).isInstanceOf(StandardAnalyzer.class);
        });
    }

    @Test
    void semanticStrategyRegisteredOnlyWhenEnabledAndEmbeddingModelPresent() {
        runner.run(ctx -> assertThat(ctx).doesNotHaveBean("semanticChunkingStrategy"));

        runner.withPropertyValues("agentic-rag.ingestion.chunking.semantic.enabled=true")
                .run(ctx -> assertThat(ctx).doesNotHaveBean("semanticChunkingStrategy")); // no EmbeddingModel

        runner.withPropertyValues("agentic-rag.ingestion.chunking.semantic.enabled=true")
                .withUserConfiguration(WithEmbeddingModel.class)
                .run(ctx -> assertThat(ctx).hasBean("semanticChunkingStrategy"));
    }

    @Test
    void vectorStoreSinkRegisteredOnlyWhenVectorStorePresent() {
        runner.run(ctx -> assertThat(ctx).doesNotHaveBean("vectorStoreChunkSink"));

        runner.withUserConfiguration(WithVectorStore.class)
                .run(ctx -> assertThat(ctx).hasBean("vectorStoreChunkSink"));
    }

    @Test
    void userBeanOverridesDefaultRagEventPublisher() {
        runner.withUserConfiguration(CustomPublisherConfig.class).run(ctx -> {
            RagEventPublisher p = ctx.getBean(RagEventPublisher.class);
            assertThat(p).isNotInstanceOf(ApplicationEventRagEventPublisher.class);
        });
    }

    @Test
    void disablingGloballySkipsAllConfig() {
        runner.withPropertyValues("agentic-rag.enabled=false").run(ctx -> {
            assertThat(ctx).doesNotHaveBean(RagEventPublisher.class);
            assertThat(ctx).doesNotHaveBean(IngestionPipeline.class);
        });
    }

    @Test
    void pipelineCollectsAllChunkingStrategiesAndSinks() {
        runner.run(ctx -> {
            List<ChunkingStrategy> strategies = List.copyOf(ctx.getBeansOfType(ChunkingStrategy.class).values());
            assertThat(strategies).extracting(ChunkingStrategy::name)
                    .containsExactlyInAnyOrder(
                            "fixed-size", "recursive-character", "markdown-heading");
            List<ChunkSink> sinks = List.copyOf(ctx.getBeansOfType(ChunkSink.class).values());
            assertThat(sinks).extracting(ChunkSink::name).contains("bm25");
            assertThat(ctx.getBeansOfType(DocumentReader.class)).hasSize(2);
        });
    }

    @Configuration
    static class WithEmbeddingModel {
        @Bean
        EmbeddingModel embeddingModel() {
            return mock(EmbeddingModel.class);
        }
    }

    @Configuration
    static class WithVectorStore {
        @Bean
        VectorStore vectorStore() {
            return mock(VectorStore.class);
        }
    }

    @Configuration
    static class CustomPublisherConfig {
        @Bean
        RagEventPublisher ragEventPublisher() {
            return new CustomPublisher();
        }
    }

    static class CustomPublisher implements RagEventPublisher {
        @Override public void publish(RagEvent event) { /* no-op */ }
        // Silence unused-import warning.
        @SuppressWarnings("unused")
        private Document unused;
    }
}
