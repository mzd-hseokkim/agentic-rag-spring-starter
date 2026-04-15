package kr.co.mz.agenticai.core.common.spi;

import kr.co.mz.agenticai.core.common.AgentContext;

/**
 * One unit of work in the orchestrator pipeline. Reads slots it needs from
 * the {@link AgentContext} and writes the slots it produces. Implementations
 * should be stateless across invocations.
 */
public interface Agent {

    /** Stable name used for tracing and bean ordering. */
    String name();

    void execute(AgentContext context);
}
