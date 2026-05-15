package kr.co.mz.agenticai.core.common.memory.policy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import kr.co.mz.agenticai.core.common.memory.MemoryRecord;
import kr.co.mz.agenticai.core.common.spi.MemoryStore;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;

/**
 * Bidirectional bridge between Spring AI's {@link ChatMemory} and this
 * library's {@link MemoryStore}. Allows callers who prefer the Spring AI
 * advisor API to use the same backing store.
 *
 * <p>Register this bean when you need ChatClient/Advisor interop. The
 * underlying storage is always the {@link MemoryStore} bean — no duplicate
 * persistence occurs.
 */
public final class SpringAiChatMemoryAdapter implements ChatMemory {

    private static final int DEFAULT_MAX_MESSAGES = Integer.MAX_VALUE;

    private final MemoryStore store;

    public SpringAiChatMemoryAdapter(MemoryStore store) {
        this.store = Objects.requireNonNull(store, "store");
    }

    @Override
    public void add(String conversationId, List<Message> messages) {
        Objects.requireNonNull(conversationId, "conversationId");
        if (messages == null) {
            return;
        }
        for (Message m : messages) {
            MemoryRecord rec = toRecord(m);
            if (rec != null) {
                store.append(conversationId, rec);
            }
        }
    }

    @Override
    public List<Message> get(String conversationId) {
        List<MemoryRecord> records = store.history(conversationId, DEFAULT_MAX_MESSAGES);
        List<Message> result = new ArrayList<>(records.size());
        for (MemoryRecord rec : records) {
            Message m = toMessage(rec);
            if (m != null) {
                result.add(m);
            }
        }
        return result;
    }

    @Override
    public void clear(String conversationId) {
        store.clear(conversationId);
    }

    private static MemoryRecord toRecord(Message message) {
        if (message == null) {
            return null;
        }
        String text = message.getText();
        if (text == null) {
            text = "";
        }
        MessageType type = message.getMessageType();
        MemoryRecord.Role role = switch (type) {
            case USER -> MemoryRecord.Role.USER;
            case ASSISTANT -> MemoryRecord.Role.ASSISTANT;
            case SYSTEM -> MemoryRecord.Role.SYSTEM;
            case TOOL -> MemoryRecord.Role.TOOL;
        };
        return new MemoryRecord(role, text, Instant.now());
    }

    private static Message toMessage(MemoryRecord rec) {
        return switch (rec.role()) {
            case USER -> new UserMessage(rec.content());
            case ASSISTANT -> new AssistantMessage(rec.content());
            case SYSTEM -> new SystemMessage(rec.content());
            case TOOL -> null;
        };
    }
}
