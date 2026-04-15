package kr.co.mz.agenticai.core.agent.agents;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import kr.co.mz.agenticai.core.common.AgentContext;
import kr.co.mz.agenticai.core.common.spi.Agent;
import org.springframework.ai.document.Document;

/**
 * Deterministic post-processing of retrieved candidates: drop empty / null
 * text, deduplicate by parent document id, preserve order. Anything more
 * elaborate (re-ranking by intent, etc.) is the user's concern via a
 * replacement {@link Agent} bean.
 */
public final class InterpretationAgent implements Agent {

    public static final String NAME = "interpretation";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public void execute(AgentContext context) {
        List<Document> retrieved = context.retrieved();
        if (retrieved.isEmpty()) {
            context.setSelectedSources(List.of());
            context.recordStep(NAME);
            return;
        }
        Set<String> seenParents = new HashSet<>();
        List<Document> kept = new ArrayList<>();
        for (Document d : retrieved) {
            String text = d.getText();
            if (text == null || text.isBlank()) {
                continue;
            }
            String parent = (String) d.getMetadata().getOrDefault(
                    "agenticRag.parentDocumentId", d.getId());
            if (seenParents.add(parent)) {
                kept.add(d);
            }
        }
        context.setSelectedSources(kept);
        context.recordStep(NAME);
    }
}
