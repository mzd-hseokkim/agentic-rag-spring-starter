package kr.co.mz.agenticai.core.autoconfigure;

import kr.co.mz.agenticai.core.common.event.ApplicationEventRagEventPublisher;
import kr.co.mz.agenticai.core.common.spi.RagEventPublisher;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnProperty(prefix = "agentic-rag", name = "enabled", matchIfMissing = true)
@EnableConfigurationProperties(AgenticRagProperties.class)
public class AgenticRagCoreAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public RagEventPublisher ragEventPublisher(ApplicationEventPublisher delegate) {
        return new ApplicationEventRagEventPublisher(delegate);
    }
}
