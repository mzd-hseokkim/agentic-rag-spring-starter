package kr.co.mz.agenticai.core.agent.orchestrator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import kr.co.mz.agenticai.core.common.AgentContext;
import kr.co.mz.agenticai.core.common.RagRequest;
import kr.co.mz.agenticai.core.common.RagResponse;
import kr.co.mz.agenticai.core.common.event.RagEvent;
import kr.co.mz.agenticai.core.common.exception.AgenticRagException;
import kr.co.mz.agenticai.core.common.spi.Agent;
import kr.co.mz.agenticai.core.common.spi.RagEventPublisher;
import org.junit.jupiter.api.Test;

class SequentialAgentOrchestratorTest {

    private final CapturingPublisher events = new CapturingPublisher();

    @Test
    void runsAllAgentsInOrderAndReturnsAnswer() {
        List<String> trace = new ArrayList<>();
        Agent a1 = stub("intent", ctx -> { ctx.setIntent("factual"); trace.add("a1"); });
        Agent a2 = stub("retrieval", ctx -> trace.add("a2"));
        Agent a3 = stub("summary", ctx -> { ctx.setAnswer("정답"); trace.add("a3"); });
        Agent a4 = stub("validation", ctx -> { ctx.setValidationPassed(true); trace.add("a4"); });

        var orch = new SequentialAgentOrchestrator(
                List.of(a1, a2, a3, a4), events, 2, "retrieval");

        RagResponse response = orch.run(RagRequest.of("질문"));

        assertThat(trace).containsExactly("a1", "a2", "a3", "a4");
        assertThat(response.answer()).isEqualTo("정답");
        assertThat(response.attributes()).containsKey("agentTrace").containsEntry("iteration", 1);
    }

    @Test
    void retriesFromRetrievalAgentWhenValidationFails() {
        List<String> trace = new ArrayList<>();
        Agent intent = stub("intent", ctx -> trace.add("intent"));
        Agent retrieval = stub("retrieval", ctx -> trace.add("retrieval"));
        Agent summary = stub("summary", ctx -> { ctx.setAnswer("ans"); trace.add("summary"); });
        // First iteration fails, second iteration passes.
        Agent validation = stub("validation", ctx -> {
            trace.add("validation");
            ctx.setValidationPassed(ctx.iteration() >= 2);
        });

        var orch = new SequentialAgentOrchestrator(
                List.of(intent, retrieval, summary, validation), events, 3, "retrieval");

        RagResponse response = orch.run(RagRequest.of("q"));

        // Iteration 1: all four. Iteration 2: skip intent, run retrieval/summary/validation.
        assertThat(trace).containsExactly(
                "intent", "retrieval", "summary", "validation",
                "retrieval", "summary", "validation");
        assertThat(response.attributes()).containsEntry("iteration", 2);
    }

    @Test
    void stopsAtMaxIterationsEvenIfValidationKeepsFailing() {
        List<String> trace = new ArrayList<>();
        Agent retrieval = stub("retrieval", ctx -> trace.add("retrieval"));
        Agent validation = stub("validation", ctx -> {
            trace.add("validation");
            ctx.setValidationPassed(false);
        });

        var orch = new SequentialAgentOrchestrator(
                List.of(retrieval, validation), events, 2, "retrieval");

        RagResponse response = orch.run(RagRequest.of("q"));

        assertThat(response.attributes()).containsEntry("iteration", 2);
        // 2 iterations × 2 agents
        assertThat(trace).hasSize(4);
    }

    @Test
    void agentExceptionWrappedAsAgenticRagException() {
        Agent a = stub("intent", ctx -> { throw new RuntimeException("boom"); });

        var orch = new SequentialAgentOrchestrator(List.of(a), events, 1, null);

        assertThatThrownBy(() -> orch.run(RagRequest.of("q")))
                .isInstanceOf(AgenticRagException.class)
                .hasMessageContaining("intent");
    }

    @Test
    void rejectsBadConstruction() {
        assertThatThrownBy(() -> new SequentialAgentOrchestrator(List.of(), events, 1, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SequentialAgentOrchestrator(
                List.of(stub("a", ctx -> {})), events, 0, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static Agent stub(String name, java.util.function.Consumer<AgentContext> exec) {
        return new Agent() {
            @Override public String name() { return name; }
            @Override public void execute(AgentContext ctx) { exec.accept(ctx); }
        };
    }

    private static final class CapturingPublisher implements RagEventPublisher {
        final List<RagEvent> events = new ArrayList<>();
        @Override public void publish(RagEvent event) { events.add(event); }
    }
}
