package kr.co.mz.agenticai.core.autoconfigure.tools;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import kr.co.mz.agenticai.core.common.spi.ToolProvider;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;

/**
 * Aggregates every {@link ToolCallbackProvider} bean in the context — covers
 * Spring AI {@code @Tool} method beans and any MCP tool sources alike — and
 * applies optional allow/deny filtering by tool name.
 *
 * <p>An empty {@code allowed} set means "no inclusion constraint"; the
 * {@code denied} set always wins over {@code allowed}.
 */
public final class CatalogToolProvider implements ToolProvider {

    private final List<ToolCallbackProvider> providers;
    private final Set<String> allowed;
    private final Set<String> denied;

    public CatalogToolProvider(
            List<ToolCallbackProvider> providers,
            Collection<String> allowed,
            Collection<String> denied) {
        this.providers = List.copyOf(Objects.requireNonNull(providers, "providers"));
        this.allowed = allowed == null ? Set.of() : Set.copyOf(allowed);
        this.denied = denied == null ? Set.of() : Set.copyOf(denied);
    }

    @Override
    public List<ToolCallback> tools() {
        List<ToolCallback> out = new ArrayList<>();
        for (ToolCallbackProvider provider : providers) {
            ToolCallback[] callbacks = provider.getToolCallbacks();
            if (callbacks == null) {
                continue;
            }
            for (ToolCallback cb : callbacks) {
                if (passesFilter(cb)) {
                    out.add(cb);
                }
            }
        }
        return List.copyOf(out);
    }

    private boolean passesFilter(ToolCallback cb) {
        String name = cb.getToolDefinition().name();
        if (denied.contains(name)) {
            return false;
        }
        return allowed.isEmpty() || allowed.contains(name);
    }
}
