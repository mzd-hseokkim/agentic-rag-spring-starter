package kr.co.mz.agenticai.core.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class AgenticRagPropertiesFsBindingTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(AgenticRagCoreAutoConfiguration.class));

    @Test
    void defaultFsPropertiesAreBound() {
        runner.run(ctx -> {
            AgenticRagProperties props = ctx.getBean(AgenticRagProperties.class);
            AgenticRagProperties.Fs fs = props.getTools().getFs();
            assertThat(fs.getRoot()).isEmpty();
            assertThat(fs.isRespectGitignore()).isTrue();
            assertThat(fs.getMaxReadBytes()).isGreaterThan(0);
            assertThat(fs.getMaxReadLines()).isGreaterThan(0);
            assertThat(fs.getMaxListEntries()).isGreaterThan(0);
        });
    }

    @Test
    void customFsRootIsBound() {
        runner.withPropertyValues("agentic-rag.tools.fs.root=/tmp/workspace")
                .run(ctx -> {
                    AgenticRagProperties.Fs fs =
                            ctx.getBean(AgenticRagProperties.class).getTools().getFs();
                    assertThat(fs.getRoot()).isEqualTo("/tmp/workspace");
                });
    }

    @Test
    void respectGitignoreCanBeDisabled() {
        runner.withPropertyValues("agentic-rag.tools.fs.respect-gitignore=false")
                .run(ctx -> {
                    AgenticRagProperties.Fs fs =
                            ctx.getBean(AgenticRagProperties.class).getTools().getFs();
                    assertThat(fs.isRespectGitignore()).isFalse();
                });
    }
}
