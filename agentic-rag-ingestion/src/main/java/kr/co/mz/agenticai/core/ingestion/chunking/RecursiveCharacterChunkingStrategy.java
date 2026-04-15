package kr.co.mz.agenticai.core.ingestion.chunking;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import kr.co.mz.agenticai.core.common.spi.ChunkingStrategy;
import org.springframework.ai.document.Document;

/**
 * Splits text recursively by a prioritized list of separators, keeping
 * semantic boundaries intact whenever possible.
 *
 * <p>The algorithm tries the first separator; if a resulting segment still
 * exceeds {@code maxChars}, it recurses with the next separator. The last
 * separator is the empty string, which forces a hard character split and
 * guarantees termination.
 */
public final class RecursiveCharacterChunkingStrategy implements ChunkingStrategy {

    public static final String NAME = "recursive-character";

    private static final List<String> DEFAULT_SEPARATORS = List.of("\n\n", "\n", ". ", " ", "");

    /**
     * Separator preset tuned for Korean prose. Splits on paragraph and line
     * breaks first, then on common Korean sentence endings ("다.", "요.",
     * "까?", "습니다.") before falling back to Western punctuation and
     * whitespace.
     */
    public static final List<String> KOREAN_SEPARATORS = List.of(
            "\n\n", "\n", "습니다. ", "다. ", "요. ", "까? ", ". ", "。", "? ", "! ", " ", "");

    private final int maxChars;
    private final List<String> separators;

    public RecursiveCharacterChunkingStrategy(int maxChars, List<String> separators) {
        if (maxChars <= 0) {
            throw new IllegalArgumentException("maxChars must be > 0, was " + maxChars);
        }
        if (separators == null || separators.isEmpty()) {
            throw new IllegalArgumentException("separators must not be empty");
        }
        this.maxChars = maxChars;
        this.separators = List.copyOf(separators);
    }

    public RecursiveCharacterChunkingStrategy() {
        this(1000, DEFAULT_SEPARATORS);
    }

    /** Instance preconfigured with {@link #KOREAN_SEPARATORS}. */
    public static RecursiveCharacterChunkingStrategy forKorean(int maxChars) {
        return new RecursiveCharacterChunkingStrategy(maxChars, KOREAN_SEPARATORS);
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public List<Document> chunk(Document document) {
        String text = document.getText();
        if (text == null || text.isEmpty()) {
            return List.of();
        }

        List<String> pieces = splitRecursively(text, separators);
        List<Document> chunks = new ArrayList<>(pieces.size());
        int index = 0;
        for (String piece : pieces) {
            if (piece.isEmpty()) {
                continue;
            }
            chunks.add(newChunk(document, piece, index++));
        }
        return chunks;
    }

    private List<String> splitRecursively(String text, List<String> seps) {
        if (text.length() <= maxChars) {
            return List.of(text);
        }
        if (seps.isEmpty()) {
            return splitByFixedSize(text);
        }

        String sep = seps.get(0);
        List<String> rest = seps.subList(1, seps.size());
        List<String> parts = sep.isEmpty() ? splitByFixedSize(text) : splitKeepingSeparator(text, sep);

        List<String> out = new ArrayList<>();
        StringBuilder buffer = new StringBuilder();
        for (String part : parts) {
            if (part.length() > maxChars) {
                if (!buffer.isEmpty()) {
                    out.add(buffer.toString());
                    buffer.setLength(0);
                }
                out.addAll(splitRecursively(part, rest));
            } else if (buffer.length() + part.length() <= maxChars) {
                buffer.append(part);
            } else {
                out.add(buffer.toString());
                buffer.setLength(0);
                buffer.append(part);
            }
        }
        if (!buffer.isEmpty()) {
            out.add(buffer.toString());
        }
        return out;
    }

    private List<String> splitKeepingSeparator(String text, String sep) {
        List<String> parts = new ArrayList<>();
        int from = 0;
        int idx;
        while ((idx = text.indexOf(sep, from)) >= 0) {
            parts.add(text.substring(from, idx + sep.length()));
            from = idx + sep.length();
        }
        if (from < text.length()) {
            parts.add(text.substring(from));
        }
        return parts;
    }

    private List<String> splitByFixedSize(String text) {
        List<String> parts = new ArrayList<>();
        for (int i = 0; i < text.length(); i += maxChars) {
            parts.add(text.substring(i, Math.min(i + maxChars, text.length())));
        }
        return parts;
    }

    private Document newChunk(Document parent, String text, int index) {
        Map<String, Object> metadata = new HashMap<>(parent.getMetadata());
        metadata.put(ChunkMetadata.PARENT_DOCUMENT_ID, parent.getId());
        metadata.put(ChunkMetadata.CHUNK_INDEX, index);
        metadata.put(ChunkMetadata.CHUNK_STRATEGY, NAME);
        return new Document(text, metadata);
    }
}
