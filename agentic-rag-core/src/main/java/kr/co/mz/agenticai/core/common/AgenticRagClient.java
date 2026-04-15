package kr.co.mz.agenticai.core.common;

import reactor.core.publisher.Flux;

/**
 * Public entry point: ask a question, get back an answer with citations.
 *
 * <p>Both methods should be safe to call from multiple threads.
 */
public interface AgenticRagClient {

    /** Synchronous request — blocks until the LLM finishes generating. */
    RagResponse ask(RagRequest request);

    /**
     * Streaming request. Emits {@link RagStreamEvent.TokenChunk} events for
     * each LLM token, then a single {@link RagStreamEvent.Completed} with
     * the assembled {@link RagResponse}, or {@link RagStreamEvent.Failed}
     * on error.
     */
    Flux<RagStreamEvent> askStream(RagRequest request);
}
