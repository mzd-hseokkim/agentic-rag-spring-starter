package kr.co.mz.agenticai.core.ingestion.chunking;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import kr.co.mz.agenticai.core.common.exception.IngestionException;
import kr.co.mz.agenticai.core.common.spi.ChunkingStrategy;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;

/**
 * Embedding-driven chunking strategy (Greg Kamradt-style).
 *
 * <p>Algorithm:
 * <ol>
 *   <li>Split text into sentences (handles English and Korean endings).</li>
 *   <li>Embed every sentence in a single batch call.</li>
 *   <li>Compute cosine distance between adjacent sentence embeddings.</li>
 *   <li>Mark a breakpoint wherever the distance exceeds the configured
 *       percentile threshold.</li>
 *   <li>Group consecutive sentences between breakpoints into chunks, with
 *       an additional hard ceiling of {@code maxChunkChars}.</li>
 * </ol>
 *
 * <p>Because it calls the embedding model, this strategy is relatively
 * expensive; batch-embed keeps the cost to one round trip per document.
 */
public final class SemanticChunkingStrategy implements ChunkingStrategy {

    public static final String CANONICAL_NAME = "semantic";

    /** Matches sentence boundaries: punctuation + whitespace, or Korean endings + Hangul start. */
    private static final Pattern SENTENCE_BOUNDARY =
            Pattern.compile("(?<=[.!?。])(?:\\s+|(?=[가-힣]))");

    private final EmbeddingModel embeddingModel;
    private final double thresholdPercentile;
    private final int maxChunkChars;

    public SemanticChunkingStrategy(EmbeddingModel embeddingModel) {
        this(embeddingModel, 0.95, 2000);
    }

    public SemanticChunkingStrategy(
            EmbeddingModel embeddingModel, double thresholdPercentile, int maxChunkChars) {
        this.embeddingModel = Objects.requireNonNull(embeddingModel, "embeddingModel");
        if (thresholdPercentile < 0.0 || thresholdPercentile > 1.0) {
            throw new IllegalArgumentException(
                    "thresholdPercentile must be in [0, 1], was " + thresholdPercentile);
        }
        if (maxChunkChars < 0) {
            throw new IllegalArgumentException("maxChunkChars must be >= 0, was " + maxChunkChars);
        }
        this.thresholdPercentile = thresholdPercentile;
        this.maxChunkChars = maxChunkChars;
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

        List<String> sentences = splitSentences(text);
        if (sentences.size() <= 1) {
            return List.of(newChunk(document, text, 0));
        }

        List<float[]> embeddings;
        try {
            embeddings = embeddingModel.embed(sentences);
        } catch (RuntimeException e) {
            throw new IngestionException(
                    "EmbeddingModel failed for " + sentences.size() + " sentences", e);
        }
        if (embeddings.size() != sentences.size()) {
            throw new IngestionException("EmbeddingModel returned "
                    + embeddings.size() + " vectors for " + sentences.size() + " sentences");
        }

        double[] distances = new double[sentences.size() - 1];
        for (int i = 0; i < distances.length; i++) {
            distances[i] = cosineDistance(embeddings.get(i), embeddings.get(i + 1));
        }
        double threshold = percentile(distances, thresholdPercentile);

        List<Document> chunks = new ArrayList<>();
        StringBuilder buffer = new StringBuilder(sentences.get(0));
        int chunkIndex = 0;
        for (int i = 0; i < distances.length; i++) {
            String next = sentences.get(i + 1);
            boolean semanticBreak = distances[i] > threshold;
            boolean overLimit = maxChunkChars > 0
                    && buffer.length() + 1 + next.length() > maxChunkChars;
            if (semanticBreak || overLimit) {
                chunks.add(newChunk(document, buffer.toString(), chunkIndex++));
                buffer.setLength(0);
                buffer.append(next);
            } else {
                buffer.append(' ').append(next);
            }
        }
        if (!buffer.isEmpty()) {
            chunks.add(newChunk(document, buffer.toString(), chunkIndex));
        }
        return chunks;
    }

    private static List<String> splitSentences(String text) {
        String[] parts = SENTENCE_BOUNDARY.split(text);
        return Arrays.stream(parts).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    private static double cosineDistance(float[] a, float[] b) {
        if (a.length != b.length) {
            throw new IllegalArgumentException(
                    "Embedding dimension mismatch: " + a.length + " vs " + b.length);
        }
        double dot = 0.0;
        double normA = 0.0;
        double normB = 0.0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        if (normA == 0.0 || normB == 0.0) {
            return 1.0;
        }
        double sim = dot / (Math.sqrt(normA) * Math.sqrt(normB));
        return 1.0 - sim;
    }

    /** Linear-interpolation percentile (matches NumPy's default for small arrays). */
    private static double percentile(double[] values, double p) {
        if (values.length == 0) {
            return 0.0;
        }
        double[] sorted = values.clone();
        Arrays.sort(sorted);
        if (sorted.length == 1) {
            return sorted[0];
        }
        double rank = p * (sorted.length - 1);
        int lower = (int) Math.floor(rank);
        int upper = (int) Math.ceil(rank);
        double frac = rank - lower;
        return sorted[lower] + frac * (sorted[upper] - sorted[lower]);
    }

    private Document newChunk(Document parent, String text, int index) {
        Map<String, Object> metadata = new HashMap<>(parent.getMetadata());
        metadata.put(ChunkMetadata.PARENT_DOCUMENT_ID, parent.getId());
        metadata.put(ChunkMetadata.CHUNK_INDEX, index);
        metadata.put(ChunkMetadata.CHUNK_STRATEGY, CANONICAL_NAME);
        return new Document(text, metadata);
    }
}
