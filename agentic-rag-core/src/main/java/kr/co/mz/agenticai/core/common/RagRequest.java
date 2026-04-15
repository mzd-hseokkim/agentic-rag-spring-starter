package kr.co.mz.agenticai.core.common;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * A single Agentic RAG query invocation.
 *
 * <p>Unknown or future-looking parameters can be carried through
 * {@link #attributes()} without breaking the public API.
 */
public record RagRequest(
        String query,
        String sessionId,
        String userId,
        Map<String, Object> metadataFilters,
        RagOverrides overrides,
        boolean streaming,
        Map<String, Object> attributes) {

    public RagRequest {
        Objects.requireNonNull(query, "query must not be null");
        metadataFilters = metadataFilters == null ? Map.of() : Map.copyOf(metadataFilters);
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
        if (overrides == null) {
            overrides = RagOverrides.empty();
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static RagRequest of(String query) {
        return builder().query(query).build();
    }

    public static final class Builder {
        private String query;
        private String sessionId;
        private String userId;
        private Map<String, Object> metadataFilters = new HashMap<>();
        private RagOverrides overrides = RagOverrides.empty();
        private boolean streaming;
        private Map<String, Object> attributes = new HashMap<>();

        public Builder query(String query) {
            this.query = query;
            return this;
        }

        public Builder sessionId(String sessionId) {
            this.sessionId = sessionId;
            return this;
        }

        public Builder userId(String userId) {
            this.userId = userId;
            return this;
        }

        public Builder metadataFilters(Map<String, Object> metadataFilters) {
            this.metadataFilters = metadataFilters == null
                    ? new HashMap<>()
                    : new HashMap<>(metadataFilters);
            return this;
        }

        public Builder metadataFilter(String key, Object value) {
            this.metadataFilters.put(key, value);
            return this;
        }

        public Builder overrides(RagOverrides overrides) {
            this.overrides = overrides == null ? RagOverrides.empty() : overrides;
            return this;
        }

        public Builder streaming(boolean streaming) {
            this.streaming = streaming;
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

        public RagRequest build() {
            return new RagRequest(
                    query, sessionId, userId, metadataFilters, overrides, streaming, attributes);
        }
    }
}
