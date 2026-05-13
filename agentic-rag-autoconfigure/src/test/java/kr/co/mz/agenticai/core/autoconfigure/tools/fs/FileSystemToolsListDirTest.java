package kr.co.mz.agenticai.core.autoconfigure.tools.fs;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import kr.co.mz.agenticai.core.autoconfigure.tools.fs.dto.DirEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Unit tests for {@link FileSystemTools#listDir}.
 *
 * <p>Covers: non-recursive, recursive, empty directory.
 */
class FileSystemToolsListDirTest {

    @TempDir
    Path workspace;

    private FileSystemTools tools;

    @BeforeEach
    void setUp() throws IOException {
        WorkspaceSandbox sandbox = new TestWorkspaceSandbox(workspace);
        OutputLimits limits = new OutputLimits(50, 2000);
        tools = new FileSystemTools(sandbox, limits);

        Files.createDirectories(workspace.resolve("a/b"));
        Files.writeString(workspace.resolve("a/file1.txt"), "hello");
        Files.writeString(workspace.resolve("a/b/file2.txt"), "world");
    }

    @Test
    void nonRecursiveListsImmediateChildren() {
        List<DirEntry> entries = tools.listDir("a", false);
        assertThat(entries).extracting(DirEntry::name)
                .containsExactlyInAnyOrder("file1.txt", "b");
    }

    @Test
    void recursiveListsAllDescendants() {
        List<DirEntry> entries = tools.listDir("a", true);
        // Path separator is OS-specific (e.g. "b\file2.txt" on Windows)
        assertThat(entries).extracting(DirEntry::name)
                .anyMatch(n -> n.equals("file1.txt"))
                .anyMatch(n -> n.equals("b"))
                .anyMatch(n -> n.replace('\\', '/').equals("b/file2.txt"));
    }

    @Test
    void emptyDirectoryReturnsEmptyList() throws IOException {
        Files.createDirectories(workspace.resolve("empty"));
        List<DirEntry> entries = tools.listDir("empty", false);
        assertThat(entries).isEmpty();
    }

    @Test
    void fileEntriesHaveCorrectType() {
        List<DirEntry> entries = tools.listDir("a", false);
        assertThat(entries).anySatisfy(e -> {
            assertThat(e.name()).isEqualTo("file1.txt");
            assertThat(e.type()).isEqualTo("FILE");
            assertThat(e.size()).isGreaterThan(0);
        });
        assertThat(entries).anySatisfy(e -> {
            assertThat(e.name()).isEqualTo("b");
            assertThat(e.type()).isEqualTo("DIR");
        });
    }
}
