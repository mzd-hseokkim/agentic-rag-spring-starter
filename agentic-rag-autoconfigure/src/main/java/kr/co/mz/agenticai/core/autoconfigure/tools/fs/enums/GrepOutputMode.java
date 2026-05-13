package kr.co.mz.agenticai.core.autoconfigure.tools.fs.enums;

/**
 * Controls the shape of output returned by {@code fs_grep}.
 *
 * <ul>
 *   <li>{@link #FILES_WITH_MATCHES} — paths of files that contain at least one match</li>
 *   <li>{@link #CONTENT} — matching lines with optional surrounding context lines</li>
 *   <li>{@link #COUNT} — per-file match counts</li>
 * </ul>
 */
public enum GrepOutputMode {

    /** Return only the paths of files that contain at least one match. */
    FILES_WITH_MATCHES,

    /** Return matching lines, optionally decorated with BEFORE/AFTER context lines. */
    CONTENT,

    /** Return a map of file path → match count (zero-count files excluded). */
    COUNT
}
