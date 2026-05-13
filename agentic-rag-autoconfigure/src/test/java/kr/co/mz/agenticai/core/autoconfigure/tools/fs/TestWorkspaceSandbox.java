package kr.co.mz.agenticai.core.autoconfigure.tools.fs;

import java.nio.file.Path;

/**
 * Simple test implementation of {@link WorkspaceSandbox} rooted at a given directory.
 *
 * <p>Rejects any resolved path that is not inside the root (path traversal guard).
 */
final class TestWorkspaceSandbox implements WorkspaceSandbox {

    private final Path root;

    TestWorkspaceSandbox(Path root) {
        this.root = root.toAbsolutePath().normalize();
    }

    @Override
    public Path resolve(String rawPath) {
        Path resolved = root.resolve(rawPath).normalize();
        if (!resolved.startsWith(root)) {
            throw new SecurityException(
                    "Path escapes sandbox: " + rawPath + " -> " + resolved);
        }
        return resolved;
    }
}
