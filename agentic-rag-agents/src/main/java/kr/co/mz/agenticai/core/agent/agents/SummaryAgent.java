package kr.co.mz.agenticai.core.agent.agents;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import kr.co.mz.agenticai.core.common.AgentContext;
import kr.co.mz.agenticai.core.common.Citation;
import kr.co.mz.agenticai.core.common.memory.MemoryRecord;
import kr.co.mz.agenticai.core.common.spi.Agent;
import kr.co.mz.agenticai.core.common.spi.MemoryStore;
import kr.co.mz.agenticai.core.common.spi.ToolProvider;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;

/**
 * Generates the final answer with the LLM, using selected sources as
 * context. For {@code conversational} intent (no sources retrieved), the
 * model answers from its own knowledge with the system prompt only.
 *
 * <p>When a {@link MemoryStore} is supplied and the request carries a
 * {@code sessionId}, prior conversation turns are prefixed to the prompt
 * and the new user/assistant exchange is appended after the call. When a
 * {@link ToolProvider} supplies tool callbacks, those are attached to the
 * LLM call so the model may invoke them.
 */
public final class SummaryAgent implements Agent {

    public static final String CANONICAL_NAME = "summary";
    public static final int DEFAULT_HISTORY_LIMIT = 10;

    private final ChatModel chatModel;
    private final ToolProvider toolProvider;
    private final MemoryStore memoryStore;
    private final String systemPrompt;
    private final String userPromptTemplate;
    private final int historyLimit;

    public SummaryAgent(ChatModel chatModel) {
        this(chatModel, null, null,
                KoreanAgentPrompts.SUMMARY_SYSTEM, KoreanAgentPrompts.SUMMARY_USER,
                DEFAULT_HISTORY_LIMIT);
    }

    public SummaryAgent(ChatModel chatModel, String systemPrompt, String userPromptTemplate) {
        this(chatModel, null, null, systemPrompt, userPromptTemplate, DEFAULT_HISTORY_LIMIT);
    }

    public SummaryAgent(ChatModel chatModel, ToolProvider toolProvider, MemoryStore memoryStore) {
        this(chatModel, toolProvider, memoryStore,
                KoreanAgentPrompts.SUMMARY_SYSTEM, KoreanAgentPrompts.SUMMARY_USER,
                DEFAULT_HISTORY_LIMIT);
    }

    public SummaryAgent(
            ChatModel chatModel,
            ToolProvider toolProvider,
            MemoryStore memoryStore,
            String systemPrompt,
            String userPromptTemplate,
            int historyLimit) {
        this.chatModel = Objects.requireNonNull(chatModel, "chatModel");
        this.toolProvider = toolProvider;
        this.memoryStore = memoryStore;
        this.systemPrompt = Objects.requireNonNull(systemPrompt, "systemPrompt");
        this.userPromptTemplate = Objects.requireNonNull(userPromptTemplate, "userPromptTemplate");
        if (!userPromptTemplate.contains("{query}") || !userPromptTemplate.contains("{sources}")) {
            throw new IllegalArgumentException("userPromptTemplate needs {query} and {sources}");
        }
        this.historyLimit = historyLimit;
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
        String conversationId = context.request().sessionId();

        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(systemPrompt));
        if (conversationId != null && memoryStore != null && historyLimit > 0) {
            for (MemoryRecord rec : memoryStore.history(conversationId, historyLimit)) {
                Message m = toMessage(rec);
                if (m != null) {
                    messages.add(m);
                }
            }
        }
        messages.add(new UserMessage(userText));

        Prompt prompt = buildPrompt(messages);
        String answer = chatModel.call(prompt).getResult().getOutput().getText();
        String safeAnswer = answer == null ? "" : answer;
        context.setAnswer(safeAnswer);
        // Default citations from selected sources; ValidationAgent may refine.
        context.setCitations(Citation.fromDocuments(context.selectedSources()));
        context.recordStep(CANONICAL_NAME);

        if (conversationId != null && memoryStore != null) {
            memoryStore.append(conversationId, MemoryRecord.user(context.request().query()));
            memoryStore.append(conversationId, MemoryRecord.assistant(safeAnswer));
        }
    }

    private Prompt buildPrompt(List<Message> messages) {
        List<ToolCallback> tools = toolProvider == null ? List.of() : toolProvider.tools();
        if (tools == null || tools.isEmpty()) {
            return new Prompt(messages);
        }
        ToolCallingChatOptions options = ToolCallingChatOptions.builder()
                .toolCallbacks(tools)
                .build();
        return new Prompt(messages, options);
    }

    private static Message toMessage(MemoryRecord rec) {
        return switch (rec.role()) {
            case USER -> new UserMessage(rec.content());
            case ASSISTANT -> new AssistantMessage(rec.content());
            case SYSTEM -> new SystemMessage(rec.content());
            // Tool messages are not replayed into history for the summary call.
            case TOOL -> null;
        };
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
