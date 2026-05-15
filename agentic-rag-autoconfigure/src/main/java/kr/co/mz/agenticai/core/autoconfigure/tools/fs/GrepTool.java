package kr.co.mz.agenticai.core.autoconfigure.tools.fs;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Stream;
import kr.co.mz.agenticai.core.autoconfigure.tools.fs.dto.GrepResult;
import kr.co.mz.agenticai.core.autoconfigure.tools.fs.dto.MatchLine;
import kr.co.mz.agenticai.core.autoconfigure.tools.fs.enums.GrepOutputMode;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

/**
 * JVM-only grep tool exposed to the LLM via Spring AI {@code @Tool}.
 *
 * <p>Supports three output modes ({@code FILES_WITH_MATCHES}, {@code CONTENT},
 * {@code COUNT}), optional surrounding context lines, multiline matching, and
 * case-insensitive search. All paths are sandboxed via {@link WorkspaceSandbox}.
 *
 * <p>This class is a plain Java object — not a Spring {@code @Component}. It must be
 * registered via {@code MethodToolCallbackProvider.builder().toolObjects(...)} in the
 * autoconfigure module (MAE-380 responsibility).
 */
public final class GrepTool {

    private static final String KIND_MATCH  = "MATCH";
    private static final String KIND_BEFORE = "BEFORE";
    private static final String KIND_AFTER  = "AFTER";

    private final WorkspaceSandbox sandbox;
    private final OutputLimits limits;

    public GrepTool(WorkspaceSandbox sandbox, OutputLimits limits) {
        this.sandbox = sandbox;
        this.limits = limits;
    }

    /**
     * Searches files inside the workspace for lines matching a regular expression.
     *
     * @param pattern         Java regex pattern to search for
     * @param path            directory or file to search (null = workspace root)
     * @param glob            optional glob filter applied to file names (e.g. {@code *.java})
     * @param type            optional file type filter; reserved for future expansion (only {@code f} is supported today)
     * @param outputMode      result shape: {@code FILES_WITH_MATCHES} | {@code CONTENT} | {@code COUNT}
     * @param contextLines    number of surrounding lines to include on each side (CONTENT mode only)
     * @param multiline       if {@code true}, {@code .} matches newlines and pattern can span lines
     * @param caseInsensitive if {@code true}, matching ignores case
     * @param headLimit       maximum results before truncation (null = {@link OutputLimits#maxResults()})
     * @return a {@link GrepResult} containing the matched data
     * @throws IllegalArgumentException if {@code pattern} is not a valid Java regex
     */
    // Parameters mirror the LLM tool schema; type is retained for forward compatibility.
    @SuppressWarnings({"java:S107", "java:S1172"})
    @Tool(name = "fs_grep",
            description = "Search files inside the workspace for lines matching a regex. "
                    + "pattern: Java regex. "
                    + "path: directory or file to search (null = workspace root). "
                    + "glob: glob filter on file names (e.g. '*.java'). "
                    + "outputMode: FILES_WITH_MATCHES | CONTENT | COUNT. "
                    + "contextLines: surrounding lines to include (CONTENT mode, null = 0). "
                    + "multiline: true to allow pattern to span multiple lines. "
                    + "caseInsensitive: true to ignore case. "
                    + "headLimit: max results (null = 50).")
    public GrepResult grep(
            @ToolParam(description = "Java regex pattern to search for") String pattern,
            @ToolParam(description = "directory or file to search (null = workspace root)", required = false) String path,
            @ToolParam(description = "glob filter on file names, e.g. '*.java' (null = all files)", required = false) String glob,
            @ToolParam(description = "file type filter; 'f' = regular files only (reserved, null = all)", required = false) String type,
            @ToolParam(description = "output mode: FILES_WITH_MATCHES | CONTENT | COUNT (null = FILES_WITH_MATCHES)", required = false) GrepOutputMode outputMode,
            @ToolParam(description = "context lines before/after each match in CONTENT mode (null = 0)", required = false) Integer contextLines,
            @ToolParam(description = "true to allow pattern to span newlines (default false)", required = false) Boolean multiline,
            @ToolParam(description = "true to ignore case (default false)", required = false) Boolean caseInsensitive,
            @ToolParam(description = "max results before truncation (null = 50)", required = false) Integer headLimit) {

        GrepOutputMode mode    = outputMode != null ? outputMode : GrepOutputMode.FILES_WITH_MATCHES;
        int            ctx     = contextLines != null ? Math.max(0, contextLines) : 0;
        boolean        ml      = Boolean.TRUE.equals(multiline);
        boolean        ci      = Boolean.TRUE.equals(caseInsensitive);
        int            limit   = headLimit != null ? headLimit : limits.maxResults();

        Pattern compiled = compilePattern(pattern, ml, ci);

        Path base = resolveBase(path);
        PathMatcher globMatcher = glob != null
                ? FileSystems.getDefault().getPathMatcher("glob:" + glob)
                : null;

        List<Path> files = collectFiles(base, globMatcher);

        return switch (mode) {
            case FILES_WITH_MATCHES -> filesWithMatches(files, compiled, base, ml, limit);
            case CONTENT            -> content(files, compiled, base, ml, ctx, limit);
            case COUNT              -> count(files, compiled, ml);
        };
    }

