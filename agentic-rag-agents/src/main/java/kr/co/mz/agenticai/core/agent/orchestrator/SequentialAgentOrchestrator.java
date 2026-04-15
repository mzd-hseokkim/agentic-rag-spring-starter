package kr.co.mz.agenticai.core.agent.orchestrator;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import kr.co.mz.agenticai.core.common.AgentContext;
import kr.co.mz.agenticai.core.common.RagRequest;
import kr.co.mz.agenticai.core.common.RagResponse;
import kr.co.mz.agenticai.core.common.RagUsage;
import kr.co.mz.agenticai.core.common.event.RagEvent;
import kr.co.mz.agenticai.core.common.exception.AgenticRagException;
import kr.co.mz.agenticai.core.common.spi.Agent;
import kr.co.mz.agenticai.core.common.spi.AgentOrchestrator;
import kr.co.mz.agenticai.core.common.spi.RagEventPublisher;

/**
 * Runs each {@link Agent} in the supplied order. After the validation step,
 * if {@code validationPassed == false} and {@code iteration < maxIterations},
 * the orchestrator restarts from the {@code retryFromAgent} (typically
 * {@code retrieval}) so the chain re-fetches sources and regenerates.
 */
public final class SequentialAgentOrchestrator implements AgentOrchestrator {

    private final List<Agent> agents;
    private final RagEventPublisher events;
    private final int maxIterations;
    private final String retryFromAgent;

    public SequentialAgentOrchestrator(
            List<Agent> agents, RagEventPublisher events,
            int maxIterations, String retryFromAgent) {
        if (agents == null || agents.isEmpty()) {
            throw new IllegalArgumentException("at least one agent is required");
        }
        if (maxIterations < 1) {
            throw new IllegalArgumentException("maxIterations must be >= 1");
        }
        this.agents = List.copyOf(agents);
        this.events = Objects.requireNonNull(events, "events");
        this.maxIterations = maxIterations;
        this.retryFromAgent = retryFromAgent;
    }

    @Override
    public RagResponse run(RagRequest request) {
        Objects.requireNonNull(request, "request");
        AgentContext ctx = new AgentContext(request, UUID.randomUUID().toString());

        events.publish(new AgentRunStarted(
                ctx.correlationId(), agents.size(), Instant.now()));

        int retryIndex = retryFromAgent == null
                ? 0
                : indexOf(retryFromAgent);

        while (true) {
            ctx.incrementIteration();
            int startIndex = ctx.iteration() == 1 ? 0 : retryIndex;
            for (int i = startIndex; i < agents.size(); i++) {
                Agent agent = agents.get(i);
                try {
                    agent.execute(ctx);
                } catch (RuntimeException e) {
                    events.publish(new AgentRunFailed(
                            ctx.correlationId(), agent.name(), e.toString(), Instant.now()));
                    throw new AgenticRagException("Agent '" + agent.name() + "' failed", e);
                }
            }
            boolean done = ctx.iteration() >= maxIterations
                    || ctx.validationPassed() == null
                    || Boolean.TRUE.equals(ctx.validationPassed());
            if (done) {
                break;
            }
        }

        events.publish(new AgentRunCompleted(
                ctx.correlationId(), ctx.iteration(),
                Boolean.TRUE.equals(ctx.validationPassed()),
                Instant.now()));

        Map<String, Object> attrs = new HashMap<>(ctx.attributes());
        attrs.put("agentTrace", List.copyOf(ctx.trace()));
        attrs.put("iteration", ctx.iteration());
        if (ctx.validationReason() != null) {
            attrs.put("validationReason", ctx.validationReason());
        }
        return new RagResponse(
                ctx.answer() == null ? "" : ctx.answer(),
                ctx.citations(),
                RagUsage.empty(),
                attrs);
    }

    private int indexOf(String name) {
        for (int i = 0; i < agents.size(); i++) {
            if (agents.get(i).name().equals(name)) {
                return i;
            }
        }
        return 0;
    }

    public record AgentRunStarted(String correlationId, int agentCount, Instant timestamp)
            implements RagEvent {}
    public record AgentRunCompleted(String correlationId, int iterations, boolean grounded,
                                    Instant timestamp) implements RagEvent {}
    public record AgentRunFailed(String correlationId, String agentName, String error,
                                 Instant timestamp) implements RagEvent {}
}
