package kr.co.mz.agenticai.core.common;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Synchronous result of an Agentic RAG invocation. */
public record RagResponse(
        String answer,
        List<Citation> citations,
        RagUsage usage,
        Map<String, Object> attributes) {

    public RagResponse {
        Objects.requireNonNull(answer, "answer must not be null");
        citations = citations == null ? List.of() : List.copyOf(citations);
        usage = usage == null ? RagUsage.empty() : usage;
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String answer;
        private List<Citation> citations = List.of();
        private RagUsage usage = RagUsage.empty();
        private Map<String, Object> attributes = new HashMap<>();

        public Builder answer(String answer) {
            this.answer = answer;
            return this;
        }

        public Builder citations(List<Citation> citations) {
            this.citations = citations == null ? List.of() : List.copyOf(citations);
            return this;
        }

        public Builder usage(RagUsage usage) {
            this.usage = usage == null ? RagUsage.empty() : usage;
            return this;
        }

        public Builder attributes(Map<String, Object> attributes) {
            this.attributes = attributes == null ? new HashMap<>() : new HashMap<>(attributes);
            return this;
        }

        public Builder attribute(String key, Object value) {
            this.attributes.put(key, value);
            return this;
        }

        public RagResponse build() {
            return new RagResponse(answer, citations, usage, attributes);
        }
    }
}
