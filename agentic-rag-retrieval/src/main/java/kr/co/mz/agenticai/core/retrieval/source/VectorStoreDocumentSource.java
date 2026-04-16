package kr.co.mz.agenticai.core.retrieval.source;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import kr.co.mz.agenticai.core.common.spi.DocumentSource;
import kr.co.mz.agenticai.core.retrieval.RetrievalMetadata;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

/**
 * {@link DocumentSource} backed by a Spring AI {@link VectorStore}.
 *
 * <p>Each hit is enriched with {@link RetrievalMetadata#VECTOR_SCORE} and
 * {@link RetrievalMetadata#RANK} metadata so downstream fusion / rerank can
 * reason about origin and position.
 */
public final class VectorStoreDocumentSource implements DocumentSource {

    public static final String CANONICAL_NAME = "vector";

    private final VectorStore vectorStore;

    public VectorStoreDocumentSource(VectorStore vectorStore) {
        this.vectorStore = Objects.requireNonNull(vectorStore, "vectorStore");
    }

    @Override
    public String name() {
        return CANONICAL_NAME;
    }

    @Override
    public List<Document> search(String query, int topK, Map<String, Object> filters) {
        SearchRequest request = SearchRequest.builder().query(query).topK(topK).build();
        List<Document> hits = vectorStore.similaritySearch(request);
        if (hits == null || hits.isEmpty()) {
            return List.of();
        }

        List<Document> enriched = new java.util.ArrayList<>(hits.size());
        for (int i = 0; i < hits.size(); i++) {
            Document d = hits.get(i);
            Map<String, Object> metadata = new HashMap<>(d.getMetadata());
            metadata.put(RetrievalMetadata.RANK, i);
            // Spring AI vector stores generally don't surface a raw similarity
            // on the returned document; leave VECTOR_SCORE set only when
            // already present (some stores include it under "distance").
            metadata.computeIfAbsent(RetrievalMetadata.VECTOR_SCORE, k ->
                    d.getMetadata().getOrDefault("distance", null));
            enriched.add(new Document(d.getId(), Objects.requireNonNullElse(d.getText(), ""), metadata));
        }
        return enriched;
    }
}
