package kr.co.mz.agenticai.core.ingestion;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import kr.co.mz.agenticai.core.common.IngestionPipeline;
import kr.co.mz.agenticai.core.common.IngestionRequest;
import kr.co.mz.agenticai.core.common.IngestionResult;
import kr.co.mz.agenticai.core.common.event.IngestionEvent;
import kr.co.mz.agenticai.core.common.exception.IngestionException;
import kr.co.mz.agenticai.core.common.spi.ChunkSink;
import kr.co.mz.agenticai.core.common.spi.ChunkingStrategy;
import kr.co.mz.agenticai.core.common.spi.DocumentReader;
import kr.co.mz.agenticai.core.common.spi.RagEventPublisher;
import kr.co.mz.agenticai.core.ingestion.normalize.TextNormalizer;
import org.springframework.ai.document.Document;
import org.springframework.core.io.Resource;

/**
 * Default {@link IngestionPipeline}: pick a {@link DocumentReader}, read the
 * resource, apply {@link TextNormalizer}, pick a {@link ChunkingStrategy},
 * chunk, and fan out to every registered {@link ChunkSink}. Every step
 * emits an {@link IngestionEvent} through the supplied
 * {@link RagEventPublisher}.
 */
public final class DefaultIngestionPipeline implements IngestionPipeline {

    private final List<DocumentReader> readers;
    private final List<ChunkingStrategy> strategies;
    private final ChunkingStrategy defaultStrategy;
    private final TextNormalizer normalizer;
    private final List<ChunkSink> sinks;
    private final RagEventPublisher events;

    public DefaultIngestionPipeline(
            List<DocumentReader> readers,
            List<ChunkingStrategy> strategies,
            ChunkingStrategy defaultStrategy,
            TextNormalizer normalizer,
            List<ChunkSink> sinks,
            RagEventPublisher events) {
        if (readers == null || readers.isEmpty()) {
            throw new IllegalArgumentException("At least one DocumentReader is required");
        }
        this.readers = List.copyOf(readers);
        this.strategies = strategies == null ? List.of() : List.copyOf(strategies);
        this.defaultStrategy = Objects.requireNonNull(defaultStrategy, "defaultStrategy");
        this.normalizer = normalizer == null ? new TextNormalizer() : normalizer;
        this.sinks = sinks == null ? List.of() : List.copyOf(sinks);
        this.events = Objects.requireNonNull(events, "events");
    }

    @Override
    public IngestionResult ingest(IngestionRequest request) {
        Objects.requireNonNull(request, "request");
        long started = System.currentTimeMillis();
        String correlationId = UUID.randomUUID().toString();

        DocumentReader reader = readers.stream()
                .filter(r -> r.supports(request.resource()))
                .findFirst()
                .orElseThrow(() -> new IngestionException(
                        "No DocumentReader supports resource: " + describe(request.resource())));

        List<Document> documents;
        try {
            documents = reader.read(request.resource());
        } catch (IngestionException e) {
            emitFailed(correlationId, describe(request.resource()), e);
            throw e;
        } catch (RuntimeException e) {
            IngestionException wrapped = new IngestionException(
                    "Unexpected error reading resource: " + describe(request.resource()), e);
            emitFailed(correlationId, describe(request.resource()), wrapped);
            throw wrapped;
        }

        List<String> sourceIds = new ArrayList<>();
        List<String> allChunkIds = new ArrayList<>();
        int totalChunks = 0;

        for (Document doc : documents) {
            Document prepared = normalizer.normalize(applyMetadataOverrides(doc, request));

            String preparedText = prepared.getText();
            events.publish(new IngestionEvent.DocumentRead(
                    prepared.getId(), describe(request.resource()),
                    preparedText == null ? 0 : preparedText.length(),
                    Instant.now(), correlationId));

            ChunkingStrategy strategy = selectStrategy(prepared, request);
            List<Document> chunks = strategy.chunk(prepared);

            events.publish(new IngestionEvent.DocumentChunked(
                    prepared.getId(), chunks.size(), strategy.name(),
                    Instant.now(), correlationId));

            for (ChunkSink sink : sinks) {
                try {
                    sink.accept(chunks);
                } catch (RuntimeException e) {
                    IngestionException wrapped = new IngestionException(
                            "ChunkSink '" + sink.name() + "' failed", e);
                    emitFailed(correlationId, prepared.getId(), wrapped);
                    throw wrapped;
                }
            }

            for (Document chunk : chunks) {
                allChunkIds.add(chunk.getId());
            }
            sourceIds.add(prepared.getId());
            totalChunks += chunks.size();

            events.publish(new IngestionEvent.IngestionCompleted(
                    prepared.getId(), chunks.size(), Instant.now(), correlationId));
        }

        return new IngestionResult(
                sourceIds, totalChunks, allChunkIds,
                System.currentTimeMillis() - started, Map.of());
    }

    private ChunkingStrategy selectStrategy(Document document, IngestionRequest request) {
        if (request.strategyName() != null) {
            return strategies.stream()
                    .filter(s -> s.name().equals(request.strategyName()))
                    .findFirst()
                    .orElseThrow(() -> new IngestionException(
                            "Unknown chunking strategy: " + request.strategyName()));
        }
        return strategies.stream()
                .filter(s -> s.supports(document))
                .findFirst()
                .orElse(defaultStrategy);
    }

    private Document applyMetadataOverrides(Document doc, IngestionRequest request) {
        if (request.metadataOverrides().isEmpty()) {
            return doc;
        }
        Map<String, Object> merged = new HashMap<>(doc.getMetadata());
        merged.putAll(request.metadataOverrides());
        return new Document(doc.getId(), Objects.requireNonNullElse(doc.getText(), ""), merged);
    }

    private void emitFailed(String correlationId, String documentId, Throwable error) {
        events.publish(new IngestionEvent.IngestionFailed(
                documentId,
                error.getMessage(),
                error.getClass().getSimpleName(),
                Instant.now(),
                correlationId));
    }

    private static String describe(Resource resource) {
        if (resource == null) {
            return "<null>";
        }
        String filename = resource.getFilename();
        return filename != null ? filename : resource.getDescription();
    }
}
