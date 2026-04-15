package kr.co.mz.agenticai.core.autoconfigure;

import io.micrometer.core.instrument.MeterRegistry;
import kr.co.mz.agenticai.core.autoconfigure.observability.MetricsRagEventListener;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

@AutoConfiguration(after = AgenticRagCoreAutoConfiguration.class)
@ConditionalOnClass(MeterRegistry.class)
@ConditionalOnBean(MeterRegistry.class)
@ConditionalOnProperty(prefix = "agentic-rag.observability", name = "enabled", matchIfMissing = true)
public class AgenticRagObservabilityAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public MetricsRagEventListener metricsRagEventListener(MeterRegistry registry) {
        return new MetricsRagEventListener(registry);
    }
}
