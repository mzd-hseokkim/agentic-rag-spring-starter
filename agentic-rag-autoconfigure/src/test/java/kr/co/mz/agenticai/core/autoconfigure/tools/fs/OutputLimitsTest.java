package kr.co.mz.agenticai.core.autoconfigure.tools.fs;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Unit tests for {@link OutputLimits} truncation behaviour and
 * {@link FileSystemTools} output-limit integration.
 */
class OutputLimitsTest {

    // --- OutputLimits.apply() ---

    @Test
    void applyReturnsSameListWhenUnderLimit() {
        OutputLimits limits = new OutputLimits(3, 100);
        List<String> items = List.of("a", "b", "c");
        assertThat(limits.apply(items, "(truncated)")).isSameAs(items);
    }

    @Test
    void applyTruncatesToMaxResultsAndAppendsSentinel() {
        OutputLimits limits = new OutputLimits(3, 100);
        List<String> items = List.of("a", "b", "c", "d", "e");
        List<String> result = limits.apply(items, "(truncated)");
        assertThat(result).hasSize(4); // 3 items + sentinel
        assertThat(result.subList(0, 3)).containsExactly("a", "b", "c");
        assertThat(result.get(3)).isEqualTo("(truncated)");
    }

    @Test
    void applyExactlyAtLimitDoesNotTruncate() {
        OutputLimits limits = new OutputLimits(3, 100);
        List<String> items = List.of("x", "y", "z");
        List<String> result = limits.apply(items, "(truncated)");
        assertThat(result).doesNotContain("(truncated)");
        assertThat(result).hasSize(3);
    }

    // --- OutputLimits.applyString() ---

    @Test
    void applyStringReturnsSameWhenUnderLimit() {
        OutputLimits limits = new OutputLimits(50, 3);
        String content = "line1\nline2\nline3";
        assertThat(limits.applyString(content, 3)).isEqualTo(content);
    }

    @Test
    void applyStringTruncatesAndAppendsTruncationLine() {
        OutputLimits limits = new OutputLimits(50, 2);
        String content = "line1\nline2\nline3\nline4";
        String result = limits.applyString(content, 4);
        assertThat(result).contains("line1");
        assertThat(result).contains("line2");
        assertThat(result).doesNotContain("line3");
        assertThat(result).contains("(truncated, 2 more)");
    }

    // --- FileSystemTools.listDir() truncation ---

    @Test
    void listDirTruncatesWhenOverMaxResults(@TempDir Path workspace) throws IOException {
        WorkspaceSandbox sandbox = new TestWorkspaceSandbox(workspace);
        OutputLimits limits = new OutputLimits(3, 2000); // maxResults=3
        FileSystemTools tools = new FileSystemTools(sandbox, limits);

        // create 5 files → should be truncated to 3 + sentinel
        for (int i = 1; i <= 5; i++) {
            Files.createFile(workspace.resolve("file" + i + ".txt"));
        }

        List<kr.co.mz.agenticai.core.autoconfigure.tools.fs.dto.DirEntry> result =
                tools.listDir(".", false);

        assertThat(result).hasSize(4); // 3 entries + TRUNCATED sentinel
        assertThat(result).anySatisfy(e ->
                assertThat(e.type()).isEqualTo("TRUNCATED"));
    }

    // --- FileSystemTools.readFile() maxLines truncation ---

    @Test
    void readFileTruncatesWhenOverMaxLines(@TempDir Path workspace) throws IOException {
        WorkspaceSandbox sandbox = new TestWorkspaceSandbox(workspace);
        OutputLimits limits = new OutputLimits(50, 3); // maxLines=3
        FileSystemTools tools = new FileSystemTools(sandbox, limits);

        String content = IntStream.rangeClosed(1, 10)
                .mapToObj(i -> "line" + i)
                .collect(Collectors.joining("\n"));
        Files.writeString(workspace.resolve("big.txt"), content);

        String result = tools.readFile("big.txt", null, null);

        assertThat(result).contains("     1\tline1");
        assertThat(result).contains("     3\tline3");
        assertThat(result).doesNotContain("line4");
        assertThat(result).contains("(truncated, more lines available");
    }

    // --- FileSystemTools.glob() maxResults truncation ---

    @Test
    void globTruncatesWhenOverHeadLimit(@TempDir Path workspace) throws IOException {
        WorkspaceSandbox sandbox = new TestWorkspaceSandbox(workspace);
        OutputLimits limits = new OutputLimits(3, 2000);
        FileSystemTools tools = new FileSystemTools(sandbox, limits);

        for (int i = 1; i <= 6; i++) {
            Files.createFile(workspace.resolve("f" + i + ".txt"));
        }

        List<String> result = tools.glob("*.txt", null, null);

        // 3 entries + 1 truncation sentinel string
        assertThat(result).hasSize(4);
        assertThat(result.get(3)).startsWith("(truncated,");
    }
}
