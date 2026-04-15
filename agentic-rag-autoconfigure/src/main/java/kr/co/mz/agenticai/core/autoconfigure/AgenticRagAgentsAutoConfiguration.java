package kr.co.mz.agenticai.core.autoconfigure;

import java.util.List;
import kr.co.mz.agenticai.core.agent.agents.DataConstructionAgent;
import kr.co.mz.agenticai.core.agent.agents.IntentAnalysisAgent;
import kr.co.mz.agenticai.core.agent.agents.InterpretationAgent;
import kr.co.mz.agenticai.core.agent.agents.RetrievalAgent;
import kr.co.mz.agenticai.core.agent.agents.SummaryAgent;
import kr.co.mz.agenticai.core.agent.agents.ValidationAgent;
import kr.co.mz.agenticai.core.agent.client.OrchestratorAgenticRagClient;
import kr.co.mz.agenticai.core.agent.orchestrator.SequentialAgentOrchestrator;
import kr.co.mz.agenticai.core.common.AgenticRagClient;
import kr.co.mz.agenticai.core.common.spi.Agent;
import kr.co.mz.agenticai.core.common.spi.AgentOrchestrator;
import kr.co.mz.agenticai.core.common.spi.FactChecker;
import kr.co.mz.agenticai.core.common.spi.RagEventPublisher;
import kr.co.mz.agenticai.core.common.spi.RetrieverRouter;
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
        AgenticRagFactCheckAutoConfiguration.class,
        AgenticRagClientAutoConfiguration.class
})
@ConditionalOnProperty(prefix = "agentic-rag.agents", name = "enabled", havingValue = "true")
public class AgenticRagAgentsAutoConfiguration {

    @Bean(name = "intentAnalysisAgent")
    @ConditionalOnMissingBean(name = "intentAnalysisAgent")
    @ConditionalOnBean(ChatModel.class)
    public Agent intentAnalysisAgent(ChatModel chatModel) {
        return new IntentAnalysisAgent(chatModel);
    }

    @Bean(name = "retrievalAgent")
    @ConditionalOnMissingBean(name = "retrievalAgent")
    @ConditionalOnBean(RetrieverRouter.class)
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
    @ConditionalOnBean(ChatModel.class)
    public Agent summaryAgent(ChatModel chatModel) {
        return new SummaryAgent(chatModel);
    }

    @Bean(name = "validationAgent")
    @ConditionalOnMissingBean(name = "validationAgent")
    public Agent validationAgent(ObjectProvider<FactChecker> factChecker) {
        return new ValidationAgent(factChecker.getIfAvailable());
    }

    @Bean
    @ConditionalOnMissingBean(AgentOrchestrator.class)
    @ConditionalOnBean(value = {ChatModel.class, RetrieverRouter.class})
    public AgentOrchestrator agentOrchestrator(
            List<Agent> agents,
            RagEventPublisher events,
            AgenticRagProperties props) {
        return new SequentialAgentOrchestrator(
                orderAgents(agents), events,
                props.getAgents().getMaxIterations(),
                RetrievalAgent.NAME);
    }

    /**
     * Orders the supplied agents into the canonical
     * intent → retrieval → interpretation → data-construction →
     * summary → validation pipeline. Unknown agent names are appended
     * after the standard set so user-added agents still execute.
     */
    private static List<Agent> orderAgents(List<Agent> agents) {
        List<String> order = List.of(
                IntentAnalysisAgent.NAME,
                RetrievalAgent.NAME,
                InterpretationAgent.NAME,
                DataConstructionAgent.NAME,
                SummaryAgent.NAME,
                ValidationAgent.NAME);
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
