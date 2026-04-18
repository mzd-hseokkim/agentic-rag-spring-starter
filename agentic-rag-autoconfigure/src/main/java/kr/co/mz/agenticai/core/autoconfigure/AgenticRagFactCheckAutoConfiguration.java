package kr.co.mz.agenticai.core.autoconfigure;

import kr.co.mz.agenticai.core.common.spi.FactChecker;
import kr.co.mz.agenticai.core.factcheck.KoreanFactCheckPrompts;
import kr.co.mz.agenticai.core.factcheck.LlmFactChecker;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

@AutoConfiguration(after = AgenticRagCoreAutoConfiguration.class)
@ConditionalOnProperty(prefix = "agentic-rag.factcheck", name = "enabled", havingValue = "true")
public class AgenticRagFactCheckAutoConfiguration {

    // NOTE: no @ConditionalOnBean(ChatModel.class) — Spring AI model
    // auto-configs may register their ChatModel AFTER this condition is
    // evaluated. We rely on constructor injection instead.
    @Bean
    @ConditionalOnMissingBean(FactChecker.class)
    public FactChecker factChecker(ChatModel chatModel, AgenticRagProperties props) {
        return new LlmFactChecker(
                chatModel, KoreanFactCheckPrompts.VERIFY,
                props.getFactcheck().getMinConfidence());
    }
}
