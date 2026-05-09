package kr.co.mz.agenticai.core.common.spi;

import java.util.List;
import kr.co.mz.agenticai.core.common.memory.MemoryRecord;
import org.springframework.ai.chat.messages.Message;

/**
 * Trims raw conversation history into the {@link Message} list that will be
 * sent to the LLM. Decouples storage ({@link MemoryStore}) from the
 * message-window strategy.
 *
 * <p>Register a bean of this type to replace the default
 * {@code RecentMessagesPolicy}. All built-in implementations are registered
 * with {@code @ConditionalOnMissingBean}.
 */
public interface MemoryPolicy {

    /**
     * Converts raw history into an ordered, LLM-ready message list.
     *
     * @param history      full conversation history, oldest-to-newest
     * @param query        the current user query (unused by simple policies;
     *                     available for semantic-selection strategies)
     * @param systemPrompt the system prompt configured for this agent;
     *                     rolling-summary policies may append a summary block here
     * @return messages to prepend before the current user message; most policies
     *         return only history messages, but rolling-summary policies may
     *         return a {@link org.springframework.ai.chat.messages.SystemMessage}
     *         as the first element — the caller must check and use it instead of
     *         constructing its own system message
     */
    List<Message> apply(List<MemoryRecord> history, String query, String systemPrompt);
}
