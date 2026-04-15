package kr.co.mz.agenticai.core.common.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

class ApplicationEventRagEventPublisherTest {

    private final ApplicationEventPublisher delegate = mock(ApplicationEventPublisher.class);
    private final ApplicationEventRagEventPublisher publisher =
            new ApplicationEventRagEventPublisher(delegate);

    @Test
    void delegatesToSpringPublisher() {
        IngestionEvent.DocumentRead evt = new IngestionEvent.DocumentRead(
                "doc-1", "file:/tmp/a.pdf", 1024, Instant.now(), "corr-1");

        publisher.publish(evt);

        verify(delegate).publishEvent(evt);
    }

    @Test
    void nullEventIsIgnored() {
        publisher.publish(null);
        verifyNoInteractions(delegate);
    }

    @Test
    void swallowsDelegateFailures() {
        IngestionEvent.DocumentRead evt = new IngestionEvent.DocumentRead(
                "d", "s", 0, Instant.now(), null);
        doThrow(new RuntimeException("kaboom")).when(delegate).publishEvent(evt);

        // Must not propagate — hot-path safety.
        publisher.publish(evt);

        assertThat(true).isTrue();
    }
}
