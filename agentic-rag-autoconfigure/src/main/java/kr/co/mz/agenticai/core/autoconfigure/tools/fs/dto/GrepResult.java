package kr.co.mz.agenticai.core.autoconfigure.tools.fs.dto;

import java.util.List;
import java.util.Map;

/**
 * Union result from {@code fs_grep}; exactly one of the three payload fields is non-null
 * depending on the requested {@code outputMode}.
 *
 * <ul>
 *   <li>{@link #files} — populated for {@code FILES_WITH_MATCHES}</li>
 *   <li>{@link #contentLines} — populated for {@code CONTENT}</li>
 *   <li>{@link #counts} — populated for {@code COUNT}</li>
 * </ul>
 *
 * @param files        matching file paths (FILES_WITH_MATCHES mode)
 * @param contentLines matching lines with optional context (CONTENT mode)
 * @param counts       per-file match counts (COUNT mode)
 * @param truncated    {@code true} when output was cut by the head-limit
 */
public record GrepResult(
        List<String> files,
        List<MatchLine> contentLines,
        Map<String, Long> counts,
        boolean truncated) {

    /** Constructs a FILES_WITH_MATCHES result. */
    public static GrepResult ofFiles(List<String> files, boolean truncated) {
        return new GrepResult(files, null, null, truncated);
    }

    /** Constructs a CONTENT result. */
    public static GrepResult ofContent(List<MatchLine> lines, boolean truncated) {
        return new GrepResult(null, lines, null, truncated);
    }

    /** Constructs a COUNT result. */
    public static GrepResult ofCounts(Map<String, Long> counts) {
        return new GrepResult(null, null, counts, false);
    }
}
