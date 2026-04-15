package kr.co.mz.agenticai.core.retrieval.bm25;

import java.util.List;
import java.util.Objects;
import kr.co.mz.agenticai.core.common.spi.ChunkSink;
import org.springframework.ai.document.Document;

/**
 * {@link ChunkSink} that forwards chunks to a {@link LuceneBm25Index}. Lets
 * the ingestion pipeline keep the in-memory BM25 index in sync with the
 * vector store.
 */
public final class Bm25ChunkSink implements ChunkSink {

    private final LuceneBm25Index index;

    public Bm25ChunkSink(LuceneBm25Index index) {
        this.index = Objects.requireNonNull(index, "index");
    }

    @Override
    public void accept(List<Document> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return;
        }
        index.addDocuments(chunks);
    }

    @Override
    public String name() {
        return "bm25";
    }
}
