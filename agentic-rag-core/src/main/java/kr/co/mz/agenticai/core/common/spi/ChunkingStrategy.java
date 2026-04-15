package kr.co.mz.agenticai.core.common.spi;

import java.util.List;
import org.springframework.ai.document.Document;

/**
 * Splits a parsed {@link Document} into smaller chunks suitable for
 * embedding and retrieval. Built-in strategies include fixed-size,
 * recursive, markdown-heading, and semantic (embedding-driven) chunking.
 */
public interface ChunkingStrategy {

    /** A human-readable name (e.g. {@code "semantic"}) used for event payloads and config. */
    String name();

    /**
     * Return {@code true} when this strategy should handle the given document.
     * The default implementation accepts everything; content-type aware
     * strategies should override.
     */
    default boolean supports(Document document) {
        return true;
    }

    List<Document> chunk(Document document);
}
