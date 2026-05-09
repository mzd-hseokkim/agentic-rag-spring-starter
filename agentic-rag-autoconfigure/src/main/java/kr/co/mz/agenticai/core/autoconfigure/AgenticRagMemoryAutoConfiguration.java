package kr.co.mz.agenticai.core.autoconfigure;

import com.fasterxml.jackson.databind.ObjectMapper;
import kr.co.mz.agenticai.core.autoconfigure.memory.RedisMemoryStore;
import kr.co.mz.agenticai.core.common.memory.policy.HeuristicTokenCounter;
import kr.co.mz.agenticai.core.common.memory.policy.RecentMessagesPolicy;
import kr.co.mz.agenticai.core.common.memory.policy.RollingSummaryPolicy;
import kr.co.mz.agenticai.core.common.memory.policy.SpringAiChatMemoryAdapter;
import kr.co.mz.agenticai.core.common.memory.policy.TokenWindowPolicy;
import kr.co.mz.agenticai.core.common.spi.MemoryPolicy;
import kr.co.mz.agenticai.core.common.spi.MemoryStore;
import kr.co.mz.agenticai.core.common.spi.TokenCounter;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.ObjectProvider;
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
 * Registers memory infrastructure beans: Redis-backed {@link MemoryStore} when
 * available, {@link TokenCounter}, {@link MemoryPolicy}, and an optional
 * {@link ChatMemory} adapter for Spring AI advisor interop.
 *
 * <p>Runs before {@link AgenticRagCoreAutoConfiguration} so its MemoryStore
 * bean wins the {@code @ConditionalOnMissingBean(MemoryStore.class)} check
 * there, leaving the in-memory default as a fallback.
 */
@AutoConfiguration(after = RedisAutoConfiguration.class, before = AgenticRagCoreAutoConfiguration.class)
@EnableConfigurationProperties(AgenticRagProperties.class)
public class AgenticRagMemoryAutoConfiguration {

    @Bean
    @ConditionalOnClass(StringRedisTemplate.class)
    @ConditionalOnBean(StringRedisTemplate.class)
    @ConditionalOnMissingBean(MemoryStore.class)
    @ConditionalOnProperty(prefix = "agentic-rag.memory.redis", name = "enabled",
            havingValue = "true", matchIfMissing = true)
    public MemoryStore redisMemoryStore(
            StringRedisTemplate redis, ObjectMapper mapper, AgenticRagProperties props) {
        AgenticRagProperties.Redis cfg = props.getMemory().getRedis();
        return new RedisMemoryStore(redis, cfg.getKeyPrefix(), cfg.getTtl(), mapper);
    }

    @Bean
    @ConditionalOnMissingBean(TokenCounter.class)
    public TokenCounter heuristicTokenCounter() {
        return new HeuristicTokenCounter();
    }

    @Bean
    @ConditionalOnMissingBean(MemoryPolicy.class)
    public MemoryPolicy memoryPolicy(
            AgenticRagProperties props,
            TokenCounter tokenCounter,
            ObjectProvider<ChatModel> chatModel) {
        AgenticRagProperties.Policy cfg = props.getMemory().getPolicy();
        return switch (cfg.getType()) {
            case TOKEN_WINDOW -> new TokenWindowPolicy(cfg.getTokenBudget(), tokenCounter);
            case ROLLING_SUMMARY -> {
                ChatModel model = chatModel.getIfAvailable();
                if (model == null) {
                    throw new IllegalStateException(
                            "agentic-rag.memory.policy.type=ROLLING_SUMMARY requires a ChatModel bean");
                }
                yield new RollingSummaryPolicy(model, tokenCounter,
                        cfg.getTokenBudget(), cfg.getSummarizeFraction(), cfg.getRecentTurns());
            }
            default -> new RecentMessagesPolicy(cfg.getWindowSize());
        };
    }

    @Bean
    @ConditionalOnMissingBean(ChatMemory.class)
    @ConditionalOnBean(MemoryStore.class)
    public ChatMemory springAiChatMemoryAdapter(MemoryStore store) {
        return new SpringAiChatMemoryAdapter(store);
    }
}
