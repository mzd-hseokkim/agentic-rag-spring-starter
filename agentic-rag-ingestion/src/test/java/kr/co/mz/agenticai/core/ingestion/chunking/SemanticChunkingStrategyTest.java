package kr.co.mz.agenticai.core.ingestion.chunking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import kr.co.mz.agenticai.core.common.exception.IngestionException;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;

class SemanticChunkingStrategyTest {

    @Test
    void rejectsInvalidParams() {
        EmbeddingModel model = mock(EmbeddingModel.class);
        assertThatThrownBy(() -> new SemanticChunkingStrategy(model, -0.1, 100))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SemanticChunkingStrategy(model, 1.1, 100))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SemanticChunkingStrategy(model, 0.5, -1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void singleSentenceReturnsSingleChunk() {
        EmbeddingModel model = mock(EmbeddingModel.class);
        SemanticChunkingStrategy strategy = new SemanticChunkingStrategy(model, 0.5, 0);

        List<Document> chunks = strategy.chunk(new Document("only one sentence.", Map.of()));

        assertThat(chunks).hasSize(1);
    }

    @Test
    void identicalEmbeddingsYieldOneChunk() {
        EmbeddingModel model = mock(EmbeddingModel.class);
        when(model.embed(anyList())).thenAnswer(inv -> {
            List<?> sentences = inv.getArgument(0);
            List<float[]> out = new ArrayList<>();
            for (int i = 0; i < sentences.size(); i++) {
                out.add(new float[] {1, 0, 0});
            }
            return out;
        });
        SemanticChunkingStrategy strategy = new SemanticChunkingStrategy(model, 0.5, 0);

        List<Document> chunks = strategy.chunk(new Document("s1. s2. s3. s4.", Map.of()));

        assertThat(chunks).hasSize(1);
    }

    @Test
    void bimodalEmbeddingsProduceBoundaries() {
        // s1,s2 cluster A; s3,s4 cluster B; s5 cluster C → expect 3 chunks.
        float[][] vectors = {
                {1, 0, 0}, {1, 0, 0},
                {0, 1, 0}, {0, 1, 0},
                {0, 0, 1}
        };
        EmbeddingModel model = mock(EmbeddingModel.class);
        when(model.embed(anyList())).thenAnswer(inv -> {
            List<float[]> out = new ArrayList<>();
            for (float[] v : vectors) {
                out.add(v.clone());
            }
            return out;
        });
        // Percentile 0.5 on distances [0, 1, 0, 1] → threshold 0; breaks where distance > 0.
        SemanticChunkingStrategy strategy = new SemanticChunkingStrategy(model, 0.5, 0);

        List<Document> chunks = strategy.chunk(new Document("s1. s2. s3. s4. s5.", Map.of()));

        assertThat(chunks).hasSize(3);
        assertThat(chunks.get(0).getText()).contains("s1").contains("s2");
        assertThat(chunks.get(1).getText()).contains("s3").contains("s4");
        assertThat(chunks.get(2).getText()).contains("s5");
    }

    @Test
    void maxChunkCharsEnforcesHardLimit() {
        // All embeddings identical → no semantic breaks. Max chars must still split.
        EmbeddingModel model = mock(EmbeddingModel.class);
        when(model.embed(anyList())).thenAnswer(inv -> {
            List<?> sentences = inv.getArgument(0);
            List<float[]> out = new ArrayList<>();
            for (int i = 0; i < sentences.size(); i++) {
                out.add(new float[] {1, 0, 0});
            }
            return out;
        });
        // 5 short sentences, max 10 chars → forces splits.
        SemanticChunkingStrategy strategy = new SemanticChunkingStrategy(model, 0.95, 10);

        List<Document> chunks = strategy.chunk(new Document("aaa. bbb. ccc. ddd. eee.", Map.of()));

        assertThat(chunks).hasSizeGreaterThan(1)
                .allSatisfy(c -> assertThat(c.getText().length()).isLessThanOrEqualTo(10));
    }

    @Test
    void splitsKoreanSentencesWithoutWhitespace() {
        // "다." 뒤에 공백 없이 이어지는 한글 문장도 분리되어 임베딩 모델에 전달된다.
        // (문장이 두 개뿐일 때 경계값이 하나라 semantic split은 일어나지 않는다 —
        //  여기서 검증하는 건 splitter 동작뿐.)
        List<List<String>> captured = new ArrayList<>();
        EmbeddingModel model = mock(EmbeddingModel.class);
        when(model.embed(anyList())).thenAnswer(inv -> {
            @SuppressWarnings("unchecked")
            List<String> sentences = inv.getArgument(0);
            captured.add(List.copyOf(sentences));
            List<float[]> out = new ArrayList<>();
            for (int i = 0; i < sentences.size(); i++) {
                out.add(new float[] {1, 0});
            }
            return out;
        });
        SemanticChunkingStrategy strategy = new SemanticChunkingStrategy(model, 0.5, 0);

        strategy.chunk(new Document("첫 문장입니다.두 번째 문장이다.", Map.of()));

        assertThat(captured).hasSize(1);
        assertThat(captured.get(0)).containsExactly("첫 문장입니다.", "두 번째 문장이다.");
    }

    @Test
    void embeddingFailurePropagatesAsIngestionException() {
        EmbeddingModel model = mock(EmbeddingModel.class);
        when(model.embed(anyList())).thenThrow(new RuntimeException("network"));
        SemanticChunkingStrategy strategy = new SemanticChunkingStrategy(model);

        Document doc = new Document("s1. s2.", Map.of());
        assertThatThrownBy(() -> strategy.chunk(doc))
                .isInstanceOf(IngestionException.class)
                .hasMessageContaining("EmbeddingModel failed");
    }

    @Test
    void attachesChunkMetadata() {
        EmbeddingModel model = mock(EmbeddingModel.class);
        when(model.embed(anyList())).thenAnswer(inv -> {
            List<?> sentences = inv.getArgument(0);
            List<float[]> out = new ArrayList<>();
            for (int i = 0; i < sentences.size(); i++) {
                out.add(new float[] {1, 0});
            }
            return out;
        });
        SemanticChunkingStrategy strategy = new SemanticChunkingStrategy(model, 0.5, 0);

        List<Document> chunks = strategy.chunk(new Document("s1. s2.", Map.of("src", "a")));

        assertThat(chunks.get(0).getMetadata())
                .containsEntry(ChunkMetadata.CHUNK_STRATEGY, "semantic")
                .containsEntry(ChunkMetadata.CHUNK_INDEX, 0)
                .containsEntry("src", "a");
    }
}
