package kr.co.mz.agenticai.core.retrieval.bm25;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import kr.co.mz.agenticai.core.retrieval.RetrievalMetadata;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

class LuceneBm25IndexTest {

    private LuceneBm25Index index;

    @BeforeEach
    void setUp() {
        index = new LuceneBm25Index();
    }

    @AfterEach
    void tearDown() {
        index.close();
    }

    @Test
    void emptyIndexReturnsNothing() {
        assertThat(index.search("anything", 5)).isEmpty();
        assertThat(index.size()).isZero();
    }

    @Test
    void emptyQueryReturnsNothing() {
        index.addDocuments(List.of(new Document("hello world", Map.of())));
        assertThat(index.search("", 5)).isEmpty();
        assertThat(index.search("   ", 5)).isEmpty();
    }

    @Test
    void ranksDocumentsByBm25Relevance() {
        Document a = new Document("spring framework dependency injection", Map.of("src", "a"));
        Document b = new Document("kubernetes container orchestration", Map.of("src", "b"));
        Document c = new Document("spring boot auto configuration", Map.of("src", "c"));
        index.addDocuments(List.of(a, b, c));

        List<Document> hits = index.search("spring configuration", 3);

        assertThat(hits).hasSize(2); // only a and c contain query terms
        assertThat(hits.get(0).getId()).isEqualTo(c.getId()); // both terms matched
        assertThat(hits.get(0).getMetadata())
                .containsEntry(RetrievalMetadata.RANK, 0)
                .containsKey(RetrievalMetadata.BM25_SCORE);
        assertThat((Double) hits.get(0).getMetadata().get(RetrievalMetadata.BM25_SCORE))
                .isGreaterThan((Double) hits.get(1).getMetadata().get(RetrievalMetadata.BM25_SCORE));
    }

    @Test
    void preservesSourceMetadata() {
        Document a = new Document("quick brown fox", Map.of("source", "/tmp/a.txt", "tag", "en"));
        index.addDocuments(List.of(a));

        List<Document> hits = index.search("fox", 1);

        assertThat(hits).hasSize(1);
        assertThat(hits.get(0).getMetadata())
                .containsEntry("source", "/tmp/a.txt")
                .containsEntry("tag", "en");
    }

    @Test
    void topKLimitsResults() {
        index.addDocuments(List.of(
                new Document("foo bar", Map.of()),
                new Document("foo baz", Map.of()),
                new Document("foo qux", Map.of())));

        assertThat(index.search("foo", 2)).hasSize(2);
    }
}
