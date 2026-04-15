package kr.co.mz.agenticai.core.retrieval.bm25;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

class KoreanAnalyzerBm25Test {

    @Test
    void standardAnalyzerFailsOnKoreanParticles() {
        // "서울에서" is a single token under StandardAnalyzer, so a query for
        // "서울" does not match. This documents why Korean needs Nori.
        try (LuceneBm25Index index = new LuceneBm25Index()) {
            index.addDocuments(List.of(new Document("서울에서 봄이 온다", Map.of())));
            assertThat(index.search("서울", 5)).isEmpty();
        }
    }

    @Test
    void koreanAnalyzerMatchesStemsAcrossParticles() {
        try (LuceneBm25Index index = new LuceneBm25Index(KoreanAnalyzers.standard())) {
            Document a = new Document("서울에서 봄이 온다", Map.of("src", "a"));
            Document b = new Document("부산에서 여름이 간다", Map.of("src", "b"));
            index.addDocuments(List.of(a, b));

            List<Document> hits = index.search("서울", 5);

            assertThat(hits).hasSize(1);
            assertThat(hits.get(0).getId()).isEqualTo(a.getId());
        }
    }

    @Test
    void koreanAnalyzerRanksByTermFrequency() {
        try (LuceneBm25Index index = new LuceneBm25Index(KoreanAnalyzers.standard())) {
            Document many = new Document("검색엔진 검색엔진 검색엔진 개발", Map.of("src", "many"));
            Document one = new Document("검색엔진 성능 분석", Map.of("src", "one"));
            index.addDocuments(List.of(many, one));

            List<Document> hits = index.search("검색엔진", 5);

            assertThat(hits).extracting(Document::getId).containsExactly(many.getId(), one.getId());
        }
    }
}
