package kr.co.mz.agenticai.core.autoconfigure.tools.fs;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import kr.co.mz.agenticai.core.autoconfigure.tools.fs.dto.DirEntry;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

/**
 * JVM-only filesystem tools exposed to the LLM via Spring AI {@code @Tool} annotations.
 *
 * <p>This class is a plain Java object — not a Spring {@code @Component}. It must be
 * registered via {@code MethodToolCallbackProvider.builder().toolObjects(...)} in the
 * autoconfigure module (MAE-380 responsibility).
 *
 * <p>Every path argument passes through {@link WorkspaceSandbox#resolve(String)} before use.
 * Output sizes are bounded by {@link OutputLimits}.
 */
public final class FileSystemTools {

    private final WorkspaceSandbox sandbox;
    private final OutputLimits limits;

    public FileSystemTools(WorkspaceSandbox sandbox, OutputLimits limits) {
        this.sandbox = sandbox;
        this.limits = limits;
    }

    /**
     * Finds files matching a glob pattern, sorted by last-modified time descending.
     *
     * @param pattern   glob pattern (e.g. {@code **}{@code /*.java})
     * @param basePath  optional base directory to search from; defaults to workspace root
     * @param headLimit maximum number of results; defaults to {@link OutputLimits#maxResults()}
     * @return list of matching file paths (relative to workspace root)
     */
    @Tool(name = "fs_glob",
            description = "Find files matching a glob pattern inside the workspace, "
                    + "sorted by last-modified time descending. "
                    + "pattern: glob expression (e.g. '**/*.java'). "
                    + "basePath: optional subdirectory (null = workspace root). "
                    + "headLimit: max results (null = default 50).")
    public List<String> glob(
            @ToolParam(description = "glob pattern, e.g. '**/*.java'") String pattern,
            @ToolParam(description = "base directory to search from (null = workspace root)", required = false) String basePath,
            @ToolParam(description = "maximum number of results (null = 50)", required = false) Integer headLimit) {

        Path base = basePath != null ? sandbox.resolve(basePath) : sandbox.resolve(".");
        int limit = headLimit != null ? headLimit : limits.maxResults();
        PathMatcher matcher = FileSystems.getDefault().getPathMatcher("glob:" + pattern);

        List<Path> matched = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(base)) {
            walk.filter(p -> !Files.isDirectory(p))
                    .filter(p -> matcher.matches(base.relativize(p)))
                    .forEach(matched::add);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        matched.sort(Comparator.comparingLong(p -> {
            try {
                return -Files.getLastModifiedTime(p).toMillis();
            } catch (IOException e) {
                return 0L;
            }
        }));

        List<String> results = new ArrayList<>();
        int count = 0;
        for (Path p : matched) {
            if (count >= limit) {
                results.add(String.format("(truncated, %d more)", matched.size() - limit));
                break;
            }
            results.add(base.relativize(p).toString());
            count++;
        }
        return List.copyOf(results);
    }

    /**
     * Lists the contents of a directory.
     *
     * @param dirPath   directory to list
     * @param recursive whether to traverse subdirectories
     * @return list of {@link DirEntry} describing each entry
     */
    @Tool(name = "fs_listDir",
            description = "List the contents of a directory inside the workspace. "
                    + "dirPath: directory to inspect. "
                    + "recursive: if true, recurse into subdirectories.")
    public List<DirEntry> listDir(
            @ToolParam(description = "directory path to list") String dirPath,
            @ToolParam(description = "true to recurse into subdirectories (default false)", required = false) Boolean recursive) {

        Path dir = sandbox.resolve(dirPath);
        boolean recurse = Boolean.TRUE.equals(recursive);

        List<DirEntry> entries = new ArrayList<>();
        try (Stream<Path> stream = recurse ? Files.walk(dir) : Files.list(dir)) {
            stream.filter(p -> !p.equals(dir)).forEach(p -> {
                String name = dir.relativize(p).toString();
                String type = entryType(p);
                long size = -1L;
                long mtime = -1L;
                try {
                    BasicFileAttributes attrs = Files.readAttributes(p, BasicFileAttributes.class);
                    size = attrs.isRegularFile() ? attrs.size() : -1L;
                    mtime = attrs.lastModifiedTime().toMillis();
                } catch (IOException ignored) {
                    // best-effort; leave defaults
                }
                entries.add(new DirEntry(name, type, size, mtime));
            });
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        // apply output limit with a sentinel DirEntry
        DirEntry sentinel = new DirEntry(
                String.format("(truncated, %d more)", entries.size() - limits.maxResults()),
                "TRUNCATED", -1, -1);
        return limits.apply(entries, sentinel);
    }

    /**
     * Reads a file, returning lines with 1-based line-number prefixes.
     *
     * @param filePath path to the file
     * @param offset   0-based line offset to start reading from (null = 0)
     * @param limit    maximum number of lines to return (null = {@link OutputLimits#maxLines()})
     * @return file content as a single string with {@code "NNNNNN\tline"} prefixes
     */
    @Tool(name = "fs_readFile",
            description = "Read a file inside the workspace with line-number prefixes. "
                    + "filePath: path to the file. "
                    + "offset: 0-based line number to start from (null = 0). "
                    + "limit: max lines to return (null = 2000).")
    public String readFile(
            @ToolParam(description = "file path to read") String filePath,
            @ToolParam(description = "0-based start line (null = 0)", required = false) Integer offset,
            @ToolParam(description = "max lines to return (null = 2000)", required = false) Integer limit) {

        Path file = sandbox.resolve(filePath);
        int startLine = offset != null ? offset : 0;
        int maxLines = limit != null ? limit : limits.maxLines();

        List<String> lines;
        try (Stream<String> lineStream = Files.lines(file)) {
            lines = lineStream.skip(startLine).limit(maxLines + 1L).toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        boolean truncated = lines.size() > maxLines;
        List<String> visible = truncated ? lines.subList(0, maxLines) : lines;

        StringBuilder sb = new StringBuilder();
        int lineNo = startLine + 1;
        for (String line : visible) {
            sb.append(String.format("%6d\t%s%n", lineNo++, line));
        }
        if (truncated) {
            sb.append("(truncated, more lines available — increase limit or use offset)");
        }
        return sb.toString();
    }

    private static String entryType(Path p) {
        if (Files.isSymbolicLink(p)) return "SYMLINK";
        if (Files.isDirectory(p))   return "DIR";
        return "FILE";
    }
}
