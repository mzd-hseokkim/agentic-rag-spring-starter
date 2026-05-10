package kr.co.mz.agenticai.core.agent.client;

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
import kr.co.mz.agenticai.core.common.RagRequest;
import kr.co.mz.agenticai.core.common.RagStreamEvent;
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
 * T3 — streaming path emits {@code rag.llm.token} observation event for each
 * token chunk produced by the {@link ChatModel}.
 */
class DefaultAgenticRagClientStreamObservabilityTest {

    private final List<String> startedSpans = new ArrayList<>();
    private final List<String> stoppedSpans = new ArrayList<>();
    private final List<String> capturedEvents = new ArrayList<>();

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
            public void onEvent(Observation.Event event, Observation.Context ctx) {
                capturedEvents.add(event.getName());
            }
            @Override
            public boolean supportsContext(Observation.Context ctx) { return true; }
        });
    }

    @Test
    void streamEmitsLlmTokenEventsForEachToken() {
        RetrieverRouter router = mock(RetrieverRouter.class);
        when(router.retrieve(any())).thenReturn(List.of(
                new Document("d1", "문서 내용", Map.of())));

        ChatModel chat = mock(ChatModel.class);
        when(chat.stream(any(Prompt.class))).thenReturn(Flux.<ChatResponse>just(
                tokenResponse("안녕"),
                tokenResponse("하세"),
                tokenResponse("요")));

        var client = new DefaultAgenticRagClient(
                router, chat, null, event -> {}, List.of(),
                new DefaultAgenticRagClient.PromptConfig(
                        KoreanRagPrompts.SYSTEM, KoreanRagPrompts.USER, 5),
                registry, null);

        StepVerifier.create(client.askStream(RagRequest.of("질문")))
                .expectNextMatches(e -> e instanceof RagStreamEvent.TokenChunk)
                .expectNextMatches(e -> e instanceof RagStreamEvent.TokenChunk)
                .expectNextMatches(e -> e instanceof RagStreamEvent.TokenChunk)
                .expectNextMatches(e -> e instanceof RagStreamEvent.Completed)
                .verifyComplete();

        assertThat(capturedEvents)
                .filteredOn(name -> name.equals(RagObservability.EVENT_LLM_TOKEN))
                .hasSize(3);
    }

    @Test
    void streamEmitsLlmCallSpanAndStopsAfterComplete() {
        RetrieverRouter router = mock(RetrieverRouter.class);
        when(router.retrieve(any())).thenReturn(List.of());

        ChatModel chat = mock(ChatModel.class);
        when(chat.stream(any(Prompt.class))).thenReturn(Flux.<ChatResponse>just(tokenResponse("토큰")));

        var client = new DefaultAgenticRagClient(
                router, chat, null, event -> {}, List.of(),
                new DefaultAgenticRagClient.PromptConfig(
                        KoreanRagPrompts.SYSTEM, KoreanRagPrompts.USER, 5),
                registry, null);

        StepVerifier.create(client.askStream(RagRequest.of("질문")))
                .expectNextCount(2)
                .verifyComplete();

        assertThat(startedSpans).contains(RagObservability.SPAN_LLM_CALL);
        assertThat(stoppedSpans).contains(RagObservability.SPAN_LLM_CALL);
    }

    @Test
    void streamWithNoopRegistryDoesNotThrow() {
        RetrieverRouter router = mock(RetrieverRouter.class);
        when(router.retrieve(any())).thenReturn(List.of());

        ChatModel chat = mock(ChatModel.class);
        when(chat.stream(any(Prompt.class))).thenReturn(Flux.<ChatResponse>just(tokenResponse("토큰")));

        var client = new DefaultAgenticRagClient(
                router, chat, null, event -> {}, List.of(),
                new DefaultAgenticRagClient.PromptConfig(
                        KoreanRagPrompts.SYSTEM, KoreanRagPrompts.USER, 5),
                ObservationRegistry.NOOP, null);

        StepVerifier.create(client.askStream(RagRequest.of("질문")))
                .expectNextCount(2)
                .verifyComplete();
    }

    private static ChatResponse tokenResponse(String text) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
    }
}
