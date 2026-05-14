package kr.co.mz.agenticai.core.common.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import kr.co.mz.agenticai.core.common.spi.OutputLimits;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link OutputLimits} record: construction validation and
 * output-truncation helpers (test-local utilities — no production truncation API exists yet).
 */
class OutputLimitsTest {

    // -----------------------------------------------------------------------
    // OutputLimits construction
    // -----------------------------------------------------------------------

    @Test
    void defaultConstantsAreReasonable() {
        OutputLimits limits = OutputLimits.DEFAULT;
        assertThat(limits.maxBytes()).isEqualTo(32 * 1024);
        assertThat(limits.maxLines()).isEqualTo(500);
        assertThat(limits.maxListEntries()).isEqualTo(200);
    }

    @Test
    void rejectsZeroMaxBytes() {
        assertThatThrownBy(() -> new OutputLimits(0, 10, 10))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxBytes");
    }

    @Test
    void rejectsNegativeMaxLines() {
        assertThatThrownBy(() -> new OutputLimits(1024, -1, 10))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxLines");
    }

    @Test
    void rejectsZeroMaxListEntries() {
        assertThatThrownBy(() -> new OutputLimits(1024, 10, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxListEntries");
    }

    // -----------------------------------------------------------------------
    // maxListEntries — directory listing truncation
    // -----------------------------------------------------------------------

    @Test
    void truncatesListAtMaxListEntries() {
        OutputLimits limits = new OutputLimits(65536, 500, 50);
        List<String> entries = generateEntries(80);

        List<String> result = truncateList(entries, limits);

        assertThat(result).hasSize(51); // 50 entries + 1 truncation marker
        assertThat(result.get(50)).contains("truncated");
    }

    @Test
    void doesNotTruncateListBelowLimit() {
        OutputLimits limits = new OutputLimits(65536, 500, 50);
        List<String> entries = generateEntries(30);

        List<String> result = truncateList(entries, limits);

        assertThat(result).hasSize(30);
    }

    @Test
    void truncatesListAtExactBoundary() {
        OutputLimits limits = new OutputLimits(65536, 500, 50);
        List<String> entries = generateEntries(50);

        List<String> result = truncateList(entries, limits);

        assertThat(result).hasSize(50); // exactly at limit — no marker needed
    }

    // -----------------------------------------------------------------------
    // maxBytes — file content truncation
    // -----------------------------------------------------------------------

    @Test
    void truncatesFileContentAtMaxBytes() {
        OutputLimits limits = new OutputLimits(100, 500, 200);
        String content = "A".repeat(200); // 200 bytes, exceeds 100-byte limit

        String result = truncateBytes(content, limits);

        byte[] bytes = result.getBytes(StandardCharsets.UTF_8);
        assertThat(bytes.length).isLessThanOrEqualTo(limits.maxBytes() + 64); // allow truncation line
        assertThat(result).contains("truncated");
    }

    @Test
    void doesNotTruncateFileContentBelowLimit() {
        OutputLimits limits = new OutputLimits(1024, 500, 200);
        String content = "Hello, world!";

        String result = truncateBytes(content, limits);

        assertThat(result).isEqualTo(content);
    }

    // -----------------------------------------------------------------------
    // maxLines — line-level truncation
    // -----------------------------------------------------------------------

    @Test
    void truncatesLinesAtMaxLines() {
        OutputLimits limits = new OutputLimits(65536, 10, 200);
        String content = buildLines(20);

        String result = truncateLines(content, limits);

        long lineCount = result.lines().count();
        assertThat(lineCount).isEqualTo(11); // 10 lines + 1 truncation marker
        assertThat(result).contains("truncated");
    }

    @Test
    void doesNotTruncateLinesWhenBelowLimit() {
        OutputLimits limits = new OutputLimits(65536, 10, 200);
        String content = buildLines(5);

        String result = truncateLines(content, limits);

        assertThat(result.lines().count()).isEqualTo(5);
    }

    // -----------------------------------------------------------------------
    // Test-local truncation helpers
    // (Production tools that apply OutputLimits are outside this module's scope.)
    // -----------------------------------------------------------------------

    private static List<String> truncateList(List<String> entries, OutputLimits limits) {
        if (entries.size() <= limits.maxListEntries()) {
            return new ArrayList<>(entries);
        }
        List<String> result = new ArrayList<>(entries.subList(0, limits.maxListEntries()));
        result.add("... truncated: " + (entries.size() - limits.maxListEntries()) + " more entries");
        return result;
    }

    private static String truncateBytes(String content, OutputLimits limits) {
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        if (bytes.length <= limits.maxBytes()) {
            return content;
        }
        String truncated = new String(bytes, 0, limits.maxBytes(), StandardCharsets.UTF_8);
        return truncated + "\n... truncated: content exceeded " + limits.maxBytes() + " bytes";
    }

    private static String truncateLines(String content, OutputLimits limits) {
        List<String> lines = content.lines().toList();
        if (lines.size() <= limits.maxLines()) {
            return content;
        }
        List<String> kept = lines.subList(0, limits.maxLines());
        return String.join("\n", kept) + "\n... truncated: "
                + (lines.size() - limits.maxLines()) + " more lines";
    }

    private static List<String> generateEntries(int count) {
        List<String> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            list.add("entry-" + i);
        }
        return list;
    }

    private static String buildLines(int count) {
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= count; i++) {
            sb.append("line ").append(i).append('\n');
        }
        return sb.toString();
    }
}
