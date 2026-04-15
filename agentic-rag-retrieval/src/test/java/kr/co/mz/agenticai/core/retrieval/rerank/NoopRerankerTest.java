package kr.co.mz.agenticai.core.retrieval.rerank;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

class NoopRerankerTest {

    private final NoopReranker reranker = new NoopReranker();

    @Test
    void preservesOrderAndTruncatesToTopK() {
        List<Document> candidates = List.of(
                new Document("a", "A", Map.of()),
                new Document("b", "B", Map.of()),
                new Document("c", "C", Map.of()));

        assertThat(reranker.rerank("q", candidates, 2))
                .extracting(Document::getId)
                .containsExactly("a", "b");
    }

    @Test
    void returnsAllWhenCandidatesAreFewer() {
        List<Document> candidates = List.of(new Document("a", "A", Map.of()));

        assertThat(reranker.rerank("q", candidates, 5)).hasSize(1);
    }

    @Test
    void emptyInputsYieldEmpty() {
        assertThat(reranker.rerank("q", List.of(), 5)).isEmpty();
        assertThat(reranker.rerank("q", null, 5)).isEmpty();
        assertThat(reranker.rerank("q", List.of(new Document("a", "A", Map.of())), 0)).isEmpty();
    }
}
