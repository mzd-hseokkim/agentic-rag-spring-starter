package kr.co.mz.agenticai.core.agent.agents;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import kr.co.mz.agenticai.core.common.AgentContext;
import kr.co.mz.agenticai.core.common.RagOverrides;
import kr.co.mz.agenticai.core.common.RagRequest;
import kr.co.mz.agenticai.core.common.spi.RetrieverRouter;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.document.Document;

class RetrievalAgentTest {

    @Test
    void rejectsBadConstruction() {
        var router = mock(RetrieverRouter.class);
        assertThatThrownBy(() -> new RetrievalAgent(router, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void skipsWhenIntentIsConversational() {
        RetrieverRouter router = mock(RetrieverRouter.class);
        var ctx = new AgentContext(RagRequest.of("hi"), "c1");
        ctx.setIntent("conversational");

        new RetrievalAgent(router, 5).execute(ctx);

        assertThat(ctx.retrieved()).isEmpty();
        assertThat(ctx.trace()).containsExactly(RetrievalAgent.CANONICAL_NAME + ":skip");
        verifyNoInteractions(router);
    }

    @Test
    void usesDefaultTopKWhenNoOverride() {
        RetrieverRouter router = mock(RetrieverRouter.class);
        Document hit = new Document("d", "x", Map.of());
        when(router.retrieve(any())).thenReturn(List.of(hit));

        var ctx = new AgentContext(RagRequest.of("질문"), "c1");
        ctx.setIntent("factual");

        new RetrievalAgent(router, 7).execute(ctx);

        ArgumentCaptor<RetrieverRouter.Query> captor = ArgumentCaptor.forClass(RetrieverRouter.Query.class);
        verify(router).retrieve(captor.capture());
        assertThat(captor.getValue().topK()).isEqualTo(7);
        assertThat(captor.getValue().text()).isEqualTo("질문");
        assertThat(ctx.retrieved()).extracting(Document::getId).containsExactly("d");
        assertThat(ctx.trace()).containsExactly(RetrievalAgent.CANONICAL_NAME);
    }

    @Test
    void honorsTopKOverrideFromRequest() {
        RetrieverRouter router = mock(RetrieverRouter.class);
        when(router.retrieve(any())).thenReturn(List.of());

        RagOverrides overrides = new RagOverrides(
                null, null, null, null, null, 3, null, Map.of());
        RagRequest req = RagRequest.builder().query("q").overrides(overrides).build();
        var ctx = new AgentContext(req, "c1");
        ctx.setIntent("factual");

        new RetrievalAgent(router, 7).execute(ctx);

        ArgumentCaptor<RetrieverRouter.Query> captor = ArgumentCaptor.forClass(RetrieverRouter.Query.class);
        verify(router).retrieve(captor.capture());
        assertThat(captor.getValue().topK()).isEqualTo(3);
    }

    @Test
    void usesRefinedQueryWhenSet() {
        RetrieverRouter router = mock(RetrieverRouter.class);
        when(router.retrieve(any())).thenReturn(List.of());

        var ctx = new AgentContext(RagRequest.of("원본"), "c1");
        ctx.setIntent("factual");
        ctx.setRefinedQuery("정제된");

        new RetrievalAgent(router, 5).execute(ctx);

        ArgumentCaptor<RetrieverRouter.Query> captor = ArgumentCaptor.forClass(RetrieverRouter.Query.class);
        verify(router).retrieve(captor.capture());
        assertThat(captor.getValue().text()).isEqualTo("정제된");
    }
}
