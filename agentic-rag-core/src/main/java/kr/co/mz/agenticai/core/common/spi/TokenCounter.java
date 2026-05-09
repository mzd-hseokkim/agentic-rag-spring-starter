package kr.co.mz.agenticai.core.common.spi;

/**
 * Estimates the token count of a text string. The default implementation
 * uses a character/4 heuristic; register a bean of this type to supply a
 * model-specific counter (e.g. jtokkit for OpenAI models).
 */
public interface TokenCounter {

    /**
     * Returns the estimated token count for {@code text}.
     * Must never return a negative value.
     */
    int count(String text);
}
