package kr.co.mz.agenticai.core.autoconfigure;

import java.nio.file.Path;
import kr.co.mz.agenticai.core.autoconfigure.tools.CatalogToolProvider;
import kr.co.mz.agenticai.core.common.spi.ToolProvider;
import kr.co.mz.agenticai.core.common.spi.OutputLimits;
import kr.co.mz.agenticai.core.common.spi.WorkspaceSandbox;
import kr.co.mz.agenticai.core.common.tool.DefaultWorkspaceSandbox;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Registers a {@link CatalogToolProvider} that aggregates every
 * {@link ToolCallbackProvider} bean (Spring AI {@code @Tool} beans, MCP tool
 * sources, etc.). Runs before {@link AgenticRagCoreAutoConfiguration} so the
 * default {@code EmptyToolProvider} only takes effect when no
 * {@code ToolCallbackProvider} is present.
 */
@AutoConfiguration(before = AgenticRagCoreAutoConfiguration.class)
@ConditionalOnClass(ToolCallbackProvider.class)
@ConditionalOnProperty(prefix = "agentic-rag.tools", name = "enabled",
        havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(AgenticRagProperties.class)
public class AgenticRagToolsAutoConfiguration {

    @Bean
    @ConditionalOnBean(ToolCallbackProvider.class)
    @ConditionalOnMissingBean(ToolProvider.class)
    public ToolProvider catalogToolProvider(
            ObjectProvider<ToolCallbackProvider> providers,
            AgenticRagProperties props) {
        AgenticRagProperties.Tools cfg = props.getTools();
        return new CatalogToolProvider(
                providers.orderedStream().toList(),
                cfg.getAllowedNames(),
                cfg.getDeniedNames());
    }

    @Bean
    @ConditionalOnMissingBean(WorkspaceSandbox.class)
    public WorkspaceSandbox workspaceSandbox(AgenticRagProperties props) {
        AgenticRagProperties.Fs fsCfg = props.getTools().getFs();
        Path root = fsCfg.getRoot().isBlank()
                ? Path.of(System.getProperty("user.dir"))
                : Path.of(fsCfg.getRoot());
        OutputLimits limits = new OutputLimits(
                fsCfg.getMaxReadBytes(),
                fsCfg.getMaxReadLines(),
                fsCfg.getMaxListEntries());
        return new DefaultWorkspaceSandbox(root, limits, fsCfg.isRespectGitignore());
    }
}
