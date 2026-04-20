package kr.co.mz.agenticai.core.common.tool;

import java.util.List;
import kr.co.mz.agenticai.core.common.spi.ToolProvider;
import org.springframework.ai.tool.ToolCallback;

/**
 * Default {@link ToolProvider}: no tools. Replace with a custom bean to
 * expose function-calling tools to agents.
 */
public final class EmptyToolProvider implements ToolProvider {

    @Override
    public List<ToolCallback> tools() {
        return List.of();
    }
}
