package kr.co.mz.agenticai.core.retrieval.fusion;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import kr.co.mz.agenticai.core.retrieval.RetrievalMetadata;
import org.springframework.ai.document.Document;

/**
 * Reciprocal Rank Fusion (Cormack, Clarke, Büttcher 2009).
 *
 * <p>{@code RRF(d) = Σ_i 1 / (k + rank_i(d))}, where rank starts at 1. The
 * smoothing constant {@code k} defaults to 60 and dampens the dominance of
 * the top-ranked document in any single list.
 */
public final class ReciprocalRankFusion implements ResultFusion {

    private static final int DEFAULT_K = 60;

    private final int k;

    public ReciprocalRankFusion() {
        this(DEFAULT_K);
    }

    public ReciprocalRankFusion(int k) {
        if (k < 1) {
            throw new IllegalArgumentException("k must be >= 1, was " + k);
        }
        this.k = k;
    }

    @Override
    public List<Document> fuse(List<List<Document>> rankedLists, int topK) {
        if (rankedLists == null || rankedLists.isEmpty() || topK <= 0) {
            return List.of();
        }

        Map<String, Double> scoreById = new HashMap<>();
        Map<String, Document> docById = new HashMap<>();

        for (List<Document> list : rankedLists) {
            if (list == null) {
                continue;
            }
            int rank = 1;
            for (Document doc : list) {
                if (doc == null) {
                    continue;
                }
                double contribution = 1.0 / (k + rank);
                scoreById.merge(doc.getId(), contribution, Double::sum);
                docById.putIfAbsent(doc.getId(), doc);
                rank++;
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
        return new Document(doc.getId(), java.util.Objects.requireNonNullElse(doc.getText(), ""), metadata);
    }
}
