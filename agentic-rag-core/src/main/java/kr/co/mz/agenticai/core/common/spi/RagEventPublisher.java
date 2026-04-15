package kr.co.mz.agenticai.core.common.spi;

import kr.co.mz.agenticai.core.common.event.RagEvent;

/**
 * Publishes {@link RagEvent}s to whatever external system the host
 * application chooses (Kafka, HTTP, Slack, ...). The default implementation
 * delegates to Spring's {@code ApplicationEventPublisher}; users may register
 * their own bean of this type to override it.
 *
 * <p>Implementations MUST be non-blocking or fast enough to be safely called
 * on the hot path of LLM token streaming.
 */
@FunctionalInterface
public interface RagEventPublisher {

    /** Publish an event. Must not throw to callers. */
    void publish(RagEvent event);
}
