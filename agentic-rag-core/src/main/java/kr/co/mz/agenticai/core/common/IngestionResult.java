package kr.co.mz.agenticai.core.common;

import java.util.List;
import java.util.Map;

/** Outcome of a single {@link IngestionPipeline#ingest} invocation. */
public record IngestionResult(
        List<String> sourceDocumentIds,
        int totalChunks,
        List<String> chunkIds,
        long elapsedMillis,
        Map<String, Object> attributes) {

    public IngestionResult {
        sourceDocumentIds = sourceDocumentIds == null ? List.of() : List.copyOf(sourceDocumentIds);
        chunkIds = chunkIds == null ? List.of() : List.copyOf(chunkIds);
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }
}
