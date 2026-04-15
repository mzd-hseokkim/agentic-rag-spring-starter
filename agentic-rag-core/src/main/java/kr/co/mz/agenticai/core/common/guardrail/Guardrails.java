package kr.co.mz.agenticai.core.common.guardrail;

import java.util.Comparator;
import java.util.List;
import kr.co.mz.agenticai.core.common.spi.Guardrail;

/**
 * Applies an ordered chain of {@link Guardrail}s for a given stage.
 * Returns an {@link Outcome} indicating the (possibly transformed) text and
 * whether any guardrail blocked further processing.
 */
public final class Guardrails {

    private Guardrails() {}

    public static Outcome apply(List<Guardrail> guardrails, Guardrail.Stage stage, String text) {
        if (guardrails == null || guardrails.isEmpty() || text == null) {
            return new Outcome(text, false, null, null);
        }
        String current = text;
        for (Guardrail g : guardrails.stream()
                .filter(x -> x.stage() == stage)
                .sorted(Comparator.comparingInt(Guardrail::order))
                .toList()) {
            Guardrail.Result result = g.apply(current);
            if (result.blocked()) {
                return new Outcome(null, true, result.reason(), g.getClass().getSimpleName());
            }
            if (result.text() != null) {
                current = result.text();
            }
        }
        return new Outcome(current, false, null, null);
    }

    /** Result of a chain application. */
    public record Outcome(String text, boolean blocked, String reason, String blockedBy) {}
}
