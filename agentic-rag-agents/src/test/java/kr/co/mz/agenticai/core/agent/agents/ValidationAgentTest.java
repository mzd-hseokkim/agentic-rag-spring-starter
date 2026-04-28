package kr.co.mz.agenticai.core.agent.agents;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import kr.co.mz.agenticai.core.common.AgentContext;
import kr.co.mz.agenticai.core.common.Citation;
import kr.co.mz.agenticai.core.common.RagRequest;
import kr.co.mz.agenticai.core.common.event.FactCheckEvent;
import kr.co.mz.agenticai.core.common.event.RagEvent;
import kr.co.mz.agenticai.core.common.spi.FactChecker;
import kr.co.mz.agenticai.core.common.spi.RagEventPublisher;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

class ValidationAgentTest {

    private static final class CapturingPublisher implements RagEventPublisher {
        final List<RagEvent> events = new ArrayList<>();
        @Override public void publish(RagEvent event) { events.add(event); }
    }

    private static AgentContext contextWith(String answer, List<Document> sources) {
        var ctx = new AgentContext(RagRequest.of("질문"), "corr-1");
        if (answer != null) {
            ctx.setAnswer(answer);
        }
        ctx.setSelectedSources(sources);
        return ctx;
    }

    private static Document doc() {
        return new Document("d1", "내용", Map.of());
    }

    @Test
    void skipsWhenFactCheckerIsNull() {
        var events = new CapturingPublisher();
        AgentContext ctx = contextWith("답", List.of(doc()));

        new ValidationAgent(null, events).execute(ctx);

        assertThat(ctx.validationPassed()).isTrue();
        assertThat(ctx.trace()).containsExactly(ValidationAgent.CANONICAL_NAME + ":skip");
        assertThat(events.events).isEmpty();
    }

    @Test
    void skipsWhenSourcesEmpty() {
        FactChecker checker = mock(FactChecker.class);
        var events = new CapturingPublisher();
        AgentContext ctx = contextWith("답", List.of());

        new ValidationAgent(checker, events).execute(ctx);

        assertThat(ctx.validationPassed()).isTrue();
        verifyNoInteractions(checker);
        assertThat(events.events).isEmpty();
    }

    @Test
    void skipsWhenAnswerBlank() {
        FactChecker checker = mock(FactChecker.class);
        var events = new CapturingPublisher();
        AgentContext ctx = contextWith("   ", List.of(doc()));

        new ValidationAgent(checker, events).execute(ctx);

        assertThat(ctx.validationPassed()).isTrue();
        verifyNoInteractions(checker);
    }

    @Test
    void groundedAnswerSetsCitationsAndPublishesPassed() {
        FactChecker checker = mock(FactChecker.class);
        Citation cit = Citation.of("d1");
        when(checker.check(any())).thenReturn(new FactChecker.FactCheckResult(
                true, 0.92, List.of(cit), "ok", Map.of()));

        var events = new CapturingPublisher();
        AgentContext ctx = contextWith("답변", List.of(doc()));

        new ValidationAgent(checker, events).execute(ctx);

        assertThat(ctx.validationPassed()).isTrue();
        assertThat(ctx.citations()).containsExactly(cit);
        assertThat(events.events).hasSize(1)
                .first().isInstanceOf(FactCheckEvent.FactCheckPassed.class);
    }

    @Test
    void notGroundedPublishesFailedAndKeepsExistingCitations() {
        FactChecker checker = mock(FactChecker.class);
        when(checker.check(any())).thenReturn(new FactChecker.FactCheckResult(
                false, 0.21, List.of(), "근거 부족", Map.of()));

        var events = new CapturingPublisher();
        AgentContext ctx = contextWith("답변", List.of(doc()));
        Citation existing = Citation.of("prev");
        ctx.setCitations(List.of(existing));

        new ValidationAgent(checker, events).execute(ctx);

        assertThat(ctx.validationPassed()).isFalse();
        assertThat(ctx.validationReason()).isEqualTo("근거 부족");
        assertThat(ctx.citations()).containsExactly(existing);
        assertThat(events.events).hasSize(1)
                .first().isInstanceOf(FactCheckEvent.FactCheckFailed.class);
    }

    @Test
    void runsWithoutEventsPublisher() {
        FactChecker checker = mock(FactChecker.class);
        when(checker.check(any())).thenReturn(new FactChecker.FactCheckResult(
                true, 1.0, List.of(), "ok", Map.of()));

        AgentContext ctx = contextWith("답변", List.of(doc()));

        new ValidationAgent(checker, null).execute(ctx);

        assertThat(ctx.validationPassed()).isTrue();
        assertThat(ctx.trace()).containsExactly(ValidationAgent.CANONICAL_NAME);
    }
}
