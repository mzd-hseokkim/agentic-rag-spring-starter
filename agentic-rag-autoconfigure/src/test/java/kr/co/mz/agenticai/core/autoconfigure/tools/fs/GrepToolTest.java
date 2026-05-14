package kr.co.mz.agenticai.core.autoconfigure.tools.fs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.List;
import java.util.Map;
import kr.co.mz.agenticai.core.autoconfigure.tools.fs.dto.GrepResult;
import kr.co.mz.agenticai.core.autoconfigure.tools.fs.dto.MatchLine;
import kr.co.mz.agenticai.core.autoconfigure.tools.fs.enums.GrepOutputMode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Unit tests for {@link GrepTool#grep}.
 *
 * <p>Covers: FILES_WITH_MATCHES / CONTENT / COUNT output modes, contextLines,
 * multiline matching, caseInsensitive, invalid regex error, NFC/NFD normalisation,
 * headLimit truncation, and sandbox path-traversal rejection.
 */
class GrepToolTest {

    @TempDir
    Path workspace;

    private GrepTool tool;

    @BeforeEach
    void setUp() throws IOException {
        WorkspaceSandbox sandbox = new TestWorkspaceSandbox(workspace);
        OutputLimits limits = new OutputLimits(50, 2000);
        tool = new GrepTool(sandbox, limits);

        // ── fixture files ──────────────────────────────────────────────────────
        // alpha.txt  : lines containing "hello"
        Files.writeString(workspace.resolve("alpha.txt"),
                "hello world\n" +
                "foo bar\n" +
                "hello again\n");

        // beta.txt   : no match for "hello"
        Files.writeString(workspace.resolve("beta.txt"),
                "no match here\n");

        // gamma.txt  : multiline content
        Files.writeString(workspace.resolve("gamma.txt"),
                "start\n" +
                "foo\n" +
                "bar\n" +
                "end\n");

        // delta.txt  : uppercase HELLO
        Files.writeString(workspace.resolve("delta.txt"),
                "HELLO uppercase\n");
    }

    // ── (a) FILES_WITH_MATCHES ────────────────────────────────────────────────

    @Test
    void filesWithMatches_returnsMatchingFiles() {
        GrepResult result = tool.grep("hello", null, null, null,
                GrepOutputMode.FILES_WITH_MATCHES, null, null, null, null);

        assertThat(result.files()).isNotNull();
        assertThat(result.files()).hasSize(1);
        assertThat(result.files().get(0)).endsWith("alpha.txt");
        assertThat(result.truncated()).isFalse();
    }

    @Test
    void filesWithMatches_returnsEmptyListWhenNoMatch() {
        GrepResult result = tool.grep("nonexistent_xyz", null, null, null,
                GrepOutputMode.FILES_WITH_MATCHES, null, null, null, null);

        assertThat(result.files()).isNotNull().isEmpty();
        assertThat(result.truncated()).isFalse();
    }

    // ── (b) CONTENT — basic and contextLines ─────────────────────────────────

    @Test
    void content_basicMatchLines() {
        GrepResult result = tool.grep("hello", null, null, null,
                GrepOutputMode.CONTENT, null, null, null, null);

        assertThat(result.contentLines()).isNotNull();
        List<MatchLine> lines = result.contentLines();
        assertThat(lines).isNotEmpty();
        lines.forEach(ml -> assertThat(ml.kind()).isEqualTo("MATCH"));
        assertThat(lines).allMatch(ml -> ml.line().contains("hello"));
    }

    @Test
    void content_contextLines_includesBeforeAndAfter() {
        GrepResult result = tool.grep("foo", null, null, null,
                GrepOutputMode.CONTENT, 2, null, null, null);

        assertThat(result.contentLines()).isNotNull();
        List<MatchLine> lines = result.contentLines();
        // expect BEFORE and AFTER context kinds as well as MATCH
        List<String> kinds = lines.stream().map(MatchLine::kind).distinct().toList();
        assertThat(kinds).containsAnyOf("BEFORE", "AFTER");
        assertThat(kinds).contains("MATCH");
    }

    // ── (c) COUNT ─────────────────────────────────────────────────────────────

    @Test
    void count_returnsPerFileMatchCounts() {
        GrepResult result = tool.grep("hello", null, null, null,
                GrepOutputMode.COUNT, null, null, null, null);

        assertThat(result.counts()).isNotNull();
        Map<String, Long> counts = result.counts();
        // alpha.txt has 2 occurrences of "hello"
        assertThat(counts.values()).allMatch(n -> n > 0);
        boolean foundAlpha = counts.entrySet().stream()
                .anyMatch(e -> e.getKey().contains("alpha") && e.getValue() == 2L);
        assertThat(foundAlpha).isTrue();
    }

    @Test
    void count_excludesZeroMatchFiles() {
        GrepResult result = tool.grep("hello", null, null, null,
                GrepOutputMode.COUNT, null, null, null, null);

        assertThat(result.counts()).isNotNull();
        // beta.txt has no match — must not appear in counts
        boolean betaPresent = result.counts().keySet().stream()
                .anyMatch(k -> k.contains("beta"));
        assertThat(betaPresent).isFalse();
    }

    // ── (d) multiline ────────────────────────────────────────────────────────

    @Test
    void multiline_matchesPatternSpanningLines() {
        GrepResult result = tool.grep("foo\\nbar", null, null, null,
                GrepOutputMode.FILES_WITH_MATCHES, null, true, null, null);

        assertThat(result.files()).isNotNull();
        assertThat(result.files()).anyMatch(p -> p.contains("gamma"));
    }

    // ── (e) caseInsensitive ───────────────────────────────────────────────────

    @Test
    void caseInsensitive_matchesRegardlessOfCase() {
        GrepResult result = tool.grep("hello", null, null, null,
                GrepOutputMode.FILES_WITH_MATCHES, null, null, true, null);

        assertThat(result.files()).isNotNull();
        // should match both alpha.txt (lowercase) and delta.txt (uppercase HELLO)
        assertThat(result.files()).hasSizeGreaterThanOrEqualTo(2);
    }

    // ── (f) invalid regex ─────────────────────────────────────────────────────

    @Test
    void invalidRegex_throwsIllegalArgumentExceptionWithLocation() {
        assertThatThrownBy(() ->
                tool.grep("[", null, null, null, null, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("invalid regex")
                .hasMessageContaining("at index");
    }

    // ── (g) NFC/NFD normalisation ─────────────────────────────────────────────

    @Test
    void nfcNfd_normalisation_sameResult() throws IOException {
        // Create a file with a Korean filename in NFC form
        String nfc = Normalizer.normalize("파일.txt", Normalizer.Form.NFC);
        Files.writeString(workspace.resolve(nfc), "hello 한글\n");

        GrepResult nfcResult = tool.grep("hello", nfc, null, null,
                GrepOutputMode.FILES_WITH_MATCHES, null, null, null, null);
        String nfd = Normalizer.normalize("파일.txt", Normalizer.Form.NFD);
        GrepResult nfdResult = tool.grep("hello", nfd, null, null,
                GrepOutputMode.FILES_WITH_MATCHES, null, null, null, null);

        // both should resolve to the same file and produce a match
        assertThat(nfcResult.files()).isNotEmpty();
        assertThat(nfdResult.files()).isNotEmpty();
    }

    // ── (h) headLimit / truncation ────────────────────────────────────────────

    @Test
    void headLimit_truncatesResultAndSetsTruncatedFlag() throws IOException {
        // Create 5 files each containing the pattern
        for (int i = 0; i < 5; i++) {
            Files.writeString(workspace.resolve("match" + i + ".txt"), "target\n");
        }

        // headLimit = 2 → only 2 files returned, truncated = true
        GrepResult result = tool.grep("target", null, null, null,
                GrepOutputMode.FILES_WITH_MATCHES, null, null, null, 2);

        assertThat(result.files()).hasSizeLessThanOrEqualTo(3); // 2 matches + sentinel
        assertThat(result.truncated()).isTrue();
    }

    // ── (i) sandbox path-traversal rejection ──────────────────────────────────

    @Test
    void sandboxEscape_throwsSecurityException() {
        assertThatThrownBy(() ->
                tool.grep("hello", "../outside", null, null, null, null, null, null, null))
                .isInstanceOf(SecurityException.class);
    }
}
