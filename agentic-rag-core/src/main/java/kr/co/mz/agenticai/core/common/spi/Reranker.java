package kr.co.mz.agenticai.core.common.spi;

import java.util.List;
import org.springframework.ai.document.Document;

/**
 * Reorders retrieved candidates by relevance to the query. The default
 * registered implementation is a no-op pass-through; register a bean of
 * this type to plug in a cross-encoder or any other model.
 */
public interface Reranker {

    List<Document> rerank(String query, List<Document> candidates, int topK);
}
