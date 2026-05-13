package kr.co.mz.agenticai.core.common.spi;

import java.nio.file.Path;

/**
 * Sandboxes file-system tool operations to a configured root directory.
 *
 * <p>The default implementation ({@code DefaultWorkspaceSandbox}) blocks
 * path-traversal ({@code ../}), absolute paths escaping the root, and symlinks
 * pointing outside the root. Register a bean of this type to override the
 * default behaviour.
 */
public interface WorkspaceSandbox {

    /**
     * Resolves {@code relative} against the sandbox root and verifies it stays
     * within the root.
     *
     * @param relative a caller-supplied path; must not escape the root
     * @return the fully resolved, real {@link Path} within the sandbox
     * @throws SecurityException if the resolved path escapes the sandbox root
     *         or involves a forbidden symlink
     * @throws java.io.IOException if the path cannot be resolved on the filesystem
     */
    Path resolve(String relative) throws java.io.IOException;

    /** Returns the configured sandbox root directory. */
    Path root();

    /** Returns the output limits applied to tool responses. */
    OutputLimits outputLimits();
}
