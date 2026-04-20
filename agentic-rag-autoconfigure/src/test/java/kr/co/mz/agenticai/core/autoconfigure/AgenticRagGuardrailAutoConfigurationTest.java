package kr.co.mz.agenticai.core.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import kr.co.mz.agenticai.core.common.spi.Guardrail;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

class AgenticRagGuardrailAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    AgenticRagCoreAutoConfiguration.class,
                    AgenticRagGuardrailAutoConfiguration.class));

    @Test
    void noGuardrailBeansRegisteredByDefault() {
        runner.run(ctx -> assertThat(ctx.getBeansOfType(Guardrail.class)).isEmpty());
    }

    @Test
    void individualGuardrailRegisteredWhenOptedIn() {
        runner.withPropertyValues("agentic-rag.guardrails.logging.enabled=true")
                .run(ctx -> {
                    assertThat(ctx).hasBean("loggingInputGuardrail");
                    assertThat(ctx).hasBean("loggingOutputGuardrail");
                });
    }

    @Test
    void killSwitchBlocksAllGuardrailsEvenWhenIndividuallyEnabled() {
        runner.withPropertyValues(
                        "agentic-rag.guardrails.enabled=false",
                        "agentic-rag.guardrails.logging.enabled=true",
                        "agentic-rag.guardrails.pii-mask.enabled=true",
                        "agentic-rag.guardrails.prompt-injection.enabled=true")
                .run(ctx -> assertThat(ctx.getBeansOfType(Guardrail.class)).isEmpty());
    }

    @Test
    void userBeanOverridesDefaultGuardrail() {
        runner.withPropertyValues("agentic-rag.guardrails.prompt-injection.enabled=true")
                .withUserConfiguration(CustomPromptInjectionConfig.class)
                .run(ctx -> {
                    Guardrail g = (Guardrail) ctx.getBean("promptInjectionGuardrail");
                    assertThat(g).isInstanceOf(NoopGuardrail.class);
                });
    }

    @Configuration
    static class CustomPromptInjectionConfig {
        @Bean(name = "promptInjectionGuardrail")
        Guardrail promptInjectionGuardrail() {
            return new NoopGuardrail();
        }
    }

    static class NoopGuardrail implements Guardrail {
        @Override public Stage stage() { return Stage.INPUT; }
        @Override public Result apply(String text) { return Result.pass(text); }
    }
}
