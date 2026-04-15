package kr.co.mz.agenticai.core.ingestion.chunking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

class MarkdownHeadingChunkingStrategyTest {

    @Test
    void rejectsOutOfRangeLevel() {
        assertThatThrownBy(() -> new MarkdownHeadingChunkingStrategy(0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new MarkdownHeadingChunkingStrategy(7))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void supportsDecidedByContentTypeOrMdSuffix() {
        var strategy = new MarkdownHeadingChunkingStrategy();
        assertThat(strategy.supports(new Document("x", Map.of("contentType", "text/markdown")))).isTrue();
        assertThat(strategy.supports(new Document("x", Map.of("source", "/tmp/a.md")))).isTrue();
        assertThat(strategy.supports(new Document("x", Map.of("source", "/tmp/a.pdf")))).isFalse();
        assertThat(strategy.supports(new Document("x", Map.of()))).isFalse();
    }

    @Test
    void splitsAtHeadingsAndRecordsBreadcrumb() {
        var strategy = new MarkdownHeadingChunkingStrategy(3);
        String md = """
                # Intro
                Intro body.
                ## Setup
                Setup body.
                ### Details
                Detail body.
                ## Usage
                Usage body.
                """;
        Document doc = new Document(md, Map.of("source", "doc.md"));

        List<Document> chunks = strategy.chunk(doc);

        assertThat(chunks).hasSize(4);
        assertThat(chunks.get(0).getMetadata()).containsEntry(ChunkMetadata.HEADING_PATH, "Intro");
        assertThat(chunks.get(1).getMetadata()).containsEntry(ChunkMetadata.HEADING_PATH, "Intro > Setup");
        assertThat(chunks.get(2).getMetadata())
                .containsEntry(ChunkMetadata.HEADING_PATH, "Intro > Setup > Details");
        assertThat(chunks.get(3).getMetadata()).containsEntry(ChunkMetadata.HEADING_PATH, "Intro > Usage");
    }

    @Test
    void headingsBeyondMaxLevelDoNotSplit() {
        var strategy = new MarkdownHeadingChunkingStrategy(2);
        String md = """
                # A
                body A
                ### too deep
                still part of A
                ## B
                body B
                """;

        List<Document> chunks = strategy.chunk(new Document(md, Map.of()));

        assertThat(chunks).hasSize(2);
        assertThat(chunks.get(0).getText()).contains("too deep").contains("still part of A");
    }

    @Test
    void emptyDocumentYieldsNoChunks() {
        assertThat(new MarkdownHeadingChunkingStrategy().chunk(new Document("", Map.of()))).isEmpty();
    }
}
