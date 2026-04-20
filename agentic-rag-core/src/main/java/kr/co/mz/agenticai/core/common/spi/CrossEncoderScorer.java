package kr.co.mz.agenticai.core.common.spi;

import java.util.List;
import org.springframework.ai.document.Document;

/**
 * Scores (query, document) pairs jointly for reranking. Implementations
 * typically wrap a cross-encoder model (HuggingFace TEI rerank endpoint,
 * ONNX-hosted BGE-reranker, LLM-as-judge, ...). Register a single bean of
 * this type to activate {@link CrossEncoderScorer}-backed reranking.
 *
 * <p>Higher scores indicate greater relevance. Implementations must return
 * exactly one score per input document, in the same order.
 */
@FunctionalInterface
public interface CrossEncoderScorer {

    List<Float> score(String query, List<Document> documents);
}
