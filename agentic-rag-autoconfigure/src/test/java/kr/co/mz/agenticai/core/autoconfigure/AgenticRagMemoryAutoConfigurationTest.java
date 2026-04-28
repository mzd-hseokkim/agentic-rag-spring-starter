package kr.co.mz.agenticai.core.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import kr.co.mz.agenticai.core.autoconfigure.memory.RedisMemoryStore;
import kr.co.mz.agenticai.core.common.memory.InMemoryMemoryStore;
import kr.co.mz.agenticai.core.common.spi.MemoryStore;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

class AgenticRagMemoryAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    JacksonAutoConfiguration.class,
                    AgenticRagMemoryAutoConfiguration.class,
                    AgenticRagCoreAutoConfiguration.class));

    @Test
    void registersRedisMemoryStoreWhenStringRedisTemplatePresent() {
        runner.withUserConfiguration(StubRedisConfig.class)
                .run(ctx -> assertThat(ctx).getBean(MemoryStore.class)
                        .isInstanceOf(RedisMemoryStore.class));
    }

    @Test
    void fallsBackToInMemoryWhenRedisClasspathMissing() {
        runner.withClassLoader(new FilteredClassLoader(StringRedisTemplate.class))
                .run(ctx -> assertThat(ctx).getBean(MemoryStore.class)
                        .isInstanceOf(InMemoryMemoryStore.class));
    }

    @Test
    void fallsBackToInMemoryWhenRedisDisabledByProperty() {
        runner.withUserConfiguration(StubRedisConfig.class)
                .withPropertyValues("agentic-rag.memory.redis.enabled=false")
                .run(ctx -> assertThat(ctx).getBean(MemoryStore.class)
                        .isInstanceOf(InMemoryMemoryStore.class));
    }

    @Test
    void userSuppliedMemoryStoreOverridesAutoConfig() {
        InMemoryMemoryStore custom = new InMemoryMemoryStore();
        runner.withUserConfiguration(StubRedisConfig.class)
                .withBean("memoryStore", MemoryStore.class, () -> custom)
                .run(ctx -> assertThat(ctx.getBean(MemoryStore.class)).isSameAs(custom));
    }

    @Test
    void appliesPropertiesToRedisStore() {
        runner.withUserConfiguration(StubRedisConfig.class)
                .withPropertyValues(
                        "agentic-rag.memory.redis.key-prefix=custom:",
                        "agentic-rag.memory.redis.ttl=1h")
                .run(ctx -> {
                    AgenticRagProperties props = ctx.getBean(AgenticRagProperties.class);
                    assertThat(props.getMemory().getRedis().getKeyPrefix()).isEqualTo("custom:");
                    assertThat(props.getMemory().getRedis().getTtl().toHours()).isEqualTo(1);
                    assertThat(ctx.getBean(MemoryStore.class)).isInstanceOf(RedisMemoryStore.class);
                });
    }

    @Configuration
    static class StubRedisConfig {
        @Bean
        StringRedisTemplate stringRedisTemplate(ObjectMapper mapper) {
            // No real connection — the auto-config only needs the bean type to satisfy
            // ConditionalOnBean(StringRedisTemplate.class). RedisMemoryStore is constructed
            // but never invoked in these tests.
            RedisConnectionFactory factory = new RedisConnectionFactory() {
                @Override public RedisConnection getConnection() { throw new UnsupportedOperationException(); }
                @Override public org.springframework.data.redis.connection.RedisClusterConnection getClusterConnection() { throw new UnsupportedOperationException(); }
                @Override public boolean getConvertPipelineAndTxResults() { return false; }
                @Override public org.springframework.data.redis.connection.RedisSentinelConnection getSentinelConnection() { throw new UnsupportedOperationException(); }
                @Override public org.springframework.dao.DataAccessException translateExceptionIfPossible(RuntimeException ex) { return null; }
            };
            return new StringRedisTemplate(factory);
        }
    }
}
