package kr.co.mz.agenticai.core.common.spi;

import java.util.List;
import java.util.Map;
import org.springframework.ai.document.Document;

/**
 * Routes a query to one or more retrieval backends (vector / BM25 / graph)
 * and fuses their results. Implementations own the fusion strategy.
 */
public interface RetrieverRouter {

    List<Document> retrieve(Query query);

    record Query(
            String text,
            int topK,
            Map<String, Object> metadataFilters,
            Map<String, Object> attributes) {

        public Query {
            metadataFilters = metadataFilters == null ? Map.of() : Map.copyOf(metadataFilters);
            attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
        }

        public static Query of(String text, int topK) {
            return new Query(text, topK, Map.of(), Map.of());
        }
    }
}
