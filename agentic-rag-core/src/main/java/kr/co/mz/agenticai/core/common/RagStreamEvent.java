package kr.co.mz.agenticai.core.common;

import java.util.Objects;

/**
 * Streaming protocol between {@code AgenticRagClient.askStream(...)} and its
 * caller. Sealed because the set of events the client emits is a closed
 * protocol; adding a new variant is a breaking API change by design.
 */
public sealed interface RagStreamEvent
        permits RagStreamEvent.TokenChunk,
                RagStreamEvent.CitationEmitted,
                RagStreamEvent.Completed,
                RagStreamEvent.Failed {

    /** A single token (or text delta) from the LLM. */
    record TokenChunk(String text) implements RagStreamEvent {
        public TokenChunk {
            Objects.requireNonNull(text, "text");
        }
    }

    /** A citation recognized in the answer stream. */
    record CitationEmitted(Citation citation) implements RagStreamEvent {
        public CitationEmitted {
            Objects.requireNonNull(citation, "citation");
        }
    }

    /** Final response when the stream finishes successfully. */
    record Completed(RagResponse response) implements RagStreamEvent {
        public Completed {
            Objects.requireNonNull(response, "response");
        }
    }

    /** Terminal error. */
    record Failed(Throwable error) implements RagStreamEvent {
        public Failed {
            Objects.requireNonNull(error, "error");
        }
    }
}
