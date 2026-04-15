package kr.co.mz.agenticai.core.agent.agents;

import java.util.Objects;
import java.util.Set;
import kr.co.mz.agenticai.core.common.AgentContext;
import kr.co.mz.agenticai.core.common.spi.Agent;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;

/**
 * Classifies the query into {@code factual}, {@code conversational}, or
 * {@code unsupported}. Skipped on later iterations (already classified).
 */
public final class IntentAnalysisAgent implements Agent {

    public static final String NAME = "intent";

    private static final Set<String> KNOWN = Set.of("factual", "conversational", "unsupported");

    private final ChatModel chatModel;
    private final String promptTemplate;

    public IntentAnalysisAgent(ChatModel chatModel) {
        this(chatModel, KoreanAgentPrompts.INTENT_CLASSIFY);
    }

    public IntentAnalysisAgent(ChatModel chatModel, String promptTemplate) {
        this.chatModel = Objects.requireNonNull(chatModel, "chatModel");
        this.promptTemplate = Objects.requireNonNull(promptTemplate, "promptTemplate");
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public void execute(AgentContext context) {
        if (context.intent() != null) {
            return;
        }
        String rendered = promptTemplate.replace("{query}", context.request().query());
        String raw = chatModel.call(new Prompt(rendered)).getResult().getOutput().getText();
        String intent = raw == null ? "" : raw.trim().toLowerCase().split("\\s+")[0]
                .replaceAll("[^a-z]", "");
        context.setIntent(KNOWN.contains(intent) ? intent : "factual");
        context.recordStep(NAME);
    }
}