    // ── pattern compilation ───────────────────────────────────────────────────

    private static Pattern compilePattern(String pattern, boolean multiline, boolean caseInsensitive) {
        int flags = 0;
        if (multiline)       flags |= Pattern.DOTALL | Pattern.MULTILINE;
        if (caseInsensitive) flags |= Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
        try {
            return Pattern.compile(pattern, flags);
        } catch (PatternSyntaxException e) {
            throw new IllegalArgumentException(
                    "invalid regex: " + e.getDescription() + " at index " + e.getIndex(), e);
        }
    }

    // ── path helpers ──────────────────────────────────────────────────────────

    private Path resolveBase(String rawPath) {
        String normalized = rawPath != null
                ? Normalizer.normalize(rawPath, Normalizer.Form.NFC)
                : ".";
        return sandbox.resolve(normalized);
    }

    private List<Path> collectFiles(Path base, PathMatcher globMatcher) {
        List<Path> result = new ArrayList<>();
        if (Files.isRegularFile(base)) {
            result.add(base);
            return result;
        }
        try (Stream<Path> walk = Files.walk(base)) {
            walk.filter(Files::isRegularFile)
                    .filter(p -> globMatcher == null || globMatcher.matches(p.getFileName()))
                    .forEach(result::add);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return result;
    }

    // ── FILES_WITH_MATCHES ────────────────────────────────────────────────────

    private GrepResult filesWithMatches(List<Path> files, Pattern pattern, Path base,
                                        boolean multiline, int limit) {
        List<String> matched = new ArrayList<>();
        for (Path file : files) {
            if (matched.size() >= limit) {
                // collect to get total, then truncate
                break;
            }
            if (fileContainsMatch(file, pattern, multiline)) {
                matched.add(relativize(base, file));
            }
        }

        // check if there are more beyond the limit
        boolean truncated = false;
        if (matched.size() >= limit) {
            // scan remaining to detect truncation
            int scanned = matched.size();
            for (int i = scanned; i < files.size(); i++) {
                if (fileContainsMatch(files.get(i), pattern, multiline)) {
                    truncated = true;
                    matched.add("(truncated, more files match)");
                    break;
                }
            }
        }
        return GrepResult.ofFiles(List.copyOf(matched), truncated);
    }

    private boolean fileContainsMatch(Path file, Pattern pattern, boolean multiline) {
        try {
            if (multiline) {
                String content = readStringSafe(file);
                return pattern.matcher(content).find();
            }
            try (Stream<String> lines = Files.lines(file, StandardCharsets.UTF_8)) {
                return lines.anyMatch(line -> pattern.matcher(line).find());
            }
        } catch (IOException e) {
            return false; // unreadable files are silently skipped
        }
    }

    // ── CONTENT ───────────────────────────────────────────────────────────────

    private GrepResult content(List<Path> files, Pattern pattern, Path base,
                               boolean multiline, int ctx, int limit) {
        List<MatchLine> result = new ArrayList<>();
        boolean truncated = false;

        for (Path file : files) {
            String relPath = relativize(base, file);
            for (MatchLine ml : collectMatchLines(file, relPath, pattern, multiline, ctx)) {
                if (result.size() >= limit) {
                    truncated = true;
                    break;
                }
                result.add(ml);
            }
            if (truncated) {
                break;
            }
        }

        return GrepResult.ofContent(List.copyOf(result), truncated);
    }

    /**
     * Reads one file and returns its match lines with surrounding context.
     * Returns an empty list when the file is unreadable or too large.
     */
    private List<MatchLine> collectMatchLines(Path file, String relPath, Pattern pattern,
                                              boolean multiline, int ctx) {
        try {
            String[] allLines;
            boolean[] matchMask;
            if (multiline) {
                String text = readStringSafe(file);
                allLines = text.split("\n", -1);
                matchMask = buildMatchMask(pattern, allLines, text);
            } else {
                List<String> lineList;
                try (Stream<String> s = Files.lines(file, StandardCharsets.UTF_8)) {
                    lineList = s.toList();
                }
                allLines = lineList.toArray(String[]::new);
                matchMask = new boolean[allLines.length];
                for (int i = 0; i < allLines.length; i++) {
                    matchMask[i] = pattern.matcher(allLines[i]).find();
                }
            }
            return applyContext(relPath, allLines, matchMask, ctx);
        } catch (IOException e) {
            return List.of();
        }
    }

    /** Builds a match-mask for multiline mode by mapping regex match positions to lines. */
    private static boolean[] buildMatchMask(Pattern pattern, String[] lines, String text) {
        boolean[] mask = new boolean[lines.length];
        Matcher m = pattern.matcher(text);
        while (m.find()) {
            // find which line the match start falls on
            int offset = 0;
            for (int i = 0; i < lines.length; i++) {
                int end = offset + lines[i].length();
                if (m.start() >= offset && m.start() <= end) {
                    mask[i] = true;
                    break;
                }
                offset = end + 1; // +1 for the '\n' consumed by split
            }
        }
        return mask;
    }

    /** Expands match lines with surrounding context, deduplicating by line number. */
    private static List<MatchLine> applyContext(String path, String[] lines, boolean[] matchMask, int ctx) {
        List<MatchLine> result = new ArrayList<>();
        boolean[] included = new boolean[lines.length];
        String[] kinds     = new String[lines.length];

        for (int i = 0; i < lines.length; i++) {
            if (!matchMask[i]) {
                continue;
            }
            int from = Math.max(0, i - ctx);
            int to   = Math.min(lines.length - 1, i + ctx);
            for (int j = from; j <= to; j++) {
                markContextLine(j, i, included, kinds);
            }
        }

        for (int i = 0; i < lines.length; i++) {
            if (included[i]) {
                result.add(new MatchLine(path, i + 1, lines[i], kinds[i]));
            }
        }
        return result;
    }

    /** Marks line {@code j} as included and assigns its kind relative to {@code matchLine}. */
    private static void markContextLine(int j, int matchLine, boolean[] included, String[] kinds) {
        if (!included[j]) {
            included[j] = true;
            kinds[j] = kindOf(j, matchLine);
        } else if (j == matchLine) {
            // a line previously included as BEFORE/AFTER is also a match
            kinds[j] = KIND_MATCH;
        }
    }

    private static String kindOf(int line, int matchLine) {
        if (line < matchLine) {
            return KIND_BEFORE;
        }
        if (line > matchLine) {
            return KIND_AFTER;
        }
        return KIND_MATCH;
    }

    // ── COUNT ─────────────────────────────────────────────────────────────────

    private GrepResult count(List<Path> files, Pattern pattern, boolean multiline) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (Path file : files) {
            long n = countMatches(file, pattern, multiline);
            if (n > 0) {
                counts.put(file.toString(), n);
            }
        }
        return GrepResult.ofCounts(Map.copyOf(counts));
    }

    private long countMatches(Path file, Pattern pattern, boolean multiline) {
        try {
            if (multiline) {
                String text = readStringSafe(file);
                Matcher m = pattern.matcher(text);
                long count = 0;
                while (m.find()) count++;
                return count;
            }
            try (Stream<String> lines = Files.lines(file, StandardCharsets.UTF_8)) {
                return lines.filter(line -> pattern.matcher(line).find()).count();
            }
        } catch (IOException e) {
            return 0;
        }
    }

    // ── utilities ─────────────────────────────────────────────────────────────

    private String relativize(Path base, Path file) {
        if (Files.isRegularFile(base)) {
            return base.getParent() != null
                    ? base.getParent().relativize(file).toString()
                    : file.toString();
        }
        return base.relativize(file).toString();
    }

    private String readStringSafe(Path file) throws IOException {
        long size = Files.size(file);
        // guard: skip files larger than 4 MB to avoid OOM in multiline mode
        if (size > 4L * 1024 * 1024) {
            throw new IOException("file too large for multiline mode: " + size + " bytes");
        }
        return Files.readString(file, StandardCharsets.UTF_8);
    }
}
