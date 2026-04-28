package kr.co.mz.agenticai.core.agent.agents;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import kr.co.mz.agenticai.core.common.AgentContext;
import kr.co.mz.agenticai.core.common.RagRequest;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

class DataConstructionAgentTest {

    private static AgentContext context() {
        return new AgentContext(RagRequest.of("질문"), "corr-1");
    }

    private static Document doc(String id) {
        return new Document(id, "내용 " + id, Map.of());
    }

    @Test
    void rejectsNonPositiveMax() {
        assertThatThrownBy(() -> new DataConstructionAgent(0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DataConstructionAgent(-1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void truncatesToMaxSources() {
        AgentContext ctx = context();
        ctx.setSelectedSources(List.of(doc("a"), doc("b"), doc("c"), doc("d")));

        new DataConstructionAgent(2).execute(ctx);

        assertThat(ctx.selectedSources()).extracting(Document::getId).containsExactly("a", "b");
    }

    @Test
    void leavesShorterListUntouched() {
        AgentContext ctx = context();
        ctx.setSelectedSources(List.of(doc("a"), doc("b")));

        new DataConstructionAgent(5).execute(ctx);

        assertThat(ctx.selectedSources()).extracting(Document::getId).containsExactly("a", "b");
    }

    @Test
    void recordsStep() {
        AgentContext ctx = context();
        ctx.setSelectedSources(List.of(doc("a")));

        new DataConstructionAgent(3).execute(ctx);

        assertThat(ctx.trace()).containsExactly(DataConstructionAgent.CANONICAL_NAME);
    }
}
