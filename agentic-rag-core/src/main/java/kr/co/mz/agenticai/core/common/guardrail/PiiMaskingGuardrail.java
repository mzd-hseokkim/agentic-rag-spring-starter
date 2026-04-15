package kr.co.mz.agenticai.core.common.guardrail;

import java.util.List;
import java.util.regex.Pattern;
import kr.co.mz.agenticai.core.common.spi.Guardrail;

/**
 * Masks common Personally Identifiable Information (email, Korean phone
 * numbers, Korean RRN) with type-tagged placeholders. Never blocks — the
 * sanitized text is forwarded down the chain.
 */
public final class PiiMaskingGuardrail implements Guardrail {

    private record Rule(Pattern pattern, String replacement) {}

    private static final List<Rule> DEFAULT_RULES = List.of(
            new Rule(Pattern.compile("\\b[\\w.+-]+@[\\w-]+(?:\\.[\\w-]+)+\\b"), "[REDACTED:EMAIL]"),
            // Korean RRN: 6 digits - 7 digits (e.g. 900101-1234567).
            new Rule(Pattern.compile("\\b\\d{6}-?\\d{7}\\b"), "[REDACTED:RRN]"),
            // Korean phone: 010-xxxx-xxxx, 02-xxxx-xxxx, etc.
            new Rule(Pattern.compile("\\b0\\d{1,2}-?\\d{3,4}-?\\d{4}\\b"), "[REDACTED:PHONE]"));

    private final Stage stage;
    private final int order;
    private final List<Rule> rules;

    public PiiMaskingGuardrail(Stage stage) {
        this(stage, 0, DEFAULT_RULES);
    }

    public PiiMaskingGuardrail(Stage stage, int order, List<Rule> rules) {
        this.stage = java.util.Objects.requireNonNull(stage, "stage");
        this.order = order;
        this.rules = List.copyOf(rules);
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
        if (text == null) {
            return Result.pass(null);
        }
        String current = text;
        for (Rule r : rules) {
            current = r.pattern().matcher(current).replaceAll(r.replacement());
        }
        return Result.pass(current);
    }
}
