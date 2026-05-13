package kr.co.mz.agenticai.core.autoconfigure.tools.fs;

import java.util.List;

/**
 * Applies output size limits to tool results to prevent context overflow.
 *
 * <p>NOTE: MAE-377 will provide the canonical implementation. This placeholder class
 * lives in {@code tools.fs} until that API is stabilised.
 */
public final class OutputLimits {

    /** Default maximum number of list entries returned by any single tool call. */
    public static final int DEFAULT_MAX_RESULTS = 50;

    /** Default maximum number of lines returned by {@code fs_readFile}. */
    public static final int DEFAULT_MAX_LINES = 2000;

    private final int maxResults;
    private final int maxLines;

    public OutputLimits(int maxResults, int maxLines) {
        this.maxResults = maxResults;
        this.maxLines = maxLines;
    }

    public static OutputLimits defaults() {
        return new OutputLimits(DEFAULT_MAX_RESULTS, DEFAULT_MAX_LINES);
    }

    public int maxResults() {
        return maxResults;
    }

    public int maxLines() {
        return maxLines;
    }

    /**
     * Truncates {@code items} to {@link #maxResults} and appends a sentinel entry
     * when the list was actually cut.
     */
    public <T> List<T> apply(List<T> items, T truncationSentinel) {
        if (items.size() <= maxResults) {
            return items;
        }
        List<T> truncated = new java.util.ArrayList<>(items.subList(0, maxResults));
        truncated.add(truncationSentinel);
        return List.copyOf(truncated);
    }

    /**
     * Appends {@code (truncated, N more)} as the last line when the content exceeds
     * {@link #maxLines} lines.
     */
    public String applyString(String content, int totalLines) {
        String[] lines = content.split("\n", -1);
        if (lines.length <= maxLines) {
            return content;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < maxLines; i++) {
            sb.append(lines[i]).append('\n');
        }
        int remaining = totalLines - maxLines;
        sb.append(String.format("(truncated, %d more)", remaining));
        return sb.toString();
    }
}
