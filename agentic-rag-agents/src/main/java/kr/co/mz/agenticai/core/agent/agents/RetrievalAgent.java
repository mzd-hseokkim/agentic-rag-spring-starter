package kr.co.mz.agenticai.core.agent.agents;

import java.util.Objects;
import kr.co.mz.agenticai.core.common.AgentContext;
import kr.co.mz.agenticai.core.common.spi.Agent;
import kr.co.mz.agenticai.core.common.spi.RetrieverRouter;

/**
 * Calls the {@link RetrieverRouter} with the (possibly refined) query and
 * stashes results under {@link AgentContext#retrieved()}. Skipped for
 * {@code conversational} intent (no retrieval needed).
 */
public final class RetrievalAgent implements Agent {

    public static final String CANONICAL_NAME = "retrieval";

    private final RetrieverRouter router;
    private final int defaultTopK;

    public RetrievalAgent(RetrieverRouter router, int defaultTopK) {
        this.router = Objects.requireNonNull(router, "router");
        if (defaultTopK <= 0) {
            throw new IllegalArgumentException("defaultTopK must be > 0");
        }
        this.defaultTopK = defaultTopK;
    }

    @Override
    public String name() {
        return CANONICAL_NAME;
    }

    @Override
    public void execute(AgentContext context) {
        if ("conversational".equals(context.intent())) {
            context.setRetrieved(java.util.List.of());
            context.recordStep(CANONICAL_NAME + ":skip");
            return;
        }
        Integer override = context.request().overrides().topK();
        int topK = override != null && override > 0 ? override : defaultTopK;
        var hits = router.retrieve(new RetrieverRouter.Query(
                context.refinedQuery(), topK,
                context.request().metadataFilters(),
                context.request().attributes()));
        context.setRetrieved(hits);
        context.recordStep(CANONICAL_NAME);
    }
}
