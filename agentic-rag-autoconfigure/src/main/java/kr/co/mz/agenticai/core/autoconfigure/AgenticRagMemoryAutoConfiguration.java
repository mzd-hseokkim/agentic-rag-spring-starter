package kr.co.mz.agenticai.core.autoconfigure;

import com.fasterxml.jackson.databind.ObjectMapper;
import kr.co.mz.agenticai.core.autoconfigure.memory.RedisMemoryStore;
import kr.co.mz.agenticai.core.common.spi.MemoryStore;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Registers a Redis-backed {@link MemoryStore} when Spring Data Redis is on
 * the classpath and a {@link StringRedisTemplate} bean is available. Runs
 * before {@link AgenticRagCoreAutoConfiguration} so its bean wins the
 * {@code @ConditionalOnMissingBean(MemoryStore.class)} check there, leaving
 * the in-memory default as a fallback.
 */
@AutoConfiguration(after = RedisAutoConfiguration.class, before = AgenticRagCoreAutoConfiguration.class)
@ConditionalOnClass(StringRedisTemplate.class)
@ConditionalOnProperty(prefix = "agentic-rag.memory.redis", name = "enabled",
        havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(AgenticRagProperties.class)
public class AgenticRagMemoryAutoConfiguration {

    @Bean
    @ConditionalOnBean(StringRedisTemplate.class)
    @ConditionalOnMissingBean(MemoryStore.class)
    public MemoryStore redisMemoryStore(
            StringRedisTemplate redis, ObjectMapper mapper, AgenticRagProperties props) {
        AgenticRagProperties.Redis cfg = props.getMemory().getRedis();
        return new RedisMemoryStore(redis, cfg.getKeyPrefix(), cfg.getTtl(), mapper);
    }
}
