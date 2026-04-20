package kr.co.mz.agenticai.core.retrieval.rerank;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

class CrossEncoderRerankerTest {

    private static final List<Document> CANDIDATES = List.of(
            new Document("a", "A", Map.of()),
            new Document("b", "B", Map.of()),
            new Document("c", "C", Map.of()));

    @Test
    void reordersByDescendingScoreAndTruncates() {
        CrossEncoderReranker reranker = new CrossEncoderReranker(
                (q, docs) -> List.of(0.2f, 0.9f, 0.5f));

        assertThat(reranker.rerank("q", CANDIDATES, 2))
                .extracting(Document::getId)
                .containsExactly("b", "c");
    }

    @Test
    void returnsEmptyForEmptyOrZeroTopK() {
        CrossEncoderReranker reranker = new CrossEncoderReranker(
                (q, docs) -> List.of(1.0f));

        assertThat(reranker.rerank("q", List.of(), 5)).isEmpty();
        assertThat(reranker.rerank("q", null, 5)).isEmpty();
        assertThat(reranker.rerank("q", CANDIDATES, 0)).isEmpty();
    }

    @Test
    void throwsWhenScoreCountMismatch() {
        CrossEncoderReranker reranker = new CrossEncoderReranker(
                (q, docs) -> List.of(0.5f));

        assertThatThrownBy(() -> reranker.rerank("q", CANDIDATES, 3))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("exactly one score per document");
    }
}
