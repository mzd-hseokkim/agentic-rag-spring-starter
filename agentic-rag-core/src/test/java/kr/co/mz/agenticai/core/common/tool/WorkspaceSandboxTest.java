package kr.co.mz.agenticai.core.common.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import kr.co.mz.agenticai.core.common.spi.OutputLimits;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/**
 * Sandbox security unit tests for {@link DefaultWorkspaceSandbox}.
 *
 * <p>Covers symlink escape and Windows junction escape in addition to the
 * basic path-traversal / absolute-path / blank / null cases already in
 * {@link DefaultWorkspaceSandboxTest}.
 */
class WorkspaceSandboxTest {

    @TempDir
    Path root;

    private DefaultWorkspaceSandbox sandbox() {
        return new DefaultWorkspaceSandbox(root, OutputLimits.DEFAULT, false);
    }

    // --- symlink escape ---

    @Test
    void rejectsSymlinkPointingOutsideRoot() throws IOException {
        Path outside = Files.createTempDirectory(root.getParent(), "outside");
        Path secret = Files.createFile(outside.resolve("secret.txt"));
        Path link = root.resolve("link.txt");
        try {
            try {
                Files.createSymbolicLink(link, secret);
            } catch (UnsupportedOperationException | IOException e) {
                // Symlink creation not supported or permission denied on this environment
                assumeTrue(false, "Symlink creation not available: " + e.getMessage());
            }
            DefaultWorkspaceSandbox sb = sandbox();
            assertThatThrownBy(() -> sb.resolve("link.txt"))
                    .isInstanceOf(SecurityException.class);
        } finally {
            Files.deleteIfExists(link);
            Files.deleteIfExists(secret);
            Files.deleteIfExists(outside);
        }
    }

    @Test
    void allowsSymlinkInsideRoot() throws IOException {
        Path target = Files.createFile(root.resolve("target.txt"));
        Path link = root.resolve("alias.txt");
        try {
            try {
                Files.createSymbolicLink(link, target);
            } catch (UnsupportedOperationException | IOException e) {
                assumeTrue(false, "Symlink creation not available: " + e.getMessage());
            }
            // toRealPath(NOFOLLOW_LINKS) resolves the link path itself, not the target.
            // The link path is inside root, so it must be allowed.
            Path resolved = sandbox().resolve("alias.txt");
            assertThat(resolved).startsWith(root);
        } finally {
            Files.deleteIfExists(link);
            Files.deleteIfExists(target);
        }
    }

    // --- Windows junction (NTFS directory junction) escape ---

    @Test
    @EnabledOnOs(OS.WINDOWS)
    void rejectsJunctionPointingOutsideRoot() throws IOException, InterruptedException {
        Path outside = Files.createTempDirectory(root.getParent(), "outside-junc");
        Path junction = root.resolve("junc");
        boolean created = canCreateJunction(junction, outside);
        assumeTrue(created, "Junction creation requires elevated privileges — skipping.");
        try {
            DefaultWorkspaceSandbox sb = sandbox();
            assertThatThrownBy(() -> sb.resolve("junc"))
                    .isInstanceOf(SecurityException.class);
        } finally {
            deleteJunction(junction);
            Files.deleteIfExists(outside);
        }
    }

    // --- .gitignore matching ---

    @Test
    void rejectsGitignoreMatchedPathWhenFlagEnabled() throws IOException {
        Files.writeString(root.resolve(".gitignore"), "*.secret\n");
        Files.createFile(root.resolve("private.secret"));
        DefaultWorkspaceSandbox withGitignore =
                new DefaultWorkspaceSandbox(root, OutputLimits.DEFAULT, true);
        assertThatThrownBy(() -> withGitignore.resolve("private.secret"))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("gitignored");
    }

    @Test
    void allowsNonIgnoredPathWhenFlagEnabled() throws IOException {
        Files.writeString(root.resolve(".gitignore"), "*.secret\n");
        Path file = Files.createFile(root.resolve("readme.txt"));
        DefaultWorkspaceSandbox withGitignore =
                new DefaultWorkspaceSandbox(root, OutputLimits.DEFAULT, true);
        Path resolved = withGitignore.resolve("readme.txt");
        assertThat(resolved).isEqualTo(file.toRealPath(java.nio.file.LinkOption.NOFOLLOW_LINKS));
    }

    @Test
    void negatedGitignoreRuleAllowsFile() throws IOException {
        Files.writeString(root.resolve(".gitignore"), "*.log\n!important.log\n");
        Path logFile = Files.createFile(root.resolve("important.log"));
        DefaultWorkspaceSandbox withGitignore =
                new DefaultWorkspaceSandbox(root, OutputLimits.DEFAULT, true);
        Path resolved = withGitignore.resolve("important.log");
        assertThat(resolved).isEqualTo(logFile.toRealPath(java.nio.file.LinkOption.NOFOLLOW_LINKS));
    }

    // --- helpers (Windows junction) ---

    private static boolean canCreateJunction(Path junction, Path target) {
        try {
            Process p = Runtime.getRuntime().exec(new String[]{
                "cmd", "/c", "mklink", "/J",
                junction.toAbsolutePath().toString(),
                target.toAbsolutePath().toString()
            });
            return p.waitFor() == 0 && Files.exists(junction);
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private static void deleteJunction(Path junction) {
        try {
            Runtime.getRuntime().exec(new String[]{"cmd", "/c", "rmdir",
                junction.toAbsolutePath().toString()}).waitFor();
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
