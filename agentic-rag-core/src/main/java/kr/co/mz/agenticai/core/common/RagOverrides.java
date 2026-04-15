package kr.co.mz.agenticai.core.common;

import java.util.Map;

/**
 * Per-request overrides for model / strategy selection.
 *
 * <p>Any field set to {@code null} means "fall back to auto-configured default".
 * Additional ad-hoc overrides can be supplied via {@link #attributes()}.
 */
public record RagOverrides(
        String llmProvider,
        String llmModel,
        String embeddingProvider,
        String embeddingModel,
        String chunkingStrategy,
        Integer topK,
        Double temperature,
        Map<String, Object> attributes) {

    private static final RagOverrides EMPTY =
            new RagOverrides(null, null, null, null, null, null, null, Map.of());

    public RagOverrides {
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }

    public static RagOverrides empty() {
        return EMPTY;
    }
}
