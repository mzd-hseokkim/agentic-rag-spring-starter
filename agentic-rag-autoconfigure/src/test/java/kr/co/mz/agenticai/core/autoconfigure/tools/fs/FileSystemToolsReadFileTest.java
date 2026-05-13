package kr.co.mz.agenticai.core.autoconfigure.tools.fs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Unit tests for {@link FileSystemTools#readFile}.
 *
 * <p>Covers: offset/limit slice, out-of-range offset empty result, sandbox rejection.
 */
class FileSystemToolsReadFileTest {

    @TempDir
    Path workspace;

    private FileSystemTools tools;

    @BeforeEach
    void setUp() throws IOException {
        WorkspaceSandbox sandbox = new TestWorkspaceSandbox(workspace);
        OutputLimits limits = new OutputLimits(50, 2000);
        tools = new FileSystemTools(sandbox, limits);

        // 5-line file
        Files.writeString(workspace.resolve("sample.txt"),
                "line1\nline2\nline3\nline4\nline5\n");
    }

    @Test
    void readsAllLinesWithLineNumberPrefix() {
        String result = tools.readFile("sample.txt", null, null);
        assertThat(result).contains("     1\tline1");
        assertThat(result).contains("     5\tline5");
    }

    @Test
    void offsetAndLimitSlice() {
        String result = tools.readFile("sample.txt", 1, 2);
        assertThat(result).contains("     2\tline2");
        assertThat(result).contains("     3\tline3");
        assertThat(result).doesNotContain("line1");
        assertThat(result).doesNotContain("line4");
    }

    @Test
    void offsetBeyondFileLengthReturnsEmpty() {
        String result = tools.readFile("sample.txt", 100, null);
        assertThat(result).isEmpty();
    }

    @Test
    void sandboxRejectsPathTraversal() {
        assertThatThrownBy(() -> tools.readFile("../secret.txt", null, null))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    void truncationSentinelAppendedWhenLimitExceeded() throws IOException {
        // File with 5 lines; limit = 2 → should truncate
        String result = tools.readFile("sample.txt", 0, 2);
        assertThat(result).contains("(truncated, more lines available — increase limit or use offset)");
    }
}
