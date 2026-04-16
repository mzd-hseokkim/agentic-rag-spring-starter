package kr.co.mz.agenticai.core.agent.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import kr.co.mz.agenticai.core.common.RagRequest;
import kr.co.mz.agenticai.core.common.RagResponse;
import kr.co.mz.agenticai.core.common.RagStreamEvent;
import kr.co.mz.agenticai.core.common.event.LlmEvent;
import kr.co.mz.agenticai.core.common.event.RagEvent;
import kr.co.mz.agenticai.core.common.spi.FactChecker;
import kr.co.mz.agenticai.core.common.spi.RagEventPublisher;
import kr.co.mz.agenticai.core.common.spi.RetrieverRouter;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

class DefaultAgenticRagClientTest {

    private final CapturingPublisher events = new CapturingPublisher();

    private static ChatResponse respondWith(String text) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
    }

    @Test
    void askRetrievesGeneratesAndAttachesCitations() {
        RetrieverRouter router = mock(RetrieverRouter.class);
        when(router.retrieve(any())).thenReturn(List.of(
                new Document("c1", "벡터+BM25 결합", Map.of("source", "doc.md"))));

        ChatModel chat = mock(ChatModel.class);
        when(chat.call(any(Prompt.class))).thenReturn(respondWith("RRF로 결합합니다."));

        var client = new DefaultAgenticRagClient(
                router, chat, null, events,
                List.<kr.co.mz.agenticai.core.common.spi.Guardrail>of(),
                new DefaultAgenticRagClient.PromptConfig(KoreanRagPrompts.SYSTEM, KoreanRagPrompts.USER, 5));

        RagResponse response = client.ask(RagRequest.of("RRF란?"));

        assertThat(response.answer()).isEqualTo("RRF로 결합합니다.");
        assertThat(response.citations()).hasSize(1);
        assertThat(response.citations().get(0).documentId()).isEqualTo("c1");
        assertThat(events.events)
                .hasAtLeastOneElementOfType(LlmEvent.LlmRequested.class)
                .hasAtLeastOneElementOfType(LlmEvent.LlmResponded.class);
    }

    @Test
    void askUsesFactCheckerCitationsWhenAvailable() {
        RetrieverRouter router = mock(RetrieverRouter.class);
        when(router.retrieve(any())).thenReturn(List.of(
                new Document("c1", "alpha", Map.of()),
                new Document("c2", "beta", Map.of())));

        ChatModel chat = mock(ChatModel.class);
        when(chat.call(any(Prompt.class))).thenReturn(respondWith("alpha만 사실"));

        FactChecker factChecker = req -> new FactChecker.FactCheckResult(
                true, 0.9,
                List.of(kr.co.mz.agenticai.core.common.Citation.of("c1")),
                "ok", Map.of());

        var client = new DefaultAgenticRagClient(
                router, chat, factChecker, events,
                List.<kr.co.mz.agenticai.core.common.spi.Guardrail>of(),
                new DefaultAgenticRagClient.PromptConfig(KoreanRagPrompts.SYSTEM, KoreanRagPrompts.USER, 5));

        RagResponse response = client.ask(RagRequest.of("질문"));

        assertThat(response.citations()).hasSize(1);
        assertThat(response.citations().get(0).documentId()).isEqualTo("c1");
    }

    @Test
    void askStreamEmitsTokensThenCompleted() {
        RetrieverRouter router = mock(RetrieverRouter.class);
        when(router.retrieve(any())).thenReturn(List.of(new Document("c1", "x", Map.of())));

        ChatModel chat = mock(ChatModel.class);
        when(chat.stream(any(Prompt.class))).thenReturn(Flux.just(
                respondWith("안녕"), respondWith(" 세계"), respondWith("입니다.")));

        var client = new DefaultAgenticRagClient(
                router, chat, null, events,
                List.<kr.co.mz.agenticai.core.common.spi.Guardrail>of(),
                new DefaultAgenticRagClient.PromptConfig(KoreanRagPrompts.SYSTEM, KoreanRagPrompts.USER, 3));

        StepVerifier.create(client.askStream(RagRequest.of("hi")))
                .expectNextMatches(e -> e instanceof RagStreamEvent.TokenChunk t && t.text().equals("안녕"))
                .expectNextMatches(e -> e instanceof RagStreamEvent.TokenChunk t && t.text().equals(" 세계"))
                .expectNextMatches(e -> e instanceof RagStreamEvent.TokenChunk t && t.text().equals("입니다."))
                .expectNextMatches(e -> e instanceof RagStreamEvent.Completed c
                        && c.response().answer().equals("안녕 세계입니다."))
                .verifyComplete();

        // Each token should publish a LlmTokenStreamed event.
        long tokenEvents = events.events.stream()
                .filter(e -> e instanceof LlmEvent.LlmTokenStreamed)
                .count();
        assertThat(tokenEvents).isEqualTo(3);
    }

    @Test
    void askStreamPropagatesErrorAsFailedEvent() {
        RetrieverRouter router = mock(RetrieverRouter.class);
        when(router.retrieve(any())).thenReturn(List.of());

        ChatModel chat = mock(ChatModel.class);
        when(chat.stream(any(Prompt.class))).thenReturn(Flux.error(new RuntimeException("kaboom")));

        var client = new DefaultAgenticRagClient(
                router, chat, null, events,
                List.<kr.co.mz.agenticai.core.common.spi.Guardrail>of(),
                new DefaultAgenticRagClient.PromptConfig(KoreanRagPrompts.SYSTEM, KoreanRagPrompts.USER, 3));

        StepVerifier.create(client.askStream(RagRequest.of("hi")))
                .expectNextMatches(e -> e instanceof RagStreamEvent.Failed f
                        && f.error().getMessage().equals("kaboom"))
                .verifyComplete();
    }

    @Test
    void overrideTopKWinsOverDefault() {
        RetrieverRouter router = mock(RetrieverRouter.class);
        when(router.retrieve(any())).thenReturn(List.of());

        ChatModel chat = mock(ChatModel.class);
        when(chat.call(any(Prompt.class))).thenReturn(respondWith("ok"));

        var client = new DefaultAgenticRagClient(
                router, chat, null, events,
                List.<kr.co.mz.agenticai.core.common.spi.Guardrail>of(),
                new DefaultAgenticRagClient.PromptConfig(KoreanRagPrompts.SYSTEM, KoreanRagPrompts.USER, 5));

        client.ask(RagRequest.builder()
                .query("q")
                .overrides(new kr.co.mz.agenticai.core.common.RagOverrides(
                        null, null, null, null, null, 12, null, Map.of()))
                .build());

        org.mockito.ArgumentCaptor<RetrieverRouter.Query> captor =
                org.mockito.ArgumentCaptor.forClass(RetrieverRouter.Query.class);
        verify(router, times(1)).retrieve(captor.capture());
        assertThat(captor.getValue().topK()).isEqualTo(12);
    }

    private static final class CapturingPublisher implements RagEventPublisher {
        final List<RagEvent> events = new ArrayList<>();
        @Override public void publish(RagEvent event) { events.add(event); }
    }
}
