package kr.co.mz.agenticai.core.autoconfigure.tools.fs.dto;

/**
 * Single directory entry returned by {@code fs_listDir}.
 *
 * @param name     file or directory name (not the full path)
 * @param type     {@code FILE}, {@code DIR}, or {@code SYMLINK}
 * @param size     size in bytes ({@code -1} if unavailable)
 * @param mtimeMs  last-modified time in epoch milliseconds ({@code -1} if unavailable)
 */
public record DirEntry(String name, String type, long size, long mtimeMs) {
}
