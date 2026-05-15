package kr.co.mz.agenticai.core.autoconfigure;

import java.nio.file.Path;
import kr.co.mz.agenticai.core.autoconfigure.tools.fs.FileSystemToolCallbackProvider;
import kr.co.mz.agenticai.core.autoconfigure.tools.fs.FileSystemTools;
import kr.co.mz.agenticai.core.autoconfigure.tools.fs.GrepTool;
import kr.co.mz.agenticai.core.autoconfigure.tools.fs.OutputLimits;
import kr.co.mz.agenticai.core.common.spi.WorkspaceSandbox;
import kr.co.mz.agenticai.core.common.tool.DefaultWorkspaceSandbox;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Registers {@link WorkspaceSandbox} and {@link FileSystemToolCallbackProvider} beans
 * when {@code agentic-rag.tools.fs.enabled=true} is set.
 *
 * <p>Runs before {@link AgenticRagCoreAutoConfiguration} so that
 * {@code CatalogToolProvider} can aggregate the resulting
 * {@link ToolCallbackProvider} bean.
 */
@AutoConfiguration(before = AgenticRagCoreAutoConfiguration.class)
@ConditionalOnClass(ToolCallbackProvider.class)
@ConditionalOnProperty(prefix = "agentic-rag.tools.fs", name = "enabled",
        havingValue = "true")
@EnableConfigurationProperties(AgenticRagProperties.class)
public class AgenticRagFsToolsAutoConfiguration {

    /**
     * Default {@link WorkspaceSandbox} (core SPI) backed by
     * {@code agentic-rag.tools.fs.root}. Fails fast if root is not set.
     */
    @Bean
    @ConditionalOnMissingBean(WorkspaceSandbox.class)
    public WorkspaceSandbox workspaceSandbox(AgenticRagProperties props) {
        AgenticRagProperties.Fs fsCfg = props.getTools().getFs();
        if (fsCfg.getRoot().isBlank()) {
            throw new IllegalStateException(
                    "agentic-rag.tools.fs.root must be set when agentic-rag.tools.fs.enabled=true");
        }
        kr.co.mz.agenticai.core.common.spi.OutputLimits coreLimits =
                new kr.co.mz.agenticai.core.common.spi.OutputLimits(
                        fsCfg.getMaxReadBytes(),
                        fsCfg.getMaxReadLines(),
                        fsCfg.getMaxListEntries());
        return new DefaultWorkspaceSandbox(
                Path.of(fsCfg.getRoot()), coreLimits, fsCfg.isRespectGitignore());
    }

    /**
     * Exposes {@link FileSystemTools} as a {@link ToolCallbackProvider} so that
     * {@code CatalogToolProvider} aggregates it automatically.
     *
     * <p>{@link FileSystemTools} currently depends on the legacy
     * {@code tools.fs.WorkspaceSandbox} interface. The core SPI bean is adapted
     * via a lambda until {@link FileSystemTools} is migrated to the core SPI.
     */
    @Bean
    @ConditionalOnMissingBean(FileSystemToolCallbackProvider.class)
    public FileSystemToolCallbackProvider fileSystemToolCallbackProvider(
            WorkspaceSandbox sandbox, AgenticRagProperties props) {
        AgenticRagProperties.Fs fsCfg = props.getTools().getFs();
        OutputLimits limits = new OutputLimits(fsCfg.getMaxListEntries(), fsCfg.getMaxReadLines());
        kr.co.mz.agenticai.core.autoconfigure.tools.fs.WorkspaceSandbox legacySandbox =
                rawPath -> {
                    try {
                        return sandbox.resolve(rawPath);
                    } catch (java.io.IOException e) {
                        throw new java.io.UncheckedIOException(e);
                    }
                };
        return new FileSystemToolCallbackProvider(
                new FileSystemTools(legacySandbox, limits),
                new GrepTool(legacySandbox, limits));
    }
}
