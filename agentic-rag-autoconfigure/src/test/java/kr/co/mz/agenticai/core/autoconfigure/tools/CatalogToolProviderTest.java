package kr.co.mz.agenticai.core.autoconfigure.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.definition.ToolDefinition;

class CatalogToolProviderTest {

    private static ToolCallback callbackNamed(String name) {
        ToolCallback cb = mock(ToolCallback.class);
        ToolDefinition def = mock(ToolDefinition.class);
        when(def.name()).thenReturn(name);
        when(cb.getToolDefinition()).thenReturn(def);
        return cb;
    }

    private static ToolCallbackProvider providerOf(ToolCallback... callbacks) {
        return () -> callbacks;
    }

    @Test
    void aggregatesAcrossMultipleProviders() {
        var p1 = providerOf(callbackNamed("a"), callbackNamed("b"));
        var p2 = providerOf(callbackNamed("c"));

        List<ToolCallback> tools = new CatalogToolProvider(
                List.of(p1, p2), List.of(), List.of()).tools();

        assertThat(tools).extracting(cb -> cb.getToolDefinition().name())
                .containsExactly("a", "b", "c");
    }

    @Test
    void allowedNamesActAsInclusionFilter() {
        var p = providerOf(callbackNamed("a"), callbackNamed("b"), callbackNamed("c"));

        List<ToolCallback> tools = new CatalogToolProvider(
                List.of(p), List.of("a", "c"), List.of()).tools();

        assertThat(tools).extracting(cb -> cb.getToolDefinition().name())
                .containsExactly("a", "c");
    }

    @Test
    void deniedNamesAreExcluded() {
        var p = providerOf(callbackNamed("a"), callbackNamed("b"), callbackNamed("c"));

        List<ToolCallback> tools = new CatalogToolProvider(
                List.of(p), List.of(), List.of("b")).tools();

        assertThat(tools).extracting(cb -> cb.getToolDefinition().name())
                .containsExactly("a", "c");
    }

    @Test
    void deniedWinsOverAllowed() {
        var p = providerOf(callbackNamed("a"), callbackNamed("b"));

        List<ToolCallback> tools = new CatalogToolProvider(
                List.of(p), List.of("a", "b"), List.of("b")).tools();

        assertThat(tools).extracting(cb -> cb.getToolDefinition().name())
                .containsExactly("a");
    }

    @Test
    void nullCallbackArrayIsHandled() {
        ToolCallbackProvider noisy = () -> null;
        var p = providerOf(callbackNamed("a"));

        List<ToolCallback> tools = new CatalogToolProvider(
                List.of(noisy, p), List.of(), List.of()).tools();

        assertThat(tools).extracting(cb -> cb.getToolDefinition().name())
                .containsExactly("a");
    }

    @Test
    void noProvidersYieldsEmpty() {
        assertThat(new CatalogToolProvider(List.of(), List.of(), List.of()).tools()).isEmpty();
    }
}
