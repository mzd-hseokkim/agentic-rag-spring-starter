package kr.co.mz.agenticai.core.common.tool;

import java.io.IOException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import kr.co.mz.agenticai.core.common.spi.OutputLimits;
import kr.co.mz.agenticai.core.common.spi.WorkspaceSandbox;

/**
 * Default {@link WorkspaceSandbox}: blocks path-traversal, absolute
 * paths escaping the root, and symlinks pointing outside the root.
 *
 * <p>Resolution strategy: {@code Path.toRealPath(NOFOLLOW_LINKS)} followed by
 * {@code startsWith(root)} check. Any path that fails the check throws
 * {@link SecurityException}.
 *
 * <p>The {@code respectGitignore} flag is a future-phase hook; when {@code true}
 * a minimal glob-based {@link GitignoreMatcher} is consulted. Unsupported
 * patterns (complex negation, character classes, etc.) are silently ignored —
 * this is documented behaviour and can be replaced by a JGit adapter in Phase 2.
 */
public final class DefaultWorkspaceSandbox implements WorkspaceSandbox {

    private final Path root;
    private final OutputLimits outputLimits;
    private final boolean respectGitignore;
    private final GitignoreMatcher gitignoreMatcher;

    public DefaultWorkspaceSandbox(Path root, OutputLimits outputLimits, boolean respectGitignore) {
        this.root = root.toAbsolutePath().normalize();
        this.outputLimits = outputLimits;
        this.respectGitignore = respectGitignore;
        this.gitignoreMatcher = respectGitignore
                ? GitignoreMatcher.fromRootDir(this.root)
                : GitignoreMatcher.NOOP;
    }

    @Override
    public Path resolve(String relative) throws IOException {
        if (relative == null || relative.isBlank()) {
            throw new SecurityException("Path must not be blank");
        }
        Path candidate = Path.of(relative);
        if (candidate.isAbsolute()) {
            throw new SecurityException("Absolute paths are not allowed: " + relative);
        }
        Path resolved = root.resolve(candidate).toRealPath(LinkOption.NOFOLLOW_LINKS);
        if (!resolved.startsWith(root)) {
            throw new SecurityException("Path escapes sandbox root: " + relative);
        }
        if (respectGitignore && gitignoreMatcher.isIgnored(root.relativize(resolved))) {
            throw new SecurityException("Path is gitignored: " + relative);
        }
        return resolved;
    }

    @Override
    public Path root() {
        return root;
    }

    @Override
    public OutputLimits outputLimits() {
        return outputLimits;
    }
}
