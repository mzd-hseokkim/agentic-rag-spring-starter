package kr.co.mz.agenticai.core.common.tool;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Minimal glob-based {@code .gitignore} matcher.
 *
 * <p>Supported patterns: {@code *} (single segment wildcard),
 * {@code **} (multi-segment wildcard), {@code /} prefix (root-anchored),
 * {@code !} prefix (negation). Unsupported patterns (character classes,
 * complex escapes) are silently skipped — this is intentional and
 * documented. A JGit-backed implementation can be substituted in Phase 2.
 */
final class GitignoreMatcher {

    static final GitignoreMatcher NOOP = new GitignoreMatcher(List.of());

    private record Rule(Pattern pattern, boolean negated) {}

    private final List<Rule> rules;

    private GitignoreMatcher(List<Rule> rules) {
        this.rules = rules;
    }

    static GitignoreMatcher fromRootDir(Path root) {
        Path gitignore = root.resolve(".gitignore");
        if (!Files.isRegularFile(gitignore)) {
            return NOOP;
        }
        try {
            List<String> lines = Files.readAllLines(gitignore);
            List<Rule> rules = new ArrayList<>();
            for (String line : lines) {
                String trimmed = line.strip();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
                boolean negated = trimmed.startsWith("!");
                String pattern = negated ? trimmed.substring(1) : trimmed;
                Pattern regex = toRegex(pattern);
                if (regex != null) {
                    rules.add(new Rule(regex, negated));
                }
            }
            return new GitignoreMatcher(rules);
        } catch (IOException e) {
            return NOOP;
        }
    }

    /**
     * Returns {@code true} if {@code relativePath} (relative to sandbox root)
     * is ignored by the loaded rules. The last matching rule wins (gitignore
     * semantics).
     */
    boolean isIgnored(Path relativePath) {
        String posix = relativePath.toString().replace('\\', '/');
        boolean ignored = false;
        for (Rule rule : rules) {
            if (rule.pattern().matcher(posix).matches()) {
                ignored = !rule.negated();
            }
        }
        return ignored;
    }

    /**
     * Converts a gitignore glob pattern to a {@link Pattern}, or {@code null}
     * when the pattern uses unsupported syntax (silently skipped).
     */
    private static Pattern toRegex(String glob) {
        // Reject unsupported: character classes, escape sequences
        if (glob.contains("[") || glob.contains("{") || glob.contains("\\")) {
            return null;
        }
        boolean rootAnchored = glob.startsWith("/");
        String body = rootAnchored ? glob.substring(1) : glob;
        StringBuilder sb = new StringBuilder();
        if (!rootAnchored) {
            // match at any depth
            sb.append("(?:.+/)?");
        }
        sb.append(globBodyToRegex(body));
        // A pattern without trailing / can match both files and directories
        sb.append("(?:/.*)?");
        try {
            return Pattern.compile(sb.toString());
        } catch (Exception e) {
            return null;
        }
    }

    /** Translates the glob body (after root-anchor handling) into a regex fragment. */
    private static String globBodyToRegex(String g) {
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i < g.length()) {
            char c = g.charAt(i);
            if (c == '*') {
                i = appendStar(g, i, sb);
            } else if (c == '?') {
                sb.append("[^/]");
                i++;
            } else {
                sb.append(Pattern.quote(String.valueOf(c)));
                i++;
            }
        }
        return sb.toString();
    }

    /** Appends regex for a {@code *} or {@code **} token; returns the index after it. */
    private static int appendStar(String g, int i, StringBuilder sb) {
        boolean doubleStar = i + 1 < g.length() && g.charAt(i + 1) == '*';
        if (doubleStar) {
            // ** matches any path segments
            sb.append(".*");
            int next = i + 2;
            if (next < g.length() && g.charAt(next) == '/') {
                next++; // skip trailing /
            }
            return next;
        }
        // * matches within one segment
        sb.append("[^/]*");
        return i + 1;
    }
}
