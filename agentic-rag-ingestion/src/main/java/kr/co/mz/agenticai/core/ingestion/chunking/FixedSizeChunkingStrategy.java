package kr.co.mz.agenticai.core.ingestion.chunking;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import kr.co.mz.agenticai.core.common.spi.ChunkingStrategy;
import org.springframework.ai.document.Document;

/**
 * Splits a document into fixed-size character windows with configurable
 * overlap between consecutive chunks.
 */
public final class FixedSizeChunkingStrategy implements ChunkingStrategy {

    public static final String CANONICAL_NAME = "fixed-size";

    private final int maxChars;
    private final int overlap;

    public FixedSizeChunkingStrategy(int maxChars, int overlap) {
        if (maxChars <= 0) {
            throw new IllegalArgumentException("maxChars must be > 0, was " + maxChars);
        }
        if (overlap < 0 || overlap >= maxChars) {
            throw new IllegalArgumentException(
                    "overlap must be in [0, maxChars), was " + overlap + " for maxChars " + maxChars);
        }
        this.maxChars = maxChars;
        this.overlap = overlap;
    }

    public FixedSizeChunkingStrategy() {
        this(1000, 200);
    }

    @Override
    public String name() {
        return CANONICAL_NAME;
    }

    @Override
    public List<Document> chunk(Document document) {
        String text = document.getText();
        if (text == null || text.isEmpty()) {
            return List.of();
        }

        List<Document> chunks = new ArrayList<>();
        int step = maxChars - overlap;
        int index = 0;
        for (int start = 0; start < text.length(); start += step) {
            int end = Math.min(start + maxChars, text.length());
            chunks.add(newChunk(document, text.substring(start, end), index++));
            if (end == text.length()) {
                break;
            }
        }
        return chunks;
    }

    private Document newChunk(Document parent, String text, int index) {
        Map<String, Object> metadata = new HashMap<>(parent.getMetadata());
        metadata.put(ChunkMetadata.PARENT_DOCUMENT_ID, parent.getId());
        metadata.put(ChunkMetadata.CHUNK_INDEX, index);
        metadata.put(ChunkMetadata.CHUNK_STRATEGY, CANONICAL_NAME);
        return new Document(text, metadata);
    }
}
