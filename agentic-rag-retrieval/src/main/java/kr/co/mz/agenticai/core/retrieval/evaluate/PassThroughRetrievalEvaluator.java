package kr.co.mz.agenticai.core.retrieval.evaluate;

import java.util.List;
import kr.co.mz.agenticai.core.common.spi.RetrievalEvaluator;
import kr.co.mz.agenticai.core.common.spi.RetrieverRouter.Query;
import org.springframework.ai.document.Document;

/**
 * Default {@link RetrievalEvaluator} — always returns {@link Action#ACCEPT}
 * with maximum confidence, imposing no quality gate.
 *
 * <p>This implementation is the safe default for Phase 2. Activate a
 * threshold-based or LLM-based evaluator by providing a custom
 * {@code RetrievalEvaluator} bean, which overrides this one via
 * {@code @ConditionalOnMissingBean}.
 */
public final class PassThroughRetrievalEvaluator implements RetrievalEvaluator {

    @Override
    public Decision evaluate(Query query, List<Document> candidates) {
        return Decision.of(Action.ACCEPT, 1.0);
    }
}
