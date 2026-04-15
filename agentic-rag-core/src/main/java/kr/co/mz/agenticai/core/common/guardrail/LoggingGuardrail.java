package kr.co.mz.agenticai.core.common.guardrail;

import kr.co.mz.agenticai.core.common.spi.Guardrail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Pass-through guardrail that logs the (truncated) text passing through.
 * Convenient as the last guardrail in a chain to capture the final
 * sanitized form.
 */
public final class LoggingGuardrail implements Guardrail {

    private static final Logger log = LoggerFactory.getLogger(LoggingGuardrail.class);
    private static final int MAX_LOG_CHARS = 200;

    private final Stage stage;
    private final int order;

    public LoggingGuardrail(Stage stage) {
        this(stage, Integer.MAX_VALUE);
    }

    public LoggingGuardrail(Stage stage, int order) {
        this.stage = java.util.Objects.requireNonNull(stage, "stage");
        this.order = order;
    }

    @Override
    public Stage stage() {
        return stage;
    }

    @Override
    public int order() {
        return order;
    }

    @Override
    public Result apply(String text) {
        if (log.isInfoEnabled()) {
            String preview = text == null ? "<null>"
                    : text.length() > MAX_LOG_CHARS ? text.substring(0, MAX_LOG_CHARS) + "…(truncated)" : text;
            log.info("[guardrail] {} {} chars: {}", stage, text == null ? 0 : text.length(), preview);
        }
        return Result.pass(text);
    }
}
