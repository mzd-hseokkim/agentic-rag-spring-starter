package kr.co.mz.agenticai.core.autoconfigure.tools.fs.dto;

/**
 * A single line from a grep result, with its source context.
 *
 * @param path       file path (relative to workspace root)
 * @param lineNumber 1-based line number within the file
 * @param line       raw text of the line
 * @param kind       {@code MATCH}, {@code BEFORE}, or {@code AFTER}
 */
public record MatchLine(String path, int lineNumber, String line, String kind) {
}
