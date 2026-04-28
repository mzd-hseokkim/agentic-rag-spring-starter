package kr.co.mz.agenticai.core.agent.agents;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import kr.co.mz.agenticai.core.common.AgentContext;
import kr.co.mz.agenticai.core.common.RagMetadataKeys;
import kr.co.mz.agenticai.core.common.RagRequest;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

class InterpretationAgentTest {

    private static AgentContext context() {
        return new AgentContext(RagRequest.of("질문"), "corr-1");
    }

    @Test
    void emptyRetrievedYieldsEmptySelected() {
        AgentContext ctx = context();
        ctx.setRetrieved(List.of());

        new InterpretationAgent().execute(ctx);

        assertThat(ctx.selectedSources()).isEmpty();
        assertThat(ctx.trace()).containsExactly(InterpretationAgent.CANONICAL_NAME);
    }

    @Test
    void deduplicatesByParentDocumentIdAndPreservesOrder() {
        Document a1 = new Document("c1", "alpha",
                Map.of(RagMetadataKeys.PARENT_DOCUMENT_ID, "p-A"));
        Document a2 = new Document("c2", "alpha-2",
                Map.of(RagMetadataKeys.PARENT_DOCUMENT_ID, "p-A"));
        Document b = new Document("c3", "beta",
                Map.of(RagMetadataKeys.PARENT_DOCUMENT_ID, "p-B"));

        AgentContext ctx = context();
        ctx.setRetrieved(List.of(a1, b, a2));

        new InterpretationAgent().execute(ctx);

        assertThat(ctx.selectedSources()).extracting(Document::getId).containsExactly("c1", "c3");
    }

    @Test
    void droppedDocumentsHaveBlankOrNullText() {
        Document blank = new Document("blank", "   ", Map.of());
        Document good = new Document("good", "내용", Map.of());

        AgentContext ctx = context();
        ctx.setRetrieved(List.of(blank, good));

        new InterpretationAgent().execute(ctx);

        assertThat(ctx.selectedSources()).extracting(Document::getId).containsExactly("good");
    }

    @Test
    void usesChunkIdAsParentWhenMetadataMissing() {
        Document a = new Document("only-id", "내용", Map.of());

        AgentContext ctx = context();
        ctx.setRetrieved(List.of(a, a));

        new InterpretationAgent().execute(ctx);

        assertThat(ctx.selectedSources()).hasSize(1);
    }
}
