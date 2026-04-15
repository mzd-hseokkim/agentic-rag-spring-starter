package kr.co.mz.agenticai.core.common;

/** Token accounting for a single RAG invocation. */
public record RagUsage(long promptTokens, long completionTokens, long totalTokens) {

    private static final RagUsage EMPTY = new RagUsage(0L, 0L, 0L);

    public static RagUsage empty() {
        return EMPTY;
    }

    public static RagUsage of(long promptTokens, long completionTokens) {
        return new RagUsage(promptTokens, completionTokens, promptTokens + completionTokens);
    }
}
