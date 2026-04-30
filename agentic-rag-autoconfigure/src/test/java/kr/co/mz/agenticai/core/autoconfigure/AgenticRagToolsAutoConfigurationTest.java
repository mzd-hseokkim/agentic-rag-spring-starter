package kr.co.mz.agenticai.core.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import kr.co.mz.agenticai.core.autoconfigure.tools.CatalogToolProvider;
import kr.co.mz.agenticai.core.common.spi.ToolProvider;
import kr.co.mz.agenticai.core.common.tool.EmptyToolProvider;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

class AgenticRagToolsAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    AgenticRagToolsAutoConfiguration.class,
                    AgenticRagCoreAutoConfiguration.class));

    @Test
    void registersCatalogProviderWhenToolCallbackProviderBeanPresent() {
        runner.withUserConfiguration(StubToolsConfig.class)
                .run(ctx -> {
                    assertThat(ctx).getBean(ToolProvider.class)
                            .isInstanceOf(CatalogToolProvider.class);
                    assertThat(ctx.getBean(ToolProvider.class).tools())
                            .extracting(cb -> cb.getToolDefinition().name())
                            .containsExactly("greet");
                });
    }

    @Test
    void fallsBackToEmptyWhenNoToolCallbackProvider() {
        runner.run(ctx -> assertThat(ctx).getBean(ToolProvider.class)
                .isInstanceOf(EmptyToolProvider.class));
    }

    @Test
    void fallsBackToEmptyWhenToolsDisabled() {
        runner.withUserConfiguration(StubToolsConfig.class)
                .withPropertyValues("agentic-rag.tools.enabled=false")
                .run(ctx -> assertThat(ctx).getBean(ToolProvider.class)
                        .isInstanceOf(EmptyToolProvider.class));
    }

    @Test
    void userSuppliedToolProviderOverridesAutoConfig() {
        EmptyToolProvider custom = new EmptyToolProvider();
        runner.withUserConfiguration(StubToolsConfig.class)
                .withBean("toolProvider", ToolProvider.class, () -> custom)
                .run(ctx -> assertThat(ctx.getBean(ToolProvider.class)).isSameAs(custom));
    }

    @Test
    void appliesDenyListFromProperties() {
        runner.withUserConfiguration(MultiToolsConfig.class)
                .withPropertyValues("agentic-rag.tools.denied-names=danger")
                .run(ctx -> assertThat(ctx.getBean(ToolProvider.class).tools())
                        .extracting(cb -> cb.getToolDefinition().name())
                        .containsExactly("safe"));
    }

    private static ToolCallback namedCallback(String name) {
        ToolCallback cb = mock(ToolCallback.class);
        ToolDefinition def = mock(ToolDefinition.class);
        when(def.name()).thenReturn(name);
        when(cb.getToolDefinition()).thenReturn(def);
        return cb;
    }

    @Configuration
    static class StubToolsConfig {
        @Bean
        ToolCallbackProvider greetingTools() {
            return () -> new ToolCallback[]{namedCallback("greet")};
        }
    }

    @Configuration
    static class MultiToolsConfig {
        @Bean
        ToolCallbackProvider mixedTools() {
            return () -> new ToolCallback[]{namedCallback("safe"), namedCallback("danger")};
        }
    }
}
