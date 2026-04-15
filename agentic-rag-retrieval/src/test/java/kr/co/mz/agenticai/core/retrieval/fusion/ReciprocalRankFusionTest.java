package kr.co.mz.agenticai.core.retrieval.fusion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import kr.co.mz.agenticai.core.retrieval.RetrievalMetadata;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

class ReciprocalRankFusionTest {

    @Test
    void rejectsNonPositiveK() {
        assertThatThrownBy(() -> new ReciprocalRankFusion(0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void documentInBothListsRanksHigherThanSingletons() {
        Document a = new Document("a-id", "A", Map.of());
        Document b = new Document("b-id", "B", Map.of());
        Document c = new Document("c-id", "C", Map.of());

        List<Document> vector = List.of(a, b);
        List<Document> bm25 = List.of(c, a);

        List<Document> fused = new ReciprocalRankFusion().fuse(List.of(vector, bm25), 3);

        // a appears in both lists → highest fused score. c at rank 1 in bm25 beats b at rank 2 in vector.
        assertThat(fused).extracting(Document::getId).containsExactly("a-id", "c-id", "b-id");
        assertThat(fused.get(0).getMetadata()).containsKey(RetrievalMetadata.FUSED_SCORE);
    }

    @Test
    void emptyInputYieldsEmptyOutput() {
        assertThat(new ReciprocalRankFusion().fuse(List.of(), 5)).isEmpty();
        assertThat(new ReciprocalRankFusion().fuse(List.of(List.of()), 5)).isEmpty();
    }

    @Test
    void respectsTopK() {
        Document a = new Document("a", "A", Map.of());
        Document b = new Document("b", "B", Map.of());
        Document c = new Document("c", "C", Map.of());

        List<Document> fused = new ReciprocalRankFusion().fuse(List.of(List.of(a, b, c)), 2);

        assertThat(fused).hasSize(2);
    }
}
