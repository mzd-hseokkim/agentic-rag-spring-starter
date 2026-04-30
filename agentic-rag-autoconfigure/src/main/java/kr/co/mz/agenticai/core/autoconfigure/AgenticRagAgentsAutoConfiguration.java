package kr.co.mz.agenticai.core.autoconfigure;

import java.util.List;
import kr.co.mz.agenticai.core.agent.agents.DataConstructionAgent;
import kr.co.mz.agenticai.core.agent.agents.IntentAnalysisAgent;
import kr.co.mz.agenticai.core.agent.agents.InterpretationAgent;
import kr.co.mz.agenticai.core.agent.agents.KoreanAgentPrompts;
import kr.co.mz.agenticai.core.agent.agents.RetrievalAgent;
import kr.co.mz.agenticai.core.agent.agents.SummaryAgent;
import kr.co.mz.agenticai.core.agent.agents.ValidationAgent;
import kr.co.mz.agenticai.core.agent.client.OrchestratorAgenticRagClient;
import kr.co.mz.agenticai.core.agent.orchestrator.SequentialAgentOrchestrator;
import kr.co.mz.agenticai.core.common.AgenticRagClient;
import kr.co.mz.agenticai.core.common.spi.Agent;
import kr.co.mz.agenticai.core.common.spi.AgentOrchestrator;
import kr.co.mz.agenticai.core.common.spi.FactChecker;
import kr.co.mz.agenticai.core.common.spi.MemoryStore;
import kr.co.mz.agenticai.core.common.spi.RagEventPublisher;
import kr.co.mz.agenticai.core.common.spi.RetrieverRouter;
import kr.co.mz.agenticai.core.common.spi.ToolProvider;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

@AutoConfiguration(after = {
        AgenticRagCoreAutoConfiguration.class,
        AgenticRagRetrievalAutoConfiguration.class,
        AgenticRagFactCheckAutoConfiguration.class
})
@ConditionalOnProperty(prefix = "agentic-rag.agents", name = "enabled", havingValue = "true")
public class AgenticRagAgentsAutoConfiguration {

    // NOTE: no @ConditionalOnBean(ChatModel.class) — Spring AI model
    // auto-configs may register their ChatModel AFTER our condition is
    // evaluated, causing false negatives. We rely on constructor injection
    // to surface a clear error if no ChatModel bean exists.
    @Bean(name = "intentAnalysisAgent")
    @ConditionalOnMissingBean(name = "intentAnalysisAgent")
    public Agent intentAnalysisAgent(ChatModel chatModel) {
        return new IntentAnalysisAgent(chatModel);
    }

    @Bean(name = "retrievalAgent")
    @ConditionalOnMissingBean(name = "retrievalAgent")
    public Agent retrievalAgent(RetrieverRouter router, AgenticRagProperties props) {
        return new RetrievalAgent(router, props.getClient().getDefaultTopK());
    }

    @Bean(name = "interpretationAgent")
    @ConditionalOnMissingBean(name = "interpretationAgent")
    public Agent interpretationAgent() {
        return new InterpretationAgent();
    }

    @Bean(name = "dataConstructionAgent")
    @ConditionalOnMissingBean(name = "dataConstructionAgent")
    public Agent dataConstructionAgent(AgenticRagProperties props) {
        return new DataConstructionAgent(props.getAgents().getMaxSources());
    }

    @Bean(name = "summaryAgent")
    @ConditionalOnMissingBean(name = "summaryAgent")
    public Agent summaryAgent(
            ChatModel chatModel,
            ObjectProvider<ToolProvider> toolProvider,
            ObjectProvider<MemoryStore> memoryStore,
            AgenticRagProperties props) {
        AgenticRagProperties.Summary cfg = props.getAgents().getSummary();
        String systemPrompt = blankToNull(cfg.getSystemPrompt()) != null
                ? cfg.getSystemPrompt() : KoreanAgentPrompts.SUMMARY_SYSTEM;
        String userTemplate = blankToNull(cfg.getUserPromptTemplate()) != null
                ? cfg.getUserPromptTemplate() : KoreanAgentPrompts.SUMMARY_USER;
        return new SummaryAgent(
                chatModel,
                toolProvider.getIfAvailable(),
                memoryStore.getIfAvailable(),
                systemPrompt,
                userTemplate,
                props.getMemory().getHistoryLimit());
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }

    @Bean(name = "validationAgent")
    @ConditionalOnMissingBean(name = "validationAgent")
    public Agent validationAgent(ObjectProvider<FactChecker> factChecker, RagEventPublisher events) {
        return new ValidationAgent(factChecker.getIfAvailable(), events);
    }

    @Bean
    @ConditionalOnMissingBean(AgentOrchestrator.class)
    public AgentOrchestrator agentOrchestrator(
            List<Agent> agents,
            RagEventPublisher events,
            AgenticRagProperties props) {
        return new SequentialAgentOrchestrator(
                orderAgents(agents), events,
                props.getAgents().getMaxIterations(),
                RetrievalAgent.CANONICAL_NAME);
    }

    /**
     * Orders the supplied agents into the canonical
     * intent → retrieval → interpretation → data-construction →
     * summary → validation pipeline. Unknown agent names are appended
     * after the standard set so user-added agents still execute.
     */
    private static List<Agent> orderAgents(List<Agent> agents) {
        List<String> order = List.of(
                IntentAnalysisAgent.CANONICAL_NAME,
                RetrievalAgent.CANONICAL_NAME,
                InterpretationAgent.CANONICAL_NAME,
                DataConstructionAgent.CANONICAL_NAME,
                SummaryAgent.CANONICAL_NAME,
                ValidationAgent.CANONICAL_NAME);
        java.util.Map<String, Agent> byName = new java.util.LinkedHashMap<>();
        for (Agent a : agents) {
            byName.put(a.name(), a);
        }
        java.util.List<Agent> ordered = new java.util.ArrayList<>();
        for (String name : order) {
            Agent a = byName.remove(name);
            if (a != null) {
                ordered.add(a);
            }
        }
        ordered.addAll(byName.values());
        return ordered;
    }

    @Bean
    @ConditionalOnMissingBean(AgenticRagClient.class)
    @ConditionalOnBean(AgentOrchestrator.class)
    public AgenticRagClient orchestratorClient(
            AgentOrchestrator orchestrator,
            List<kr.co.mz.agenticai.core.common.spi.Guardrail> guardrails) {
        return new OrchestratorAgenticRagClient(orchestrator, guardrails);
    }
}
