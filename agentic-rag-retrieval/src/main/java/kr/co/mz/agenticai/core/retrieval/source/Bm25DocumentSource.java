package kr.co.mz.agenticai.core.retrieval.source;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import kr.co.mz.agenticai.core.common.spi.DocumentSource;
import kr.co.mz.agenticai.core.retrieval.bm25.LuceneBm25Index;
import org.springframework.ai.document.Document;

/** {@link DocumentSource} backed by the in-memory Lucene BM25 index. */
public final class Bm25DocumentSource implements DocumentSource {

    public static final String NAME = "bm25";

    private final LuceneBm25Index index;

    public Bm25DocumentSource(LuceneBm25Index index) {
        this.index = Objects.requireNonNull(index, "index");
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public List<Document> search(String query, int topK, Map<String, Object> filters) {
        // BM25 index does not apply metadata filters; the router can post-filter if needed.
        return index.search(query, topK);
    }
}
