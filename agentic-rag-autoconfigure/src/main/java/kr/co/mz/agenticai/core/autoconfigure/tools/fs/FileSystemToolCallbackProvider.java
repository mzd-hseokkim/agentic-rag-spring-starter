package kr.co.mz.agenticai.core.autoconfigure.tools.fs;

import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;

/**
 * Exposes {@link FileSystemTools} as a Spring AI {@link ToolCallbackProvider} so that
 * {@code CatalogToolProvider} can aggregate it automatically.
 *
 * <p>This class wraps {@link MethodToolCallbackProvider} to keep
 * {@link FileSystemTools} a plain Java object with no Spring dependency.
 * Autoconfigure wiring (bean declaration + {@link WorkspaceSandbox} / {@link OutputLimits}
 * injection) is handled by MAE-380.
 */
public final class FileSystemToolCallbackProvider implements ToolCallbackProvider {

    private final MethodToolCallbackProvider delegate;

    public FileSystemToolCallbackProvider(FileSystemTools tools) {
        this.delegate = MethodToolCallbackProvider.builder()
                .toolObjects(tools)
                .build();
    }

    @Override
    public ToolCallback[] getToolCallbacks() {
        return delegate.getToolCallbacks();
    }
}
