package kr.co.mz.agenticai.core.common.spi;

import java.util.List;
import org.springframework.ai.document.Document;

/**
 * Terminal destination for chunks produced by the ingestion pipeline.
 * Built-in sinks persist chunks to a Spring AI {@code VectorStore} and to
 * the in-memory Lucene BM25 index. Register additional beans (graph DB,
 * audit log, S3 archive, ...) to fan chunks out to more destinations.
 *
 * <p>The pipeline calls every registered sink for every batch of chunks.
 * Sinks should be idempotent when possible; failures propagate and abort
 * the ingestion of the current source document.
 */
@FunctionalInterface
public interface ChunkSink {

    void accept(List<Document> chunks);

    /** Human-readable name for observability; defaults to the class simple name. */
    default String name() {
        return getClass().getSimpleName();
    }
}
