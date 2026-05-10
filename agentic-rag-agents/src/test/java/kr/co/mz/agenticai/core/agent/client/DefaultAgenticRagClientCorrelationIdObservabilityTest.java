package kr.co.mz.agenticai.core.agent.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.ObservationRegistry;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import kr.co.mz.agenticai.core.common.RagRequest;
import kr.co.mz.agenticai.core.common.observability.RagObservability;
import kr.co.mz.agenticai.core.common.spi.RetrieverRouter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

/**
 * T5 — correlation-id is propagated into the LLM call span as a
 * high-cardinality attribute on every query path (sync and streaming).
 */
class DefaultAgenticRagClientCorrelationIdObservabilityTest {

    private final Map<String, String> capturedHighCardAttrs = new LinkedHashMap<>();

    private ObservationRegistry registry;

    @BeforeEach
    void setUp() {
        registry = ObservationRegistry.create();
        registry.observationConfig().observationHandler(new ObservationHandler<>() {
            @Override
            public void onStop(Observation.Context ctx) {
                ctx.getHighCardinalityKeyValues().forEach(kv ->
                        capturedHighCardAttrs.put(ctx.getName() + ":" + kv.getKey(), kv.getValue()));
            }
            @Override
            public boolean supportsContext(Observation.Context ctx) { return true; }
        });
    }

    @Test
    void syncAskPropagatesCorrelationIdInLlmCallSpan() {
        RetrieverRouter router = mock(RetrieverRouter.class);
        when(router.retrieve(any())).thenReturn(List.of(
                new Document("d1", "내용", Map.of())));

        ChatModel chat = mock(ChatModel.class);
        when(chat.call(any(Prompt.class))).thenReturn(
                new ChatResponse(List.of(new Generation(new AssistantMessage("답변")))));

        var client = new DefaultAgenticRagClient(
                router, chat, null, event -> {}, List.of(),
                new DefaultAgenticRagClient.PromptConfig(
                        KoreanRagPrompts.SYSTEM, KoreanRagPrompts.USER, 5),
                registry, null);

        client.ask(RagRequest.of("질문"));

        String attrKey = RagObservability.SPAN_LLM_CALL + ":" + RagObservability.ATTR_CORRELATION_ID;
        assertThat(capturedHighCardAttrs).containsKey(attrKey);
        assertThat(capturedHighCardAttrs.get(attrKey)).isNotBlank();
    }

    @Test
    void streamingAskPropagatesCorrelationIdInLlmCallSpan() {
        RetrieverRouter router = mock(RetrieverRouter.class);
        when(router.retrieve(any())).thenReturn(List.of());

        ChatModel chat = mock(ChatModel.class);
        when(chat.stream(any(Prompt.class))).thenReturn(Flux.<ChatResponse>just(
                new ChatResponse(List.of(new Generation(new AssistantMessage("스트림"))))));

        var client = new DefaultAgenticRagClient(
                router, chat, null, event -> {}, List.of(),
                new DefaultAgenticRagClient.PromptConfig(
                        KoreanRagPrompts.SYSTEM, KoreanRagPrompts.USER, 5),
                registry, null);

        StepVerifier.create(client.askStream(RagRequest.of("질문")))
                .expectNextCount(2)
                .verifyComplete();

        String attrKey = RagObservability.SPAN_LLM_CALL + ":" + RagObservability.ATTR_CORRELATION_ID;
        assertThat(capturedHighCardAttrs).containsKey(attrKey);
        assertThat(capturedHighCardAttrs.get(attrKey)).isNotBlank();
    }

    @Test
    void eachRequestGetsDistinctCorrelationId() {
        RetrieverRouter router = mock(RetrieverRouter.class);
        when(router.retrieve(any())).thenReturn(List.of());

        ChatModel chat = mock(ChatModel.class);
        when(chat.call(any(Prompt.class))).thenReturn(
                new ChatResponse(List.of(new Generation(new AssistantMessage("답변")))));

        List<String> correlationIds = new ArrayList<>();
        ObservationRegistry trackingRegistry = ObservationRegistry.create();
        trackingRegistry.observationConfig().observationHandler(new ObservationHandler<>() {
            @Override
            public void onStop(Observation.Context ctx) {
                if (RagObservability.SPAN_LLM_CALL.equals(ctx.getName())) {
                    ctx.getHighCardinalityKeyValues().forEach(kv -> {
                        if (RagObservability.ATTR_CORRELATION_ID.equals(kv.getKey())) {
                            correlationIds.add(kv.getValue());
                        }
                    });
                }
            }
            @Override
            public boolean supportsContext(Observation.Context ctx) { return true; }
        });

        var client = new DefaultAgenticRagClient(
                router, chat, null, event -> {}, List.of(),
                new DefaultAgenticRagClient.PromptConfig(
                        KoreanRagPrompts.SYSTEM, KoreanRagPrompts.USER, 5),
                trackingRegistry, null);

        client.ask(RagRequest.of("첫 번째 질문"));
        client.ask(RagRequest.of("두 번째 질문"));

        assertThat(correlationIds).hasSize(2);
        assertThat(correlationIds.get(0)).isNotEqualTo(correlationIds.get(1));
    }
}
