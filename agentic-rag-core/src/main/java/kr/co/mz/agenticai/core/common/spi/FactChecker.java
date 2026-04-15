package kr.co.mz.agenticai.core.common.spi;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import kr.co.mz.agenticai.core.common.Citation;
import org.springframework.ai.document.Document;

/**
 * Verifies that an LLM-generated answer is grounded in the retrieved source
 * chunks. Implementations may be LLM-based, rule-based, embedding-similarity
 * based, or any combination.
 */
public interface FactChecker {

    FactCheckResult check(FactCheckRequest request);

    /** Input to a fact-check call. */
    record FactCheckRequest(
            String answer,
            List<Document> sources,
            String originalQuery,
            Map<String, Object> attributes) {

        public FactCheckRequest {
            Objects.requireNonNull(answer, "answer");
            sources = sources == null ? List.of() : List.copyOf(sources);
            attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
        }
    }

    /**
     * Result of a fact-check.
     *
     * @param grounded   {@code true} when the answer is sufficiently supported
     * @param confidence implementation-specific score in {@code [0, 1]}
     * @param citations  references to the chunks that support (or refute) the answer
     * @param reason     human-readable rationale
     */
    record FactCheckResult(
            boolean grounded,
            double confidence,
            List<Citation> citations,
            String reason,
            Map<String, Object> attributes) {

        public FactCheckResult {
            citations = citations == null ? List.of() : List.copyOf(citations);
            attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
        }
    }
}
