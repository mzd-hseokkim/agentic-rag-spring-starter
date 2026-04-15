package kr.co.mz.agenticai.core.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import kr.co.mz.agenticai.core.common.IngestionRequest;
import kr.co.mz.agenticai.core.common.IngestionResult;
import kr.co.mz.agenticai.core.common.event.IngestionEvent;
import kr.co.mz.agenticai.core.common.event.RagEvent;
import kr.co.mz.agenticai.core.common.exception.IngestionException;
import kr.co.mz.agenticai.core.common.spi.ChunkSink;
import kr.co.mz.agenticai.core.common.spi.ChunkingStrategy;
import kr.co.mz.agenticai.core.common.spi.DocumentReader;
import kr.co.mz.agenticai.core.common.spi.RagEventPublisher;
import kr.co.mz.agenticai.core.ingestion.chunking.MarkdownHeadingChunkingStrategy;
import kr.co.mz.agenticai.core.ingestion.chunking.RecursiveCharacterChunkingStrategy;
import kr.co.mz.agenticai.core.ingestion.normalize.TextNormalizer;
import kr.co.mz.agenticai.core.ingestion.reader.MarkdownDocumentReader;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;

class DefaultIngestionPipelineTest {

    private final CapturingSink sink = new CapturingSink();
    private final CapturingPublisher events = new CapturingPublisher();

    @Test
    void ingestMarkdownEndToEnd() {
        DefaultIngestionPipeline pipeline = buildPipeline();

        IngestionResult result = pipeline.ingest(IngestionRequest.of(new ClassPathResource("sample.md")));

        assertThat(result.totalChunks()).isPositive();
        assertThat(result.chunkIds()).hasSize(result.totalChunks());
        assertThat(sink.received).isNotEmpty();
        assertThat(events.events).hasAtLeastOneElementOfType(IngestionEvent.DocumentRead.class);
        assertThat(events.events).hasAtLeastOneElementOfType(IngestionEvent.DocumentChunked.class);
        assertThat(events.events).hasAtLeastOneElementOfType(IngestionEvent.IngestionCompleted.class);
    }

    @Test
    void failsWhenNoReaderSupportsResource() {
        DefaultIngestionPipeline pipeline = buildPipeline();
        ByteArrayResource unknown = new ByteArrayResource("hello".getBytes(), "a.unknown");

        assertThatThrownBy(() -> pipeline.ingest(IngestionRequest.of(unknown)))
                .isInstanceOf(IngestionException.class)
                .hasMessageContaining("No DocumentReader");
    }

    @Test
    void explicitStrategyNameOverridesAutoSelection() {
        DefaultIngestionPipeline pipeline = buildPipeline();

        IngestionResult result = pipeline.ingest(
                IngestionRequest.builder()
                        .resource(new ClassPathResource("sample.md"))
                        .strategyName("recursive-character")
                        .build());

        assertThat(result.totalChunks()).isPositive();
        assertThat(sink.received).allSatisfy(chunk ->
                assertThat(chunk.getMetadata())
                        .containsEntry("agenticRag.chunkStrategy", "recursive-character"));
    }

    @Test
    void sinkFailurePublishesFailedEventAndPropagates() {
        ChunkSink failing = chunks -> { throw new RuntimeException("disk full"); };
        DefaultIngestionPipeline pipeline = new DefaultIngestionPipeline(
                List.of(new MarkdownDocumentReader()),
                List.of(new MarkdownHeadingChunkingStrategy(), new RecursiveCharacterChunkingStrategy()),
                new RecursiveCharacterChunkingStrategy(),
                new TextNormalizer(),
                List.of(failing),
                events);

        assertThatThrownBy(() -> pipeline.ingest(IngestionRequest.of(new ClassPathResource("sample.md"))))
                .isInstanceOf(IngestionException.class)
                .hasMessageContaining("ChunkSink");
        assertThat(events.events).hasAtLeastOneElementOfType(IngestionEvent.IngestionFailed.class);
    }

    @Test
    void metadataOverridesAreMerged() {
        DefaultIngestionPipeline pipeline = buildPipeline();

        pipeline.ingest(IngestionRequest.builder()
                .resource(new ClassPathResource("sample.md"))
                .metadataOverride("tenant", "acme")
                .build());

        assertThat(sink.received).allSatisfy(chunk ->
                assertThat(chunk.getMetadata()).containsEntry("tenant", "acme"));
    }

    private DefaultIngestionPipeline buildPipeline() {
        List<DocumentReader> readers = List.of(new MarkdownDocumentReader());
        List<ChunkingStrategy> strategies = List.of(
                new MarkdownHeadingChunkingStrategy(),
                new RecursiveCharacterChunkingStrategy());
        return new DefaultIngestionPipeline(
                readers, strategies, new RecursiveCharacterChunkingStrategy(),
                new TextNormalizer(), List.of(sink), events);
    }

    private static final class CapturingSink implements ChunkSink {
        final List<Document> received = new ArrayList<>();

        @Override
        public void accept(List<Document> chunks) {
            received.addAll(chunks);
        }
    }

    private static final class CapturingPublisher implements RagEventPublisher {
        final List<RagEvent> events = new ArrayList<>();

        @Override
        public void publish(RagEvent event) {
            events.add(event);
        }
    }
}
