package kr.co.mz.agenticai.core.retrieval.fusion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

class WeightedSumFusionTest {

    @Test
    void rejectsEmptyOrNegativeWeights() {
        List<Double> empty = List.of();
        List<Double> negative = List.of(-0.1);
        assertThatThrownBy(() -> new WeightedSumFusion(empty))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new WeightedSumFusion(negative))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void failsWhenListCountDiffersFromWeights() {
        WeightedSumFusion fusion = new WeightedSumFusion(List.of(0.5, 0.5));
        List<Document> a = List.of(new Document("a", "A", Map.of()));
        List<List<Document>> onlyOneList = List.of(a);

        assertThatThrownBy(() -> fusion.fuse(onlyOneList, 1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void weightShiftsRanking() {
        Document a = new Document("a", "A", Map.of());
        Document b = new Document("b", "B", Map.of());

        // List 1 ranks A above B. List 2 ranks B above A. Equal weights → A wins (was first in list 1).
        List<Document> l1 = List.of(a, b);
        List<Document> l2 = List.of(b, a);

        List<Document> equal = new WeightedSumFusion(List.of(1.0, 1.0)).fuse(List.of(l1, l2), 2);
        assertThat(equal).extracting(Document::getId).containsExactly("a", "b");

        // Heavier weight on l2 flips the ranking.
        List<Document> biased = new WeightedSumFusion(List.of(0.1, 0.9)).fuse(List.of(l1, l2), 2);
        assertThat(biased).extracting(Document::getId).containsExactly("b", "a");
    }
}
