package kr.co.mz.agenticai.core.agent.agents;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import kr.co.mz.agenticai.core.common.AgentContext;
import kr.co.mz.agenticai.core.common.event.FactCheckEvent;
import kr.co.mz.agenticai.core.common.spi.Agent;
import kr.co.mz.agenticai.core.common.spi.FactChecker;
import kr.co.mz.agenticai.core.common.spi.RagEventPublisher;

/**
 * Runs the {@link FactChecker} (when present) against the generated answer
 * and writes the verdict back into the context so the orchestrator can
 * decide whether to loop. When no fact-checker is configured, validation
 * passes by default.
 */
public final class ValidationAgent implements Agent {

    public static final String CANONICAL_NAME = "validation";

    private final FactChecker factChecker;
    private final RagEventPublisher events;

    public ValidationAgent(FactChecker factChecker, RagEventPublisher events) {
        this.factChecker = factChecker;
        this.events = events;
    }

    @Override
    public String name() {
        return CANONICAL_NAME;
    }

    @Override
    public void execute(AgentContext context) {
        if (factChecker == null || context.selectedSources().isEmpty()
                || context.answer() == null || context.answer().isBlank()) {
            context.setValidationPassed(true);
            context.recordStep(CANONICAL_NAME + ":skip");
            return;
        }
        long started = System.currentTimeMillis();
        FactChecker.FactCheckResult result = factChecker.check(new FactChecker.FactCheckRequest(
                context.answer(), context.selectedSources(), context.request().query(), Map.of()));
        long elapsed = System.currentTimeMillis() - started;
        context.setValidationPassed(result.grounded());
        context.setValidationReason(result.reason());
        if (result.grounded() && !result.citations().isEmpty()) {
            context.setCitations(result.citations());
        }

        String summary = context.answer().substring(0, Math.min(context.answer().length(), 80));
        if (events != null) {
            if (result.grounded()) {
                events.publish(new FactCheckEvent.FactCheckPassed(
                        summary, result.confidence(), result.citations().size(),
                        elapsed, Instant.now(), context.correlationId()));
            } else {
                events.publish(new FactCheckEvent.FactCheckFailed(
                        summary, result.confidence(), result.reason(),
                        elapsed, Instant.now(), context.correlationId()));
            }
        }
        context.recordStep(CANONICAL_NAME);
    }
}
