package kr.co.mz.agenticai.core.common.spi;

import kr.co.mz.agenticai.core.common.RagRequest;
import kr.co.mz.agenticai.core.common.RagResponse;

/**
 * Runs a (possibly multi-step) chain of {@link Agent}s for a single
 * {@link RagRequest} and returns the assembled answer.
 */
public interface AgentOrchestrator {

    RagResponse run(RagRequest request);
}
