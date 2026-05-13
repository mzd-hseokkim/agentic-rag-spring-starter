package kr.co.mz.agenticai.core.autoconfigure.tools.fs;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.tool.ToolCallback;

/**
 * Verifies that {@link FileSystemToolCallbackProvider} exposes tool definitions
 * with the expected names ({@code fs_glob}, {@code fs_listDir}, {@code fs_readFile}).
 */
class FileSystemToolCallbackProviderTest {

    @TempDir
    Path workspace;

    @Test
    void exposesExpectedToolNames() throws IOException {
        Files.createDirectories(workspace);
        WorkspaceSandbox sandbox = new TestWorkspaceSandbox(workspace);
        FileSystemTools fsTools = new FileSystemTools(sandbox, OutputLimits.defaults());
        FileSystemToolCallbackProvider provider = new FileSystemToolCallbackProvider(fsTools);

        List<String> names = Arrays.stream(provider.getToolCallbacks())
                .map(cb -> cb.getToolDefinition().name())
                .toList();

        assertThat(names).containsExactlyInAnyOrder("fs_glob", "fs_listDir", "fs_readFile");
    }
}
