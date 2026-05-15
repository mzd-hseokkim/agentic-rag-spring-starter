package kr.co.mz.agenticai.core.agent.client;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.tracing.Tracer;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import kr.co.mz.agenticai.core.common.AgenticRagClient;
import kr.co.mz.agenticai.core.common.Citation;
import kr.co.mz.agenticai.core.common.RagRequest;
import kr.co.mz.agenticai.core.common.RagResponse;
import kr.co.mz.agenticai.core.common.RagStreamEvent;
import kr.co.mz.agenticai.core.common.RagUsage;
import kr.co.mz.agenticai.core.common.event.FactCheckEvent;
import kr.co.mz.agenticai.core.common.event.LlmEvent;
import kr.co.mz.agenticai.core.common.exception.AgenticRagException;
import kr.co.mz.agenticai.core.common.guardrail.Guardrails;
import kr.co.mz.agenticai.core.common.observability.RagBaggage;
import kr.co.mz.agenticai.core.common.observability.RagObservability;
import kr.co.mz.agenticai.core.common.spi.FactChecker;
import kr.co.mz.agenticai.core.common.spi.Guardrail;
import kr.co.mz.agenticai.core.common.spi.RagEventPublisher;
import kr.co.mz.agenticai.core.common.spi.RetrieverRouter;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import reactor.core.publisher.Flux;

/**
 * Single-pass RAG client: retrieve → render prompt → call (or stream) LLM
 * → optionally fact-check → return {@link RagResponse}. Multi-step agent
 * behavior is the orchestrator's job; this is the baseline.
 */
public final class DefaultAgenticRagClient implements AgenticRagClient {

    private static final String PROVIDER = "spring-ai";

    private final RetrieverRouter router;
    private final ChatModel chatModel;
    private final FactChecker factChecker;
    private final RagEventPublisher events;
    private final List<Guardrail> guardrails;
    private final String systemPrompt;
    private final String userPromptTemplate;
    private final int defaultTopK;
    private final ObservationRegistry observationRegistry;
    private final Tracer tracer;

    public DefaultAgenticRagClient(
            RetrieverRouter router,
            ChatModel chatModel,
            FactChecker factChecker,
            RagEventPublisher events,
            List<Guardrail> guardrails,
            PromptConfig promptConfig) {
        this(router, chatModel, factChecker, events, guardrails, promptConfig, ObservationRegistry.NOOP, null);
    }

    public DefaultAgenticRagClient(
            RetrieverRouter router,
            ChatModel chatModel,
            FactChecker factChecker,
            RagEventPublisher events,
            List<Guardrail> guardrails,
            PromptConfig promptConfig,
            ObservationRegistry observationRegistry) {
        this(router, chatModel, factChecker, events, guardrails, promptConfig, observationRegistry, null);
    }

    // S107: the orchestration entrypoint wires independently-swappable Spring DI
    // collaborators (router, chat, fact-checker, events, guardrails, prompts,
    // observability, tracing). A config object would obscure the DI surface.
    @SuppressWarnings("java:S107")
    public DefaultAgenticRagClient(
            RetrieverRouter router,
            ChatModel chatModel,
            FactChecker factChecker,
            RagEventPublisher events,
            List<Guardrail> guardrails,
            PromptConfig promptConfig,
            ObservationRegistry observationRegistry,
            Tracer tracer) {
        Objects.requireNonNull(promptConfig, "promptConfig");
        this.router = Objects.requireNonNull(router, "router");
        this.chatModel = Objects.requireNonNull(chatModel, "chatModel");
        this.factChecker = factChecker;
        this.events = Objects.requireNonNull(events, "events");
        this.guardrails = guardrails == null ? List.of() : List.copyOf(guardrails);
        this.systemPrompt = promptConfig.systemPrompt();
        this.userPromptTemplate = promptConfig.userPromptTemplate();
        this.defaultTopK = promptConfig.defaultTopK();
        this.observationRegistry = observationRegistry != null ? observationRegistry : ObservationRegistry.NOOP;
        this.tracer = tracer;
    }

    /** Groups the answer-generation prompt settings so the public constructor stays under 7 params. */
    public record PromptConfig(String systemPrompt, String userPromptTemplate, int defaultTopK) {
        public PromptConfig {
            Objects.requireNonNull(systemPrompt, "systemPrompt");
            Objects.requireNonNull(userPromptTemplate, "userPromptTemplate");
            if (!userPromptTemplate.contains("{query}") || !userPromptTemplate.contains("{sources}")) {
                throw new IllegalArgumentException(
                        "userPromptTemplate must contain {query} and {sources} placeholders");
            }
            if (defaultTopK <= 0) {
                throw new IllegalArgumentException("defaultTopK must be > 0");
            }
        }
    }

