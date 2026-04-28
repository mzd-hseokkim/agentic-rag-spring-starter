package kr.co.mz.agenticai.core.autoconfigure.memory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Duration;
import kr.co.mz.agenticai.core.common.memory.MemoryRecord;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@EnabledIf("dockerAvailable")
class RedisMemoryStoreIT {

    @SuppressWarnings("unused") // referenced by @EnabledIf
    static boolean dockerAvailable() {
        try {
            return org.testcontainers.DockerClientFactory.instance().isDockerAvailable();
        } catch (Throwable t) {
            return false;
        }
    }

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(
            DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

    private static LettuceConnectionFactory factory;
    private StringRedisTemplate template;
    private RedisMemoryStore store;

    @BeforeAll
    static void startConnection() {
        factory = new LettuceConnectionFactory(new RedisStandaloneConfiguration(
                REDIS.getHost(), REDIS.getMappedPort(6379)));
        factory.afterPropertiesSet();
    }

    @AfterAll
    static void stopConnection() {
        if (factory != null) {
            factory.destroy();
        }
    }

    @BeforeEach
    void setUp() {
        template = new StringRedisTemplate(factory);
        template.execute((org.springframework.data.redis.connection.RedisConnection c) -> {
            c.serverCommands().flushAll();
            return null;
        });
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        store = new RedisMemoryStore(template, "test:", Duration.ofMinutes(5), mapper);
    }

    @Test
    void appendAndHistoryRoundTrip() {
        store.append("c1", MemoryRecord.user("Q1"));
        store.append("c1", MemoryRecord.assistant("A1"));
        store.append("c1", MemoryRecord.user("Q2"));

        assertThat(store.history("c1", 10))
                .extracting(MemoryRecord::role, MemoryRecord::content)
                .containsExactly(
                        org.assertj.core.api.Assertions.tuple(MemoryRecord.Role.USER, "Q1"),
                        org.assertj.core.api.Assertions.tuple(MemoryRecord.Role.ASSISTANT, "A1"),
                        org.assertj.core.api.Assertions.tuple(MemoryRecord.Role.USER, "Q2"));
    }

    @Test
    void historyReturnsMostRecentEntriesWhenCapped() {
        for (int i = 0; i < 5; i++) {
            store.append("c1", MemoryRecord.user("m" + i));
        }

        assertThat(store.history("c1", 2))
                .extracting(MemoryRecord::content)
                .containsExactly("m3", "m4");
    }

    @Test
    void historyForUnknownConversationIsEmpty() {
        assertThat(store.history("missing", 5)).isEmpty();
    }

    @Test
    void clearRemovesConversation() {
        store.append("c1", MemoryRecord.user("hi"));
        store.clear("c1");

        assertThat(store.history("c1", 10)).isEmpty();
    }

    @Test
    void ttlIsAppliedAndRefreshedOnAppend() {
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        var shortTtl = new RedisMemoryStore(template, "ttl:", Duration.ofSeconds(2), mapper);

        shortTtl.append("c1", MemoryRecord.user("hi"));
        Long initial = template.getExpire("ttl:c1");
        assertThat(initial).isPositive().isLessThanOrEqualTo(2);

        await().atMost(Duration.ofSeconds(5)).until(() -> shortTtl.history("c1", 10).isEmpty());
    }
}
