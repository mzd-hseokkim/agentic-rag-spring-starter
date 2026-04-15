package kr.co.mz.agenticai.core.common;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.ai.document.Document;

/**
 * Reference to the source chunk that supports part of a generated answer.
 *
 * <p>{@code chunkIndex}, {@code charStart}, {@code charEnd}, and {@code score}
 * are optional and may be {@code null} when unavailable.
 */
public record Citation(
        String documentId,
        Integer chunkIndex,
        Integer charStart,
        Integer charEnd,
        Double score,
        Map<String, Object> metadata) {

    public Citation {
        Objects.requireNonNull(documentId, "documentId must not be null");
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    public static Citation of(String documentId) {
        return new Citation(documentId, null, null, null, null, Map.of());
    }

    /**
     * Build a {@link Citation} from a retrieved chunk. Prefers the parent
     * document id (set by the chunking strategy) over the chunk id, and
     * propagates fused score and {@code source} metadata when available.
     */
    public static Citation fromDocument(Document chunk) {
        Map<String, Object> srcMeta = chunk.getMetadata();
        String parentId = (String) srcMeta.getOrDefault(RagMetadataKeys.PARENT_DOCUMENT_ID, chunk.getId());
        Object fusedScore = srcMeta.get(RagMetadataKeys.FUSED_SCORE);
        Double score = (fusedScore instanceof Number n) ? n.doubleValue() : null;
        Map<String, Object> citationMeta = new HashMap<>();
        citationMeta.put("chunkId", chunk.getId());
        Object source = srcMeta.get("source");
        if (source != null) {
            citationMeta.put("source", source);
        }
        return new Citation(parentId, null, null, null, score, citationMeta);
    }

    /** Convenience: convert a list of retrieved chunks to citations. */
    public static List<Citation> fromDocuments(List<Document> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return List.of();
        }
        List<Citation> out = new ArrayList<>(chunks.size());
        for (Document c : chunks) {
            out.add(fromDocument(c));
        }
        return out;
    }
}
