package kr.co.mz.agenticai.core.factcheck;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.ObservationRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import kr.co.mz.agenticai.core.common.observability.RagObservability;
import kr.co.mz.agenticai.core.common.spi.FactChecker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;

/**
 * T4 — FactCheck span emits {@code rag.factcheck.verdict} and
 * {@code rag.factcheck.confidence} attributes.
 */
class LlmFactCheckerObservabilityTest {

    private final List<String> startedSpans = new ArrayList<>();
    private final List<String> stoppedSpans = new ArrayList<>();
    private final Map<String, String> capturedAttrs = new java.util.LinkedHashMap<>();

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
                ctx.getLowCardinalityKeyValues().forEach(kv ->
                        capturedAttrs.put(ctx.getName() + ":" + kv.getKey(), kv.getValue()));
                ctx.getHighCardinalityKeyValues().forEach(kv ->
                        capturedAttrs.put(ctx.getName() + ":" + kv.getKey(), kv.getValue()));
            }
            @Override
            public boolean supportsContext(Observation.Context ctx) { return true; }
        });
    }

    @Test
    void factcheckSpanIsStartedAndStopped() {
        var checker = new LlmFactChecker(
                mockReplying("{\"grounded\":true,\"confidence\":0.85,\"reason\":\"ok\",\"supportingSourceIndexes\":[0]}"),
                KoreanFactCheckPrompts.VERIFY, 0.5, registry);

        checker.check(req("답변", new Document("s1", "근거", Map.of())));

        assertThat(startedSpans).contains(RagObservability.SPAN_FACTCHECK);
        assertThat(stoppedSpans).contains(RagObservability.SPAN_FACTCHECK);
    }

    @Test
    void factcheckSpanCarriesVerdictAndConfidenceAttributes() {
        var checker = new LlmFactChecker(
                mockReplying("{\"grounded\":true,\"confidence\":0.9,\"reason\":\"근거 충분\",\"supportingSourceIndexes\":[0]}"),
                KoreanFactCheckPrompts.VERIFY, 0.5, registry);

        checker.check(req("답변", new Document("s2", "근거 텍스트", Map.of())));

        assertThat(capturedAttrs)
                .containsKey(RagObservability.SPAN_FACTCHECK + ":" + RagObservability.ATTR_FACTCHECK_VERDICT);
        assertThat(capturedAttrs)
                .containsKey(RagObservability.SPAN_FACTCHECK + ":" + RagObservability.ATTR_FACTCHECK_CONFIDENCE);
    }

    @Test
    void factcheckVerdictAttributeReflectsGroundedResult() {
        var checker = new LlmFactChecker(
                mockReplying("{\"grounded\":true,\"confidence\":0.9,\"reason\":\"ok\",\"supportingSourceIndexes\":[0]}"),
                KoreanFactCheckPrompts.VERIFY, 0.5, registry);

        checker.check(req("답변", new Document("s3", "근거", Map.of())));

        String verdict = capturedAttrs.get(
                RagObservability.SPAN_FACTCHECK + ":" + RagObservability.ATTR_FACTCHECK_VERDICT);
        assertThat(verdict).isEqualTo("grounded");
    }

    @Test
    void factcheckVerdictAttributeReflectsUngroundedResult() {
        var checker = new LlmFactChecker(
                mockReplying("{\"grounded\":false,\"confidence\":0.2,\"reason\":\"no basis\",\"supportingSourceIndexes\":[]}"),
                KoreanFactCheckPrompts.VERIFY, 0.5, registry);

        checker.check(req("답변", new Document("s4", "무관한 텍스트", Map.of())));

        String verdict = capturedAttrs.get(
                RagObservability.SPAN_FACTCHECK + ":" + RagObservability.ATTR_FACTCHECK_VERDICT);
        assertThat(verdict).isEqualTo("ungrounded");
    }

    @Test
    void noopRegistryDoesNotThrow() {
        var checker = new LlmFactChecker(
                mockReplying("{\"grounded\":true,\"confidence\":0.8,\"reason\":\"ok\",\"supportingSourceIndexes\":[0]}"),
                KoreanFactCheckPrompts.VERIFY, 0.5, ObservationRegistry.NOOP);

        FactChecker.FactCheckResult result =
                checker.check(req("답변", new Document("s5", "근거", Map.of())));

        assertThat(result.grounded()).isTrue();
    }

    private static ChatModel mockReplying(String text) {
        ChatModel m = mock(ChatModel.class);
        when(m.call(any(Prompt.class))).thenReturn(
                new ChatResponse(List.of(new Generation(new AssistantMessage(text)))));
        return m;
    }

    private FactChecker.FactCheckRequest req(String answer, Document... sources) {
        return new FactChecker.FactCheckRequest(answer, List.of(sources), "원본 질문", Map.of());
    }
}
