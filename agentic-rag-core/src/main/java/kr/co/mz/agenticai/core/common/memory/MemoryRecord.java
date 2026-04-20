package kr.co.mz.agenticai.core.common.memory;

import java.time.Instant;
import java.util.Objects;

/**
 * Single entry in a conversation history. {@code role} captures the author
 * of the message; {@code content} is the raw text; {@code timestamp} is
 * assigned by the caller (use {@code Instant.now()} if the source does not
 * provide one).
 */
public record MemoryRecord(Role role, String content, Instant timestamp) {

    public MemoryRecord {
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(content, "content");
        Objects.requireNonNull(timestamp, "timestamp");
    }

    public enum Role { USER, ASSISTANT, SYSTEM, TOOL }

    public static MemoryRecord user(String content) {
        return new MemoryRecord(Role.USER, content, Instant.now());
    }

    public static MemoryRecord assistant(String content) {
        return new MemoryRecord(Role.ASSISTANT, content, Instant.now());
    }
}
