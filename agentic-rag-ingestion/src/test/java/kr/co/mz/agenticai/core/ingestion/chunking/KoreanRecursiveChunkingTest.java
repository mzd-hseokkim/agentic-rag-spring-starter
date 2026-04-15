package kr.co.mz.agenticai.core.ingestion.chunking;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

class KoreanRecursiveChunkingTest {

    @Test
    void splitsAtKoreanSentenceEndings() {
        var strategy = RecursiveCharacterChunkingStrategy.forKorean(40);
        String text = "첫 문장입니다. 두 번째 문장이에요. 세 번째는 질문인가요? 네 번째 문장이다. 다섯 번째 문장은 여기까지.";
        Document doc = new Document(text, Map.of());

        List<Document> chunks = strategy.chunk(doc);

        assertThat(chunks).allSatisfy(c -> assertThat(c.getText().length()).isLessThanOrEqualTo(40));
        // Content must be preserved when concatenated.
        assertThat(chunks.stream().map(Document::getText).reduce("", String::concat)).isEqualTo(text);
        // Expect at least one chunk boundary right after a Korean sentence ending.
        assertThat(chunks.stream().anyMatch(c -> c.getText().endsWith("다. ")
                || c.getText().endsWith("요. ")
                || c.getText().endsWith("까? ")
                || c.getText().endsWith("습니다. ")))
                .isTrue();
    }

    @Test
    void koreanSeparatorPresetIsReadOnly() {
        // KOREAN_SEPARATORS is exposed — ensure it's an immutable copy.
        var seps = RecursiveCharacterChunkingStrategy.KOREAN_SEPARATORS;
        assertThat(seps).isNotEmpty();
        org.junit.jupiter.api.Assertions.assertThrows(
                UnsupportedOperationException.class, () -> seps.add("boom"));
    }
}
