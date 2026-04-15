package kr.co.mz.agenticai.core.common.spi;

/**
 * Filters text entering or leaving the pipeline (PII masking,
 * prompt-injection defense, toxicity screening, ...). Multiple guardrail
 * beans are chained in {@link #order()} ascending order.
 */
public interface Guardrail {

    enum Stage { INPUT, OUTPUT }

    Stage stage();

    /** Lower runs first. */
    default int order() {
        return 0;
    }

    /**
     * Inspect and possibly transform the text. Returning a {@link Result}
     * with {@code blocked == true} aborts the pipeline.
     */
    Result apply(String text);

    record Result(String text, boolean blocked, String reason) {
        public static Result pass(String text) {
            return new Result(text, false, null);
        }

        public static Result block(String reason) {
            return new Result(null, true, reason);
        }
    }
}
