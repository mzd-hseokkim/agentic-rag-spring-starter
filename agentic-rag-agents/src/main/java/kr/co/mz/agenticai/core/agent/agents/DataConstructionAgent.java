package kr.co.mz.agenticai.core.agent.agents;

import java.util.List;
import kr.co.mz.agenticai.core.common.AgentContext;
import kr.co.mz.agenticai.core.common.spi.Agent;
import org.springframework.ai.document.Document;

/**
 * Caps the candidate set to {@code maxSources} and stores it back. Could be
 * extended to summarize long sources, dedupe semantically, or rebalance
 * across topics.
 */
public final class DataConstructionAgent implements Agent {

    public static final String NAME = "data-construction";

    private final int maxSources;

    public DataConstructionAgent(int maxSources) {
        if (maxSources <= 0) {
            throw new IllegalArgumentException("maxSources must be > 0");
        }
        this.maxSources = maxSources;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public void execute(AgentContext context) {
        List<Document> selected = context.selectedSources();
        if (selected.size() > maxSources) {
            context.setSelectedSources(selected.subList(0, maxSources));
        }
        context.recordStep(NAME);
    }
}
