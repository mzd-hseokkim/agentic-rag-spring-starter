package kr.co.mz.agenticai.core.common.event;

import java.util.Objects;
import kr.co.mz.agenticai.core.common.spi.RagEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;

/**
 * Default {@link RagEventPublisher} that republishes events as
 * Spring {@link ApplicationEventPublisher} events so in-process
 * {@code @EventListener}s can consume them. Library users wanting to
 * forward events to Kafka, HTTP, Slack, etc. should register their own
 * {@link RagEventPublisher} bean — auto-configuration will then skip
 * this default.
 */
public final class ApplicationEventRagEventPublisher implements RagEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(ApplicationEventRagEventPublisher.class);

    private final ApplicationEventPublisher delegate;

    public ApplicationEventRagEventPublisher(ApplicationEventPublisher delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    @Override
    public void publish(RagEvent event) {
        if (event == null) {
            return;
        }
        try {
            delegate.publishEvent(event);
        } catch (RuntimeException e) {
            log.warn("Failed to publish RagEvent {}: {}", event.getClass().getSimpleName(), e.toString());
        }
    }
}
