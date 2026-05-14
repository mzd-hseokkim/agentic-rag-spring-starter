package kr.co.mz.agenticai.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import kr.co.mz.agenticai.core.common.IngestionPipeline;
import kr.co.mz.agenticai.core.common.spi.ToolProvider;
import kr.co.mz.agenticai.core.common.spi.WorkspaceSandbox;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

/**
 * Integration test: verifies that the fs tools profile wires {@link WorkspaceSandbox} and
 * exposes {@code fs_glob}, {@code fs_listDir}, {@code fs_readFile} through {@link ToolProvider},
 * and that path-traversal attempts are rejected.
 *
 * <p>All external dependencies (Ollama, EmbeddingModel, IngestionPipeline) are replaced
 * with mocks so no running infrastructure is required.
 */
@SpringBootTest
@ActiveProfiles({"agents", "fs"})
class IT_FileSystemTools {

    @MockBean
    ChatModel chatModel;

    @MockBean
    EmbeddingModel embeddingModel;

    @MockBean
    OllamaApi ollamaApi;

    @MockBean
    IngestionPipeline ingestionPipeline;

    @Autowired
    ToolProvider toolProvider;

    @Autowired
    WorkspaceSandbox workspaceSandbox;

    /** AC-1: CatalogToolProvider aggregates FileSystemToolCallbackProvider — all three fs tools exposed. */
    @Test
    void fsToolsExposedViaCatalogToolProvider() {
        List<ToolCallback> tools = toolProvider.tools();
        List<String> toolNames = tools.stream()
                .map(t -> t.getToolDefinition().name())
                .toList();

        assertThat(toolNames).contains("fs_glob", "fs_listDir", "fs_readFile");
    }

    /** AC-2: sandbox resolves a valid relative path inside the workspace root. */
    @Test
    void sandboxResolvesValidPath() throws Exception {
        var resolved = workspaceSandbox.resolve(".");
        assertThat(resolved).isNotNull();
        assertThat(resolved.toFile()).exists();
    }

    /**
     * AC-3a: path traversal via '../' is rejected.
     * On Windows the non-existent target causes IOException during toRealPath();
     * on POSIX the root check triggers SecurityException. Either is correct.
     */
    @Test
    void sandboxBlocksParentTraversal() {
        assertThatThrownBy(() -> workspaceSandbox.resolve("../../../etc/passwd"))
                .isInstanceOfAny(SecurityException.class, java.io.IOException.class);
    }

    /** AC-3b: absolute path outside root is blocked immediately with SecurityException. */
    @Test
    void sandboxBlocksAbsoluteEscape() {
        // tmpdir is always absolute — rejected before any filesystem access.
        String outside = System.getProperty("java.io.tmpdir");
        assertThatThrownBy(() -> workspaceSandbox.resolve(outside))
                .isInstanceOf(SecurityException.class);
    }

    /** AC-3c: Windows-style drive-letter absolute path is rejected as absolute. */
    @Test
    void sandboxBlocksWindowsDriveAbsolutePath() {
        assertThatThrownBy(() -> workspaceSandbox.resolve("C:\\Windows\\System32"))
                .isInstanceOfAny(SecurityException.class, java.io.IOException.class);
    }
}
