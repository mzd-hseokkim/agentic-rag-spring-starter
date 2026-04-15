package kr.co.mz.agenticai.core.autoconfigure;

import kr.co.mz.agenticai.core.common.guardrail.LoggingGuardrail;
import kr.co.mz.agenticai.core.common.guardrail.PiiMaskingGuardrail;
import kr.co.mz.agenticai.core.common.guardrail.PromptInjectionGuardrail;
import kr.co.mz.agenticai.core.common.spi.Guardrail;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

@AutoConfiguration(after = AgenticRagCoreAutoConfiguration.class)
public class AgenticRagGuardrailAutoConfiguration {

    @Bean(name = "promptInjectionGuardrail")
    @ConditionalOnMissingBean(name = "promptInjectionGuardrail")
    @ConditionalOnProperty(prefix = "agentic-rag.guardrails.prompt-injection", name = "enabled",
            havingValue = "true")
    public Guardrail promptInjectionGuardrail() {
        return new PromptInjectionGuardrail();
    }

    @Bean(name = "piiMaskingInputGuardrail")
    @ConditionalOnMissingBean(name = "piiMaskingInputGuardrail")
    @ConditionalOnProperty(prefix = "agentic-rag.guardrails.pii-mask", name = "enabled",
            havingValue = "true")
    public Guardrail piiMaskingInputGuardrail() {
        return new PiiMaskingGuardrail(Guardrail.Stage.INPUT);
    }

    @Bean(name = "piiMaskingOutputGuardrail")
    @ConditionalOnMissingBean(name = "piiMaskingOutputGuardrail")
    @ConditionalOnProperty(prefix = "agentic-rag.guardrails.pii-mask", name = "enabled",
            havingValue = "true")
    public Guardrail piiMaskingOutputGuardrail() {
        return new PiiMaskingGuardrail(Guardrail.Stage.OUTPUT);
    }

    @Bean(name = "loggingInputGuardrail")
    @ConditionalOnMissingBean(name = "loggingInputGuardrail")
    @ConditionalOnProperty(prefix = "agentic-rag.guardrails.logging", name = "enabled",
            havingValue = "true")
    public Guardrail loggingInputGuardrail() {
        return new LoggingGuardrail(Guardrail.Stage.INPUT);
    }

    @Bean(name = "loggingOutputGuardrail")
    @ConditionalOnMissingBean(name = "loggingOutputGuardrail")
    @ConditionalOnProperty(prefix = "agentic-rag.guardrails.logging", name = "enabled",
            havingValue = "true")
    public Guardrail loggingOutputGuardrail() {
        return new LoggingGuardrail(Guardrail.Stage.OUTPUT);
    }
}
