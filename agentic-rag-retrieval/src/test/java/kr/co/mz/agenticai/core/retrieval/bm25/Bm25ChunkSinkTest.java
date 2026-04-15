package kr.co.mz.agenticai.core.retrieval.bm25;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

class Bm25ChunkSinkTest {

    private LuceneBm25Index index;
    private Bm25ChunkSink sink;

    @BeforeEach
    void setUp() {
        index = new LuceneBm25Index();
        sink = new Bm25ChunkSink(index);
    }

    @AfterEach
    void tearDown() {
        index.close();
    }

    @Test
    void chunksBecomeSearchable() {
        sink.accept(List.of(
                new Document("spring boot guide", Map.of()),
                new Document("kubernetes in action", Map.of())));

        assertThat(index.search("spring", 5)).hasSize(1);
        assertThat(index.size()).isEqualTo(2);
    }

    @Test
    void nullAndEmptyAreNoops() {
        sink.accept(null);
        sink.accept(List.of());
        assertThat(index.size()).isZero();
    }
}
