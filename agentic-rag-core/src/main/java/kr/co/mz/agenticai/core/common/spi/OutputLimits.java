package kr.co.mz.agenticai.core.common.spi;

/**
 * Caps returned by file-system tools to prevent context-window flooding.
 *
 * @param maxBytes    maximum bytes returned per read operation
 * @param maxLines    maximum lines returned per read operation
 * @param maxListEntries maximum directory entries returned per list operation
 */
public record OutputLimits(int maxBytes, int maxLines, int maxListEntries) {

    /** Sensible defaults: 32 KB, 500 lines, 200 entries. */
    public static final OutputLimits DEFAULT = new OutputLimits(32 * 1024, 500, 200);

    public OutputLimits {
        if (maxBytes <= 0) throw new IllegalArgumentException("maxBytes must be > 0");
        if (maxLines <= 0) throw new IllegalArgumentException("maxLines must be > 0");
        if (maxListEntries <= 0) throw new IllegalArgumentException("maxListEntries must be > 0");
    }
}
