package kr.co.mz.agenticai.core.retrieval;

import java.util.List;
import kr.co.mz.agenticai.core.common.spi.RetrievalEvaluator.Decision;
import org.springframework.ai.document.Document;

/**
 * Combines the retrieved documents with the {@link Decision} from the
 * {@link kr.co.mz.agenticai.core.common.spi.RetrievalEvaluator}.
 *
 * <p>Callers that need only the documents can continue using
 * {@link kr.co.mz.agenticai.core.common.spi.RetrieverRouter#retrieve} directly.
 * Callers that want to branch on the evaluation result should invoke
 * {@link kr.co.mz.agenticai.core.common.spi.RetrieverRouter#retrieveWithDecision}
 * and inspect this record.
 */
public record RetrievalOutcome(List<Document> documents, Decision decision) {

    public RetrievalOutcome {
        documents = documents == null ? List.of() : List.copyOf(documents);
    }
}
