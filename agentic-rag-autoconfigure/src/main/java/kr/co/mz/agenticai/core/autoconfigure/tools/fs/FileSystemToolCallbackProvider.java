package kr.co.mz.agenticai.core.autoconfigure.tools.fs;

import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;

/**
 * Exposes {@link FileSystemTools} and {@link GrepTool} as a Spring AI
 * {@link ToolCallbackProvider} so that {@code CatalogToolProvider} can aggregate
 * them automatically.
 *
 * <p>This class wraps {@link MethodToolCallbackProvider} to keep
 * {@link FileSystemTools} and {@link GrepTool} plain Java objects with no Spring
 * dependency. Autoconfigure wiring (bean declaration + {@link WorkspaceSandbox} /
 * {@link OutputLimits} injection) is handled by MAE-380.
 */
public final class FileSystemToolCallbackProvider implements ToolCallbackProvider {

    private final MethodToolCallbackProvider delegate;

    public FileSystemToolCallbackProvider(FileSystemTools tools, GrepTool grepTool) {
        this.delegate = MethodToolCallbackProvider.builder()
                .toolObjects(tools, grepTool)
                .build();
    }

    @Override
    public ToolCallback[] getToolCallbacks() {
        return delegate.getToolCallbacks();
    }
}
