package kr.co.mz.agenticai.core.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import kr.co.mz.agenticai.core.autoconfigure.tools.fs.FileSystemToolCallbackProvider;
import kr.co.mz.agenticai.core.common.spi.WorkspaceSandbox;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class AgenticRagFsToolsAutoConfigurationTest {

    @TempDir
    Path tmpDir;

    private ApplicationContextRunner runner() {
        return new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        AgenticRagFsToolsAutoConfiguration.class,
                        AgenticRagCoreAutoConfiguration.class,
                        AgenticRagToolsAutoConfiguration.class));
    }

    /**
     * Case 1: enabled=true + root set → WorkspaceSandbox + FileSystemToolCallbackProvider beans registered;
     * CatalogToolProvider exposes fs_glob, fs_listDir, fs_readFile.
     */
    @Test
    void registersBothBeansAndExposesToolsWhenEnabledWithRoot() throws IOException {
        Files.createDirectories(tmpDir);
        String root = tmpDir.toAbsolutePath().toString();

        runner()
                .withPropertyValues(
                        "agentic-rag.tools.fs.enabled=true",
                        "agentic-rag.tools.fs.root=" + root)
                .run(ctx -> {
                    assertThat(ctx).hasSingleBean(WorkspaceSandbox.class);
                    assertThat(ctx).hasSingleBean(FileSystemToolCallbackProvider.class);

                    kr.co.mz.agenticai.core.common.spi.ToolProvider toolProvider =
                            ctx.getBean(kr.co.mz.agenticai.core.common.spi.ToolProvider.class);
                    assertThat(toolProvider.tools())
                            .extracting(cb -> cb.getToolDefinition().name())
                            .containsExactlyInAnyOrder("fs_glob", "fs_listDir", "fs_readFile");
                });
    }

    /**
     * Case 2: enabled=false (or not set) → neither bean is registered.
     */
    @Test
    void doesNotRegisterBeansWhenDisabled() {
        runner()
                .withPropertyValues("agentic-rag.tools.fs.enabled=false")
                .run(ctx -> {
                    assertThat(ctx).doesNotHaveBean(WorkspaceSandbox.class);
                    assertThat(ctx).doesNotHaveBean(FileSystemToolCallbackProvider.class);
                });
    }

    /**
     * Case 2b: property absent → beans not registered (matchIfMissing=false).
     */
    @Test
    void doesNotRegisterBeansWhenPropertyAbsent() {
        runner()
                .run(ctx -> {
                    assertThat(ctx).doesNotHaveBean(WorkspaceSandbox.class);
                    assertThat(ctx).doesNotHaveBean(FileSystemToolCallbackProvider.class);
                });
    }

    /**
     * Case 3: enabled=true + root not set → context startup fails with IllegalStateException.
     */
    @Test
    void failsStartupWhenEnabledButRootNotSet() {
        runner()
                .withPropertyValues("agentic-rag.tools.fs.enabled=true")
                .run(ctx -> assertThat(ctx).hasFailed()
                        .getFailure()
                        .rootCause()
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining("agentic-rag.tools.fs.root"));
    }
}
