package kr.co.mz.agenticai.core.agent.agents;

import java.util.Map;
import kr.co.mz.agenticai.core.common.AgentContext;
import kr.co.mz.agenticai.core.common.spi.Agent;
import kr.co.mz.agenticai.core.common.spi.FactChecker;

/**
 * Runs the {@link FactChecker} (when present) against the generated answer
 * and writes the verdict back into the context so the orchestrator can
 * decide whether to loop. When no fact-checker is configured, validation
 * passes by default.
 */
public final class ValidationAgent implements Agent {

    public static final String NAME = "validation";

    private final FactChecker factChecker;

    public ValidationAgent(FactChecker factChecker) {
        this.factChecker = factChecker;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public void execute(AgentContext context) {
        if (factChecker == null || context.selectedSources().isEmpty()
                || context.answer() == null || context.answer().isBlank()) {
            context.setValidationPassed(true);
            context.recordStep(NAME + ":skip");
            return;
        }
        FactChecker.FactCheckResult result = factChecker.check(new FactChecker.FactCheckRequest(
                context.answer(), context.selectedSources(), context.request().query(), Map.of()));
        context.setValidationPassed(result.grounded());
        context.setValidationReason(result.reason());
        if (result.grounded() && !result.citations().isEmpty()) {
            context.setCitations(result.citations());
        }
        context.recordStep(NAME);
    }
}
