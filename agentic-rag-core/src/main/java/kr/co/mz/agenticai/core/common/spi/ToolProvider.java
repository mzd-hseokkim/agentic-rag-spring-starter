package kr.co.mz.agenticai.core.common.spi;

import java.util.List;
import org.springframework.ai.tool.ToolCallback;

/**
 * Supplies tool callbacks that agents can attach to a {@code ChatClient} or
 * {@code ChatModel} request. The default registered implementation returns
 * an empty list; register a bean of this type to expose custom or
 * MCP-sourced tools.
 *
 * <p>Implementations should be cheap to call — the provider is consulted
 * per agent invocation.
 */
public interface ToolProvider {

    List<ToolCallback> tools();
}
