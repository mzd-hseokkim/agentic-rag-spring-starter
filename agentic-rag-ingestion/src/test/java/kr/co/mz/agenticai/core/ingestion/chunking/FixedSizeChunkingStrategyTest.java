package kr.co.mz.agenticai.core.ingestion.chunking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

class FixedSizeChunkingStrategyTest {

    @Test
    void rejectsInvalidParams() {
        assertThatThrownBy(() -> new FixedSizeChunkingStrategy(0, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new FixedSizeChunkingStrategy(100, 100))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new FixedSizeChunkingStrategy(100, -1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void splitsIntoWindowsWithOverlap() {
        FixedSizeChunkingStrategy strategy = new FixedSizeChunkingStrategy(10, 3);
        Document doc = new Document("abcdefghijABCDEFGHIJ0123456789", Map.of("source", "test"));

        List<Document> chunks = strategy.chunk(doc);

        // step = 10 - 3 = 7, length = 30 → windows at 0, 7, 14, 21
        assertThat(chunks).extracting(Document::getText).containsExactly(
                "abcdefghij",
                "hijABCDEFG",
                "EFGHIJ0123",
                "123456789");
    }

    @Test
    void shortDocumentYieldsSingleChunk() {
        FixedSizeChunkingStrategy strategy = new FixedSizeChunkingStrategy(100, 10);
        Document doc = new Document("short", Map.of());

        List<Document> chunks = strategy.chunk(doc);

        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).getText()).isEqualTo("short");
    }

    @Test
    void emptyDocumentYieldsNoChunks() {
        assertThat(new FixedSizeChunkingStrategy().chunk(new Document("", Map.of()))).isEmpty();
    }

    @Test
    void attachesChunkMetadata() {
        FixedSizeChunkingStrategy strategy = new FixedSizeChunkingStrategy(5, 0);
        Document doc = new Document("abcdefghij", Map.of("source", "s"));

        List<Document> chunks = strategy.chunk(doc);

        assertThat(chunks).hasSize(2);
        assertThat(chunks.get(0).getMetadata())
                .containsEntry(ChunkMetadata.PARENT_DOCUMENT_ID, doc.getId())
                .containsEntry(ChunkMetadata.CHUNK_INDEX, 0)
                .containsEntry(ChunkMetadata.CHUNK_STRATEGY, "fixed-size")
                .containsEntry("source", "s");
        assertThat(chunks.get(1).getMetadata()).containsEntry(ChunkMetadata.CHUNK_INDEX, 1);
    }
}
