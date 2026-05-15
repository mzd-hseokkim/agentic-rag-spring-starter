package kr.co.mz.agenticai.core.common.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

class ToolInvocationEventTest {

    // ── (1) record equality / field population ────────────────────────────────

    @Test
    void startedRecord_fieldsAndEquality() {
        Instant now = Instant.now();
        var evt = new ToolInvocationEvent.Started("readFile", "args", false, now, now, "corr-1");

        assertThat(evt.toolName()).isEqualTo("readFile");
        assertThat(evt.argsSummary()).isEqualTo("args");
        assertThat(evt.truncated()).isFalse();
        assertThat(evt.startedAt()).isEqualTo(now);
        assertThat(evt.correlationId()).isEqualTo("corr-1");

        var same = new ToolInvocationEvent.Started("readFile", "args", false, now, now, "corr-1");
        assertThat(evt).isEqualTo(same).hasSameHashCodeAs(same);
    }

    @Test
    void completedRecord_fieldsAndEquality() {
        Instant now = Instant.now();
        var evt = new ToolInvocationEvent.Completed("writeFile", "args", false, now, 42L, null, now, "corr-2");

        assertThat(evt.toolName()).isEqualTo("writeFile");
        assertThat(evt.durationMillis()).isEqualTo(42L);
        assertThat(evt.error()).isNull();

        var same = new ToolInvocationEvent.Completed("writeFile", "args", false, now, 42L, null, now, "corr-2");
        assertThat(evt).isEqualTo(same);
    }

    // ── (2) ApplicationEventRagEventPublisher round-trip ─────────────────────

    @Test
    void publisher_roundTrip_startAndEndEventsDelivered() {
        ApplicationEventPublisher delegate = mock(ApplicationEventPublisher.class);
        ApplicationEventRagEventPublisher publisher = new ApplicationEventRagEventPublisher(delegate);

        Instant now = Instant.now();
        var started = new ToolInvocationEvent.Started("listDir", "path=/tmp", false, now, now, "c1");
        var completed = new ToolInvocationEvent.Completed("listDir", "path=/tmp", false, now, 10L, null, now, "c1");

        publisher.publish(started);
        publisher.publish(completed);

        ArgumentCaptor<Object> captor = forClass(Object.class);
        verify(delegate, times(2)).publishEvent(captor.capture());

        assertThat(captor.getAllValues().get(0)).isInstanceOf(ToolInvocationEvent.Started.class);
        assertThat(captor.getAllValues().get(1)).isInstanceOf(ToolInvocationEvent.Completed.class);
    }

    // ── (3) error field is non-null on exception case ─────────────────────────

    @Test
    void completedRecord_errorFieldIsNonNullWhenToolThrew() {
        Instant now = Instant.now();
        String msg = "file not found";
        var evt = new ToolInvocationEvent.Completed("deleteFile", "path=/x", false, now, 5L, msg, now, "c2");

        assertThat(evt.error()).isEqualTo(msg);
    }

    // ── (4) truncated=true when argsSummary exceeded ARGS_SUMMARY_MAX_LENGTH ──

    @Test
    void startedRecord_truncatedTrueWhenArgsSummaryExceedsMaxLength() {
        String longArgs = "x".repeat(ToolInvocationEvent.ARGS_SUMMARY_MAX_LENGTH + 1);
        String truncatedArgs = longArgs.substring(0, ToolInvocationEvent.ARGS_SUMMARY_MAX_LENGTH);
        Instant now = Instant.now();

        var evt = new ToolInvocationEvent.Started("readFile", truncatedArgs, true, now, now, "c3");

        assertThat(evt.argsSummary()).hasSize(ToolInvocationEvent.ARGS_SUMMARY_MAX_LENGTH);
        assertThat(evt.truncated()).isTrue();
    }

    // ── implements RagEvent ───────────────────────────────────────────────────

    @Test
    void startedAndCompleted_implementRagEvent() {
        Instant now = Instant.now();
        var started = new ToolInvocationEvent.Started("t", "a", false, now, now, null);
        var completed = new ToolInvocationEvent.Completed("t", "a", false, now, 0L, null, now, null);

        assertThat(started).isInstanceOf(RagEvent.class);
        assertThat(completed).isInstanceOf(RagEvent.class);
    }
}
