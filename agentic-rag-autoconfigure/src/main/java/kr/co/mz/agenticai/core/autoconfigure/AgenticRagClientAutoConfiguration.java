package kr.co.mz.agenticai.core.autoconfigure;

import java.util.List;
import kr.co.mz.agenticai.core.agent.client.DefaultAgenticRagClient;
import kr.co.mz.agenticai.core.agent.client.KoreanRagPrompts;
import kr.co.mz.agenticai.core.common.AgenticRagClient;
import kr.co.mz.agenticai.core.common.spi.FactChecker;
import kr.co.mz.agenticai.core.common.spi.Guardrail;
import kr.co.mz.agenticai.core.common.spi.RagEventPublisher;
import kr.co.mz.agenticai.core.common.spi.RetrieverRouter;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

@AutoConfiguration(after = {
        AgenticRagCoreAutoConfiguration.class,
        AgenticRagRetrievalAutoConfiguration.class,
        AgenticRagFactCheckAutoConfiguration.class,
        AgenticRagAgentsAutoConfiguration.class
})
@ConditionalOnClass(ChatModel.class)
@ConditionalOnProperty(name = "agentic-rag.enabled", matchIfMissing = true)
public class AgenticRagClientAutoConfiguration {

    // NOTE: no @ConditionalOnBean(ChatModel.class) — Spring AI model
    // auto-configs may register their ChatModel AFTER our condition is
    // evaluated, causing false negatives. We rely on constructor injection
    // to surface a clear error if no ChatModel bean exists.
    @Bean
    @ConditionalOnMissingBean(AgenticRagClient.class)
    public AgenticRagClient agenticRagClient(
            RetrieverRouter router,
            ChatModel chatModel,
            ObjectProvider<FactChecker> factChecker,
            RagEventPublisher events,
            List<Guardrail> guardrails,
            AgenticRagProperties props) {
        return new DefaultAgenticRagClient(
                router, chatModel,
                factChecker.getIfAvailable(),
                events,
                guardrails,
                new DefaultAgenticRagClient.PromptConfig(
                        KoreanRagPrompts.SYSTEM,
                        KoreanRagPrompts.USER,
                        props.getClient().getDefaultTopK()));
    }
}