    @Override
    public RagResponse ask(RagRequest request) {
        Objects.requireNonNull(request, "request");
        String correlationId = UUID.randomUUID().toString();

        try (AutoCloseable ignored = RagBaggage.set(tracer, correlationId)) {
            return doAsk(request, correlationId);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new AgenticRagException("Baggage scope close failed", e);
        }
    }

    private RagResponse doAsk(RagRequest request, String correlationId) {
        Guardrails.Outcome inputOutcome = Guardrails.apply(guardrails, Guardrail.Stage.INPUT, request.query());
        if (inputOutcome.blocked()) {
            return blockedResponse(inputOutcome);
        }
        RagRequest sanitizedRequest = withQuery(request, inputOutcome.text());

        List<Document> sources = retrieve(sanitizedRequest);
        Prompt prompt = buildPrompt(sanitizedRequest.query(), sources);

        long llmStarted = System.currentTimeMillis();
        events.publish(new LlmEvent.LlmRequested(
                PROVIDER, className(chatModel),
                renderedLength(prompt), Instant.now(), correlationId));

        Observation llmObs = RagObservability.start(RagObservability.SPAN_LLM_CALL, observationRegistry);
        llmObs.lowCardinalityKeyValue(RagObservability.ATTR_LLM_PROVIDER, PROVIDER);
        llmObs.lowCardinalityKeyValue(RagObservability.ATTR_LLM_MODEL_CLASS, className(chatModel));
        llmObs.highCardinalityKeyValue(RagObservability.ATTR_CORRELATION_ID, correlationId);
        String answer;
        try {
            ChatResponse resp = chatModel.call(prompt);
            answer = resp.getResult().getOutput().getText();
        } catch (RuntimeException e) {
            llmObs.error(e);
            throw new AgenticRagException("LLM call failed", e);
        } finally {
            llmObs.stop();
        }
        events.publish(new LlmEvent.LlmResponded(
                PROVIDER, className(chatModel), 0L, 0L,
                System.currentTimeMillis() - llmStarted, Instant.now(), correlationId));

        Guardrails.Outcome outputOutcome = Guardrails.apply(
                guardrails, Guardrail.Stage.OUTPUT, answer == null ? "" : answer);
        if (outputOutcome.blocked()) {
            return blockedResponse(outputOutcome);
        }
        return buildResponse(sanitizedRequest.query(), outputOutcome.text(), sources);
    }

    @Override
    public Flux<RagStreamEvent> askStream(RagRequest request) {
        Objects.requireNonNull(request, "request");
        return Flux.defer(() -> {
            String correlationId = UUID.randomUUID().toString();
            // Set baggage so downstream spans (e.g. HybridRetrieverRouter) share the same correlation-id.
            AutoCloseable baggageScope = RagBaggage.set(tracer, correlationId);

            Guardrails.Outcome inputOutcome = Guardrails.apply(
                    guardrails, Guardrail.Stage.INPUT, request.query());
            if (inputOutcome.blocked()) {
                try { baggageScope.close(); } catch (Exception ignored) { /* no-op */ }
                return Flux.just((RagStreamEvent) new RagStreamEvent.Completed(blockedResponse(inputOutcome)));
            }
            RagRequest sanitizedRequest = withQuery(request, inputOutcome.text());

            List<Document> sources = retrieve(sanitizedRequest);
            Prompt prompt = buildPrompt(sanitizedRequest.query(), sources);

            events.publish(new LlmEvent.LlmRequested(
                    PROVIDER, className(chatModel),
                    renderedLength(prompt), Instant.now(), correlationId));

            Observation streamObs = RagObservability.start(RagObservability.SPAN_LLM_CALL, observationRegistry);
            streamObs.lowCardinalityKeyValue(RagObservability.ATTR_LLM_PROVIDER, PROVIDER);
            streamObs.lowCardinalityKeyValue(RagObservability.ATTR_LLM_MODEL_CLASS, className(chatModel));
            streamObs.highCardinalityKeyValue(RagObservability.ATTR_CORRELATION_ID, correlationId);

            StringBuilder accumulated = new StringBuilder();
            AtomicReference<Long> started = new AtomicReference<>(System.currentTimeMillis());

            Flux<RagStreamEvent> tokens = chatModel.stream(prompt)
                    .map(resp -> resp.getResult().getOutput().getText())
                    .filter(t -> t != null && !t.isEmpty())
                    .doOnNext(token -> {
                        accumulated.append(token);
                        streamObs.event(Observation.Event.of(RagObservability.EVENT_LLM_TOKEN));
                        events.publish(new LlmEvent.LlmTokenStreamed(
                                PROVIDER, className(chatModel), token,
                                Instant.now(), correlationId));
                    })
                    .map(token -> (RagStreamEvent) new RagStreamEvent.TokenChunk(token));

            Flux<RagStreamEvent> tail = Flux.defer(() -> {
                streamObs.stop();
                try { baggageScope.close(); } catch (Exception ignored) { /* no-op */ }
                events.publish(new LlmEvent.LlmResponded(
                        PROVIDER, className(chatModel), 0L, 0L,
                        System.currentTimeMillis() - started.get(),
                        Instant.now(), correlationId));
                Guardrails.Outcome outputOutcome = Guardrails.apply(
                        guardrails, Guardrail.Stage.OUTPUT, accumulated.toString());
                RagResponse response = outputOutcome.blocked()
                        ? blockedResponse(outputOutcome)
                        : buildResponse(sanitizedRequest.query(), outputOutcome.text(), sources);
                return Flux.just(new RagStreamEvent.Completed(response));
            });

            return tokens.concatWith(tail)
                    .onErrorResume(e -> {
                        streamObs.error(e);
                        streamObs.stop();
                        try { baggageScope.close(); } catch (Exception ignored) { /* no-op */ }
                        return Flux.just(new RagStreamEvent.Failed(e));
                    });
        });
    }

