package kr.co.mz.agenticai.core.ingestion.chunking;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

class RecursiveCharacterChunkingStrategyTest {

    @Test
    void keepsWholeTextWhenUnderLimit() {
        var strategy = new RecursiveCharacterChunkingStrategy();
        Document doc = new Document("short text", Map.of());

        List<Document> chunks = strategy.chunk(doc);

        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).getText()).isEqualTo("short text");
    }

    @Test
    void prefersParagraphBoundaries() {
        var strategy = new RecursiveCharacterChunkingStrategy(30, List.of("\n\n", "\n", " ", ""));
        String text = "para one here.\n\npara two here.\n\npara three is somewhat longer.";
        Document doc = new Document(text, Map.of());

        List<Document> chunks = strategy.chunk(doc);

        assertThat(chunks).allSatisfy(c -> assertThat(c.getText().length()).isLessThanOrEqualTo(30));
        // All original content must be preserved after concatenation
        assertThat(chunks.stream().map(Document::getText).reduce("", String::concat))
                .isEqualTo(text);
    }

    @Test
    void fallsBackToHardSplitForTokenlessText() {
        var strategy = new RecursiveCharacterChunkingStrategy(5, List.of(" ", ""));
        Document doc = new Document("abcdefghijklmno", Map.of()); // no spaces

        List<Document> chunks = strategy.chunk(doc);

        assertThat(chunks).extracting(Document::getText).containsExactly("abcde", "fghij", "klmno");
    }

    @Test
    void attachesChunkMetadata() {
        var strategy = new RecursiveCharacterChunkingStrategy(10, List.of(" ", ""));
        Document doc = new Document("one two three four five six", Map.of("source", "t"));

        List<Document> chunks = strategy.chunk(doc);

        assertThat(chunks).isNotEmpty();
        assertThat(chunks.get(0).getMetadata())
                .containsEntry(ChunkMetadata.CHUNK_STRATEGY, "recursive-character")
                .containsEntry(ChunkMetadata.CHUNK_INDEX, 0)
                .containsEntry("source", "t");
    }
}
