package kr.co.mz.agenticai.core.common.memory.policy;

import kr.co.mz.agenticai.core.common.spi.TokenCounter;

/**
 * Token estimator based on the char/4 heuristic — adequate for English and
 * Korean mixed text. Replace with a model-specific {@link TokenCounter} bean
 * when accurate counts are required.
 */
public final class HeuristicTokenCounter implements TokenCounter {

    @Override
    public int count(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        return Math.max(1, (text.length() + 3) / 4);
    }
}