    private List<Document> retrieve(RagRequest request) {
        int topK = topKFor(request);
        return router.retrieve(new RetrieverRouter.Query(
                request.query(), topK, request.metadataFilters(), request.attributes()));
    }

    private int topKFor(RagRequest request) {
        Integer override = request.overrides() == null ? null : request.overrides().topK();
        return override != null && override > 0 ? override : defaultTopK;
    }

    private Prompt buildPrompt(String query, List<Document> sources) {
        String renderedSources = renderSources(sources);
        String userText = userPromptTemplate
                .replace("{sources}", renderedSources)
                .replace("{query}", query);
        return new Prompt(List.of(new SystemMessage(systemPrompt), new UserMessage(userText)));
    }

    private static String renderSources(List<Document> sources) {
        if (sources.isEmpty()) {
            return "(없음)";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < sources.size(); i++) {
            Document d = sources.get(i);
            String src = String.valueOf(d.getMetadata().getOrDefault("source", d.getId()));
            sb.append('[').append(i).append("] (source: ").append(src).append(")\n");
            sb.append(d.getText() == null ? "" : d.getText()).append("\n\n");
        }
        return sb.toString();
    }

    private RagResponse buildResponse(String query, String answer, List<Document> sources) {
        List<Citation> citations;
        if (factChecker != null && !sources.isEmpty()) {
            long started = System.currentTimeMillis();
            FactChecker.FactCheckResult result = factChecker.check(
                    new FactChecker.FactCheckRequest(answer, sources, query, Map.of()));
            long elapsed = System.currentTimeMillis() - started;
            String summary = answer == null ? "" : answer.substring(0, Math.min(answer.length(), 80));
            if (result.grounded()) {
                events.publish(new FactCheckEvent.FactCheckPassed(
                        summary, result.confidence(), result.citations().size(),
                        elapsed, Instant.now(), UUID.randomUUID().toString()));
            } else {
                events.publish(new FactCheckEvent.FactCheckFailed(
                        summary, result.confidence(), result.reason(),
                        elapsed, Instant.now(), UUID.randomUUID().toString()));
            }
            citations = result.citations().isEmpty() ? Citation.fromDocuments(sources) : result.citations();
        } else {
            citations = Citation.fromDocuments(sources);
        }
        return new RagResponse(answer, citations, RagUsage.empty(), Map.of());
    }

    private static RagRequest withQuery(RagRequest original, String newQuery) {
        if (newQuery == null || newQuery.equals(original.query())) {
            return original;
        }
        return RagRequest.builder()
                .query(newQuery)
                .sessionId(original.sessionId())
                .userId(original.userId())
                .metadataFilters(original.metadataFilters())
                .overrides(original.overrides())
                .streaming(original.streaming())
                .attributes(original.attributes())
                .build();
    }

    private static RagResponse blockedResponse(Guardrails.Outcome outcome) {
        Map<String, Object> attrs = new java.util.HashMap<>();
        attrs.put("blocked", true);
        attrs.put("blockedBy", outcome.blockedBy());
        attrs.put("guardrailReason", outcome.reason());
        return new RagResponse("", List.of(), RagUsage.empty(), attrs);
    }

    private static int renderedLength(Prompt prompt) {
        return prompt.getInstructions().stream()
                .mapToInt(m -> m.getText() == null ? 0 : m.getText().length())
                .sum();
    }

    private static String className(Object o) {
        return o.getClass().getSimpleName();
    }
}
