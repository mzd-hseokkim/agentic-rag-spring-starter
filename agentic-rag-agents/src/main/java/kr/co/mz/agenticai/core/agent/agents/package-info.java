/**
 * Six default {@link kr.co.mz.agenticai.core.common.spi.Agent} templates:
 * Intent → Retrieval → Interpretation → DataConstruction → Summary →
 * Validation. Most are deterministic; only Intent and Summary require an
 * LLM, and Validation delegates to {@code FactChecker}.
 */
package kr.co.mz.agenticai.core.agent.agents;
