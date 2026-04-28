package kr.co.mz.agenticai.core.autoconfigure.memory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import kr.co.mz.agenticai.core.common.memory.MemoryRecord;
import kr.co.mz.agenticai.core.common.spi.MemoryStore;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * {@link MemoryStore} backed by a Redis LIST per conversation. Each entry is
 * a JSON-serialized {@link MemoryRecord}; appends RPUSH to the tail, history
 * reads from the tail with LRANGE.
 *
 * <p>The per-conversation key is refreshed with {@code EXPIRE} on every
 * append so idle conversations naturally fall off; pass a non-positive
 * {@code ttl} to keep entries indefinitely.
 */
public final class RedisMemoryStore implements MemoryStore {

    private final StringRedisTemplate redis;
    private final String keyPrefix;
    private final Duration ttl;
    private final ObjectMapper mapper;

    public RedisMemoryStore(
            StringRedisTemplate redis, String keyPrefix, Duration ttl, ObjectMapper mapper) {
        this.redis = Objects.requireNonNull(redis, "redis");
        this.keyPrefix = Objects.requireNonNull(keyPrefix, "keyPrefix");
        this.ttl = ttl;
        this.mapper = Objects.requireNonNull(mapper, "mapper");
    }

    @Override
    public void append(String conversationId, MemoryRecord entry) {
        Objects.requireNonNull(conversationId, "conversationId");
        Objects.requireNonNull(entry, "entry");
        String key = keyPrefix + conversationId;
        String payload;
        try {
            payload = mapper.writeValueAsString(entry);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("MemoryRecord serialization failed", e);
        }
        redis.opsForList().rightPush(key, payload);
        if (ttl != null && !ttl.isZero() && !ttl.isNegative()) {
            redis.expire(key, ttl);
        }
    }

    @Override
    public List<MemoryRecord> history(String conversationId, int maxMessages) {
        if (maxMessages <= 0) {
            return List.of();
        }
        List<String> raw = redis.opsForList().range(
                keyPrefix + conversationId, -maxMessages, -1);
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        List<MemoryRecord> out = new ArrayList<>(raw.size());
        for (String s : raw) {
            try {
                out.add(mapper.readValue(s, MemoryRecord.class));
            } catch (IOException e) {
                throw new IllegalStateException("MemoryRecord deserialization failed", e);
            }
        }
        return out;
    }

    @Override
    public void clear(String conversationId) {
        redis.delete(keyPrefix + conversationId);
    }
}
