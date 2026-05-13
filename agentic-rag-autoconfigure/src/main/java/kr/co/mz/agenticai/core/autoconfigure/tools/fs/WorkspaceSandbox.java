package kr.co.mz.agenticai.core.autoconfigure.tools.fs;

import java.nio.file.Path;

/**
 * Resolves and validates user-supplied path strings against the configured workspace root.
 *
 * <p>Implementations must reject any path that escapes the sandbox boundary (e.g. {@code ../}).
 *
 * <p>NOTE: MAE-377 will provide the canonical implementation and relocate this interface to
 * {@code agentic-rag-core}. Until then, this placeholder lives in the {@code tools.fs} package.
 */
public interface WorkspaceSandbox {

    /**
     * Resolves {@code rawPath} against the workspace root and returns the absolute,
     * normalized {@link Path}.
     *
     * @param rawPath user-supplied path string (absolute or relative)
     * @return resolved path guaranteed to be inside the sandbox
     * @throws SecurityException if the resolved path escapes the sandbox
     */
    Path resolve(String rawPath);
}
