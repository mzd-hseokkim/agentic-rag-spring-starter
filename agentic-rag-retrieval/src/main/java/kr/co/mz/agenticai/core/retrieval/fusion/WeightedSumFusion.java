package kr.co.mz.agenticai.core.retrieval.fusion;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import kr.co.mz.agenticai.core.retrieval.RetrievalMetadata;
import org.springframework.ai.document.Document;

/**
 * Weighted-sum fusion using rank-based normalization (avoids mixing raw
 * BM25 and vector-similarity scales).
 *
 * <p>For a document at zero-based rank {@code r} in a list of size {@code n},
 * the contribution is {@code weight_i * (1 - r / n)}. Per-list contributions
 * are summed; the top-{@code topK} ids win.
 *
 * <p>If a score-based fusion is required, implement a custom
 * {@link ResultFusion} that reads raw scores from metadata keys under
 * {@link RetrievalMetadata}.
 */
public final class WeightedSumFusion implements ResultFusion {

    private final List<Double> weights;

    public WeightedSumFusion(List<Double> weights) {
        if (weights == null || weights.isEmpty()) {
            throw new IllegalArgumentException("weights must not be empty");
        }
        for (Double w : weights) {
            if (w == null || w < 0.0) {
                throw new IllegalArgumentException("weights must be non-negative, got " + weights);
            }
        }
        this.weights = List.copyOf(weights);
    }

    @Override
    public List<Document> fuse(List<List<Document>> rankedLists, int topK) {
        if (rankedLists == null || rankedLists.isEmpty() || topK <= 0) {
            return List.of();
        }
        if (rankedLists.size() != weights.size()) {
            throw new IllegalArgumentException(
                    "Expected " + weights.size() + " ranked lists, got " + rankedLists.size());
        }

        Map<String, Double> scoreById = new HashMap<>();
        Map<String, Document> docById = new HashMap<>();

        for (int i = 0; i < rankedLists.size(); i++) {
            List<Document> list = rankedLists.get(i);
            if (list == null || list.isEmpty()) {
                continue;
            }
            double weight = weights.get(i);
            int n = list.size();
            for (int r = 0; r < n; r++) {
                Document doc = list.get(r);
                if (doc == null) {
                    continue;
                }
                double normalized = 1.0 - ((double) r / n);
                scoreById.merge(doc.getId(), weight * normalized, Double::sum);
                docById.putIfAbsent(doc.getId(), doc);
            }
        }

        return scoreById.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue(Comparator.reverseOrder())
                        .thenComparing(Map.Entry.comparingByKey()))
                .limit(topK)
                .map(e -> withFusedScore(docById.get(e.getKey()), e.getValue()))
                .toList();
    }

    private static Document withFusedScore(Document doc, double score) {
        Map<String, Object> metadata = new HashMap<>(doc.getMetadata());
        metadata.put(RetrievalMetadata.FUSED_SCORE, score);
        return new Document(doc.getId(), doc.getText(), metadata);
    }
}
