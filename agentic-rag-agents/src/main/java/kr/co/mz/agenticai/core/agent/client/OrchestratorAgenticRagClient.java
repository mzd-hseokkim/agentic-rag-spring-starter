package kr.co.mz.agenticai.core.agent.client;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import kr.co.mz.agenticai.core.common.AgenticRagClient;
import kr.co.mz.agenticai.core.common.RagRequest;
import kr.co.mz.agenticai.core.common.RagResponse;
import kr.co.mz.agenticai.core.common.RagStreamEvent;
import kr.co.mz.agenticai.core.common.RagUsage;
import kr.co.mz.agenticai.core.common.guardrail.Guardrails;
import kr.co.mz.agenticai.core.common.spi.AgentOrchestrator;
import kr.co.mz.agenticai.core.common.spi.Guardrail;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Bridges {@link AgenticRagClient} to the multi-step {@link AgentOrchestrator}.
 * Streaming reduces to "run, then emit one Completed event" — token-level
 * streaming requires LLM access at the {@code SummaryAgent} layer and is
 * scheduled for a later iteration.
 */
public final class OrchestratorAgenticRagClient implements AgenticRagClient {

    private final AgentOrchestrator orchestrator;
    private final List<Guardrail> guardrails;

    public OrchestratorAgenticRagClient(AgentOrchestrator orchestrator) {
        this(orchestrator, List.of());
    }

    public OrchestratorAgenticRagClient(AgentOrchestrator orchestrator, List<Guardrail> guardrails) {
        this.orchestrator = Objects.requireNonNull(orchestrator, "orchestrator");
        this.guardrails = guardrails == null ? List.of() : List.copyOf(guardrails);
    }

    @Override
    public RagResponse ask(RagRequest request) {
        Guardrails.Outcome inputOutcome = Guardrails.apply(guardrails, Guardrail.Stage.INPUT, request.query());
        if (inputOutcome.blocked()) {
            return blockedResponse(inputOutcome);
        }
        RagResponse response = orchestrator.run(withQuery(request, inputOutcome.text()));

        Guardrails.Outcome outputOutcome = Guardrails.apply(
                guardrails, Guardrail.Stage.OUTPUT, response.answer());
        if (outputOutcome.blocked()) {
            return blockedResponse(outputOutcome);
        }
        if (outputOutcome.text() != null && !outputOutcome.text().equals(response.answer())) {
            return new RagResponse(outputOutcome.text(), response.citations(),
                    response.usage(), response.attributes());
        }
        return response;
    }

    @Override
    public Flux<RagStreamEvent> askStream(RagRequest request) {
        return Mono.fromCallable(() -> ask(request))
                .subscribeOn(Schedulers.boundedElastic())
                .map(response -> (RagStreamEvent) new RagStreamEvent.Completed(response))
                .onErrorResume(e -> Mono.just(new RagStreamEvent.Failed(e)))
                .flux();
    }

    private static RagRequest withQuery(RagRequest original, String newQuery) {
        if (newQuery == null || newQuery.equals(original.query())) {
            return original;
        }
        return RagRequest.builder()
                .query(newQuery)
                .sessionId(original.sessionId())
                .userId(original.userId())
                .metadataFilters(original.metadataFilters())
                .overrides(original.overrides())
                .streaming(original.streaming())
                .attributes(original.attributes())
                .build();
    }

    private static RagResponse blockedResponse(Guardrails.Outcome outcome) {
        Map<String, Object> attrs = new HashMap<>();
        attrs.put("blocked", true);
        attrs.put("blockedBy", outcome.blockedBy());
        attrs.put("guardrailReason", outcome.reason());
        return new RagResponse("", List.of(), RagUsage.empty(), attrs);
    }
}
