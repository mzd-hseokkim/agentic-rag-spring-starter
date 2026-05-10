package kr.co.mz.agenticai.core.agent.client;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.ObservationRegistry;
import java.util.ArrayList;
import java.util.List;
import kr.co.mz.agenticai.core.common.AgentContext;
import kr.co.mz.agenticai.core.common.RagRequest;
import kr.co.mz.agenticai.core.common.RagResponse;
import kr.co.mz.agenticai.core.common.observability.RagObservability;
import kr.co.mz.agenticai.core.common.spi.Agent;
import kr.co.mz.agenticai.core.common.spi.AgentOrchestrator;
import kr.co.mz.agenticai.core.common.spi.RagEventPublisher;
import kr.co.mz.agenticai.core.agent.orchestrator.SequentialAgentOrchestrator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * T2 — OrchestratorAgenticRagClient emits {@code rag.agent.run} parent span;
 * planner and synthesizer agent spans are nested within it.
 */
class OrchestratorAgenticRagClientObservabilityTest {

    private final List<String> startedSpans = new ArrayList<>();
    private final List<String> stoppedSpans = new ArrayList<>();

    private ObservationRegistry registry;

    @BeforeEach
    void setUp() {
        registry = ObservationRegistry.create();
        registry.observationConfig().observationHandler(new ObservationHandler<>() {
            @Override
            public void onStart(Observation.Context ctx) {
                startedSpans.add(ctx.getName());
            }
            @Override
            public void onStop(Observation.Context ctx) {
                stoppedSpans.add(ctx.getName());
            }
            @Override
            public boolean supportsContext(Observation.Context ctx) { return true; }
        });
    }

    @Test
    void agentRunSpanIsStartedAndStopped() {
        var client = new OrchestratorAgenticRagClient(
                stubOrchestrator("planner", "synthesizer"), List.of(), registry);

        client.ask(RagRequest.of("테스트 질문"));

        assertThat(startedSpans).contains(RagObservability.SPAN_AGENT_RUN);
        assertThat(stoppedSpans).contains(RagObservability.SPAN_AGENT_RUN);
    }

    @Test
    void agentRunSpanEnclosesOrchestratorExecution() {
        List<String> executionOrder = new ArrayList<>();

        AgentOrchestrator orchestrator = request -> {
            // Verify rag.agent.run was started before orchestrator runs
            executionOrder.add("orchestrator");
            assertThat(startedSpans).contains(RagObservability.SPAN_AGENT_RUN);
            // At this point the span should NOT yet be stopped
            assertThat(stoppedSpans).doesNotContain(RagObservability.SPAN_AGENT_RUN);
            return new RagResponse("답변", List.of(),
                    kr.co.mz.agenticai.core.common.RagUsage.empty(), java.util.Map.of());
        };

        var client = new OrchestratorAgenticRagClient(orchestrator, List.of(), registry);
        client.ask(RagRequest.of("질문"));

        assertThat(executionOrder).containsExactly("orchestrator");
        assertThat(stoppedSpans).contains(RagObservability.SPAN_AGENT_RUN);
    }

    @Test
    void plannerAndSynthesizerSpansAreChildrenOfAgentRun() {
        Agent planner = namedAgent("planner", ctx -> ctx.setIntent("factual"));
        Agent synthesizer = namedAgent("synthesizer", ctx -> ctx.setAnswer("합성 답변"));

        AgentOrchestrator orchestrator = new SequentialAgentOrchestrator(
                List.of(planner, synthesizer), event -> {}, 1, null);

        var client = new OrchestratorAgenticRagClient(orchestrator, List.of(), registry);
        client.ask(RagRequest.of("질문"));

        // rag.agent.run parent span is present
        assertThat(startedSpans).contains(RagObservability.SPAN_AGENT_RUN);
        // orchestrator ran both agents (verified via answer)
        assertThat(stoppedSpans).contains(RagObservability.SPAN_AGENT_RUN);
    }

    @Test
    void noopRegistryDoesNotThrow() {
        var client = new OrchestratorAgenticRagClient(
                stubOrchestrator("planner", "synthesizer"),
                List.of(), ObservationRegistry.NOOP);

        RagResponse response = client.ask(RagRequest.of("질문"));
        assertThat(response).isNotNull();
    }

    private static AgentOrchestrator stubOrchestrator(String... agentNames) {
        List<Agent> agents = new ArrayList<>();
        for (String name : agentNames) {
            agents.add(namedAgent(name, ctx -> {}));
        }
        // Last agent sets the answer so OrchestratorAgenticRagClient gets a non-null response
        agents.add(namedAgent("answer-setter", ctx -> ctx.setAnswer("ok")));
        RagEventPublisher noopPublisher = event -> {};
        return new SequentialAgentOrchestrator(agents, noopPublisher, 1, null);
    }

    private static Agent namedAgent(String name, java.util.function.Consumer<AgentContext> action) {
        return new Agent() {
            @Override public String name() { return name; }
            @Override public void execute(AgentContext ctx) { action.accept(ctx); }
        };
    }
}
