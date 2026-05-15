package kr.co.mz.agenticai.core.autoconfigure.tools.fs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Unit tests for {@link FileSystemTools#glob}.
 *
 * <p>Covers: normal match, zero-match empty result, sandbox rejection.
 */
class FileSystemToolsGlobTest {

    @TempDir
    Path workspace;

    private FileSystemTools tools;
    private WorkspaceSandbox sandbox;

    @BeforeEach
    void setUp() throws IOException {
        sandbox = new TestWorkspaceSandbox(workspace);
        OutputLimits limits = new OutputLimits(50, 2000);
        tools = new FileSystemTools(sandbox, limits);

        // Create a small file tree
        Files.createDirectories(workspace.resolve("src/main"));
        Files.createDirectories(workspace.resolve("src/test"));
        Files.writeString(workspace.resolve("src/main/Foo.java"), "class Foo {}");
        Files.writeString(workspace.resolve("src/main/Bar.java"), "class Bar {}");
        Files.writeString(workspace.resolve("src/test/FooTest.java"), "class FooTest {}");
        Files.writeString(workspace.resolve("README.md"), "# readme");
    }

    @Test
    void matchesJavaFiles() {
        List<String> result = tools.glob("**/*.java", null, null);
        assertThat(result).hasSize(3).allMatch(p -> p.endsWith(".java"));
    }

    @Test
    void returnsEmptyListWhenNoMatch() {
        List<String> result = tools.glob("**/*.kt", null, null);
        assertThat(result).isEmpty();
    }

    @Test
    void sandboxRejectsPathTraversal() {
        assertThatThrownBy(() -> tools.glob("**/*.java", "../outside", null))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    void headLimitTruncatesResults() throws IOException {
        // Add enough files to exceed headLimit=1
        Files.writeString(workspace.resolve("Extra.java"), "class Extra {}");

        List<String> result = tools.glob("**/*.java", null, 1);
        assertThat(result).hasSize(2); // 1 file + truncation sentinel
        assertThat(result.get(1)).startsWith("(truncated,");
    }
}
