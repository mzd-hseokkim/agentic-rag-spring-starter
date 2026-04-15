package kr.co.mz.agenticai.core.retrieval.fusion;

import java.util.List;
import org.springframework.ai.document.Document;

/**
 * Merges the ranked lists produced by multiple retrievers (vector / BM25 /
 * graph / ...) into a single ranking of size {@code topK}.
 *
 * <p>Implementations must be deterministic given the same input lists.
 */
public interface ResultFusion {

    /** @param rankedLists each inner list is a retriever's ranking, best-first. */
    List<Document> fuse(List<List<Document>> rankedLists, int topK);
}
