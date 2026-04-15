package kr.co.mz.agenticai.core.ingestion.sink;

import java.util.List;
import java.util.Objects;
import kr.co.mz.agenticai.core.common.exception.IngestionException;
import kr.co.mz.agenticai.core.common.spi.ChunkSink;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;

/**
 * {@link ChunkSink} that persists chunks to a Spring AI
 * {@link VectorStore}. The store is responsible for embedding (via its
 * configured {@code EmbeddingModel}) and storage; this sink just delegates.
 */
public final class VectorStoreChunkSink implements ChunkSink {

    private final VectorStore vectorStore;

    public VectorStoreChunkSink(VectorStore vectorStore) {
        this.vectorStore = Objects.requireNonNull(vectorStore, "vectorStore");
    }

    @Override
    public void accept(List<Document> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return;
        }
        try {
            vectorStore.add(chunks);
        } catch (RuntimeException e) {
            throw new IngestionException("VectorStore.add failed for " + chunks.size() + " chunks", e);
        }
    }

    @Override
    public String name() {
        return "vector-store";
    }
}
