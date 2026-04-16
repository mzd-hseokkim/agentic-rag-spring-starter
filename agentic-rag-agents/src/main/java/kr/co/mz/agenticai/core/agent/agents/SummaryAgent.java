package kr.co.mz.agenticai.core.agent.agents;

import java.util.List;
import java.util.Objects;
import kr.co.mz.agenticai.core.common.AgentContext;
import kr.co.mz.agenticai.core.common.Citation;
import kr.co.mz.agenticai.core.common.spi.Agent;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;

/**
 * Generates the final answer with the LLM, using selected sources as
 * context. For {@code conversational} intent (no sources retrieved), the
 * model answers from its own knowledge with the system prompt only.
 */
public final class SummaryAgent implements Agent {

    public static final String CANONICAL_NAME = "summary";

    private final ChatModel chatModel;
    private final String systemPrompt;
    private final String userPromptTemplate;

    public SummaryAgent(ChatModel chatModel) {
        this(chatModel, KoreanAgentPrompts.SUMMARY_SYSTEM, KoreanAgentPrompts.SUMMARY_USER);
    }

    public SummaryAgent(ChatModel chatModel, String systemPrompt, String userPromptTemplate) {
        this.chatModel = Objects.requireNonNull(chatModel, "chatModel");
        this.systemPrompt = Objects.requireNonNull(systemPrompt, "systemPrompt");
        this.userPromptTemplate = Objects.requireNonNull(userPromptTemplate, "userPromptTemplate");
        if (!userPromptTemplate.contains("{query}") || !userPromptTemplate.contains("{sources}")) {
            throw new IllegalArgumentException("userPromptTemplate needs {query} and {sources}");
        }
    }

    @Override
    public String name() {
        return CANONICAL_NAME;
    }

    @Override
    public void execute(AgentContext context) {
        String userText = userPromptTemplate
                .replace("{sources}", renderSources(context.selectedSources()))
                .replace("{query}", context.request().query());
        String answer = chatModel.call(new Prompt(List.of(
                new SystemMessage(systemPrompt),
                new UserMessage(userText)))).getResult().getOutput().getText();
        context.setAnswer(answer == null ? "" : answer);
        // Default citations from selected sources; ValidationAgent may refine.
        context.setCitations(Citation.fromDocuments(context.selectedSources()));
        context.recordStep(CANONICAL_NAME);
    }

    private static String renderSources(List<Document> sources) {
        if (sources.isEmpty()) {
            return "(없음)";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < sources.size(); i++) {
            Document d = sources.get(i);
            String src = String.valueOf(d.getMetadata().getOrDefault("source", d.getId()));
            sb.append('[').append(i).append("] (source: ").append(src).append(")\n");
            sb.append(d.getText() == null ? "" : d.getText()).append("\n\n");
        }
        return sb.toString();
    }
}
