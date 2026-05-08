package kr.co.mz.agenticai.core.retrieval.evaluate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import kr.co.mz.agenticai.core.common.spi.RetrievalEvaluator;
import kr.co.mz.agenticai.core.common.spi.RetrievalEvaluator.Action;
import kr.co.mz.agenticai.core.common.spi.RetrieverRouter.Query;
import kr.co.mz.agenticai.core.retrieval.RetrievalMetadata;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

class ScoreThresholdEvaluatorTest {

    private static final Query QUERY = Query.of("test query", 5);

    // T1: PassThroughRetrievalEvaluator always returns ACCEPT
    @Test
    void passThroughAlwaysAccepts() {
        var evaluator = new PassThroughRetrievalEvaluator();
        Document doc = new Document("id", "content", Map.of());

        RetrievalEvaluator.Decision result = evaluator.evaluate(QUERY, List.of(doc));

        assertThat(result.action()).isEqualTo(Action.ACCEPT);
        assertThat(result.score()).isEqualTo(1.0);
    }

    @Test
    void passThroughAcceptsEmptyCandidates() {
        var evaluator = new PassThroughRetrievalEvaluator();

        RetrievalEvaluator.Decision result = evaluator.evaluate(QUERY, List.of());

        assertThat(result.action()).isEqualTo(Action.ACCEPT);
    }

    // T2: ScoreThresholdEvaluator — top1 score >= threshold → ACCEPT
    @Test
    void scoreAboveThresholdAccepts() {
        var evaluator = new ScoreThresholdEvaluator(0.5);
        Document doc = docWithScore(0.8);

        RetrievalEvaluator.Decision result = evaluator.evaluate(QUERY, List.of(doc));

        assertThat(result.action()).isEqualTo(Action.ACCEPT);
        assertThat(result.score()).isEqualTo(0.8);
    }

    @Test
    void scoreEqualToThresholdAccepts() {
        var evaluator = new ScoreThresholdEvaluator(0.5);
        Document doc = docWithScore(0.5);

        RetrievalEvaluator.Decision result = evaluator.evaluate(QUERY, List.of(doc));

        assertThat(result.action()).isEqualTo(Action.ACCEPT);
    }

    // T3: ScoreThresholdEvaluator — top1 score < threshold → RETRY, reason contains score
    @Test
    void scoreBelowThresholdRetries() {
        var evaluator = new ScoreThresholdEvaluator(0.5);
        Document doc = docWithScore(0.3);

        RetrievalEvaluator.Decision result = evaluator.evaluate(QUERY, List.of(doc));

        assertThat(result.action()).isEqualTo(Action.RETRY);
        assertThat(result.score()).isEqualTo(0.3);
        assertThat(result.reason()).contains("0.3").contains("0.5");
    }

    @Test
    void missingFusedScoreKeyTreatedAsZero() {
        var evaluator = new ScoreThresholdEvaluator(0.5);
        Document docWithoutScore = new Document("id", "content", Map.of());

        RetrievalEvaluator.Decision result = evaluator.evaluate(QUERY, List.of(docWithoutScore));

        assertThat(result.action()).isEqualTo(Action.RETRY);
        assertThat(result.score()).isEqualTo(0.0);
    }

    @Test
    void emptyCandidatesRetries() {
        var evaluator = new ScoreThresholdEvaluator(0.5);

        RetrievalEvaluator.Decision result = evaluator.evaluate(QUERY, List.of());

        assertThat(result.action()).isEqualTo(Action.RETRY);
        assertThat(result.reason()).isNotNull();
    }

    @Test
    void nullCandidatesRetries() {
        var evaluator = new ScoreThresholdEvaluator(0.5);

        RetrievalEvaluator.Decision result = evaluator.evaluate(QUERY, null);

        assertThat(result.action()).isEqualTo(Action.RETRY);
    }

    @Test
    void invalidMinScoreThrows() {
        assertThatThrownBy(() -> new ScoreThresholdEvaluator(-0.1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ScoreThresholdEvaluator(1.1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static Document docWithScore(double score) {
        return new Document("id", "content", Map.of(RetrievalMetadata.FUSED_SCORE, score));
    }
}
