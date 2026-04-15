package kr.co.mz.agenticai.core.common;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.springframework.core.io.Resource;

/** Input to {@link IngestionPipeline#ingest(IngestionRequest)}. */
public record IngestionRequest(
        Resource resource,
        String strategyName,
        Map<String, Object> metadataOverrides,
        Map<String, Object> attributes) {

    public IngestionRequest {
        Objects.requireNonNull(resource, "resource must not be null");
        metadataOverrides = metadataOverrides == null ? Map.of() : Map.copyOf(metadataOverrides);
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }

    public static IngestionRequest of(Resource resource) {
        return new IngestionRequest(resource, null, Map.of(), Map.of());
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private Resource resource;
        private String strategyName;
        private Map<String, Object> metadataOverrides = new HashMap<>();
        private Map<String, Object> attributes = new HashMap<>();

        public Builder resource(Resource resource) {
            this.resource = resource;
            return this;
        }

        public Builder strategyName(String strategyName) {
            this.strategyName = strategyName;
            return this;
        }

        public Builder metadataOverride(String key, Object value) {
            this.metadataOverrides.put(key, value);
            return this;
        }

        public Builder attribute(String key, Object value) {
            this.attributes.put(key, value);
            return this;
        }

        public IngestionRequest build() {
            return new IngestionRequest(resource, strategyName, metadataOverrides, attributes);
        }
    }
}
