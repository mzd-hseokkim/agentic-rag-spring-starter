package kr.co.mz.agenticai.core.common.spi;

import java.util.List;
import java.util.Map;
import org.springframework.ai.document.Document;

/**
 * A single retrieval backend (vector store, BM25 index, knowledge graph, ...).
 * Multiple {@link DocumentSource} beans are collected by the default
 * {@link RetrieverRouter} and their results fused. Register a new bean to
 * add a backend without replacing the router.
 */
public interface DocumentSource {

    /** Stable identifier used in events and metadata (e.g. {@code "vector"}, {@code "bm25"}). */
    String name();

    /**
     * Return up to {@code topK} candidates ordered best-first.
     *
     * @param query the (possibly already-transformed) query text
     * @param topK  desired upper bound; implementations may return fewer
     * @param filters optional metadata filters; implementations may ignore
     */
    List<Document> search(String query, int topK, Map<String, Object> filters);
}
