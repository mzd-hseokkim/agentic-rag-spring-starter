package kr.co.mz.agenticai.core.common.spi;

import java.util.List;
import kr.co.mz.agenticai.core.common.memory.MemoryRecord;

/**
 * Conversation-scoped storage for chat history. Register a bean of this
 * type to back memory with Redis, JDBC, or any other store; the default
 * registered implementation is process-local and non-persistent.
 *
 * <p>Implementations must be safe for concurrent access across multiple
 * conversations. Single-conversation ordering is the implementation's
 * responsibility.
 */
public interface MemoryStore {

    void append(String conversationId, MemoryRecord entry);

    /**
     * Returns the most recent messages for the given conversation, ordered
     * oldest-to-newest. {@code maxMessages} &le; 0 returns an empty list.
     */
    List<MemoryRecord> history(String conversationId, int maxMessages);

    void clear(String conversationId);
}
