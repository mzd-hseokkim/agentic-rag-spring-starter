package kr.co.mz.agenticai.core.common.memory;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import kr.co.mz.agenticai.core.common.spi.MemoryStore;

/**
 * Process-local, non-persistent {@link MemoryStore}. History is kept in a
 * {@link ConcurrentHashMap} keyed by conversation id; each conversation's
 * list is guarded by synchronising on itself.
 *
 * <p>Intended for development, tests, and single-node deployments. Use a
 * persistent implementation (Redis/JDBC) in production.
 */
public final class InMemoryMemoryStore implements MemoryStore {

    private final ConcurrentMap<String, List<MemoryRecord>> conversations = new ConcurrentHashMap<>();

    @Override
    public void append(String conversationId, MemoryRecord record) {
        Objects.requireNonNull(conversationId, "conversationId");
        Objects.requireNonNull(record, "record");
        List<MemoryRecord> history = conversations.computeIfAbsent(
                conversationId, k -> new ArrayList<>());
        synchronized (history) {
            history.add(record);
        }
    }

    @Override
    public List<MemoryRecord> history(String conversationId, int maxMessages) {
        if (maxMessages <= 0) {
            return List.of();
        }
        List<MemoryRecord> history = conversations.get(conversationId);
        if (history == null) {
            return List.of();
        }
        synchronized (history) {
            int from = Math.max(0, history.size() - maxMessages);
            return List.copyOf(history.subList(from, history.size()));
        }
    }

    @Override
    public void clear(String conversationId) {
        conversations.remove(conversationId);
    }
}
