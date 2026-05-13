package kr.co.mz.agenticai.core.common.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import kr.co.mz.agenticai.core.common.spi.OutputLimits;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DefaultWorkspaceSandboxTest {

    @TempDir
    Path root;

    private DefaultWorkspaceSandbox sandbox(boolean respectGitignore) {
        return new DefaultWorkspaceSandbox(root, OutputLimits.DEFAULT, respectGitignore);
    }

    // Case 1: path-traversal / absolute path rejection

    @Test
    void rejectsTraversalPath() throws IOException {
        // Create a sibling directory with a file so toRealPath can succeed,
        // then startsWith(root) detects the escape.
        Path parent = root.getParent();
        Path sibling = Files.createTempDirectory(parent, "sibling");
        try {
            Files.createFile(sibling.resolve("secret.txt"));
            String siblingName = sibling.getFileName().toString();
            assertThatThrownBy(() -> sandbox(false).resolve("../" + siblingName + "/secret.txt"))
                    .isInstanceOf(SecurityException.class)
                    .hasMessageContaining("escapes sandbox root");
        } finally {
            // cleanup
            Files.walk(sibling)
                    .sorted(java.util.Comparator.reverseOrder())
                    .forEach(p -> p.toFile().delete());
        }
    }

    @Test
    void rejectsAbsolutePath() {
        String absPath = root.toAbsolutePath().toString();
        assertThatThrownBy(() -> sandbox(false).resolve(absPath))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    void rejectsBlankPath() {
        assertThatThrownBy(() -> sandbox(false).resolve("   "))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    void rejectsNullPath() {
        assertThatThrownBy(() -> sandbox(false).resolve(null))
                .isInstanceOf(SecurityException.class);
    }

    // Case 2: normal resolve inside root

    @Test
    void resolvesFileInsideRoot() throws IOException {
        Path file = Files.createFile(root.resolve("hello.txt"));
        Path resolved = sandbox(false).resolve("hello.txt");
        assertThat(resolved).isEqualTo(file.toRealPath());
    }

    @Test
    void resolvesNestedFileInsideRoot() throws IOException {
        Path dir = Files.createDirectory(root.resolve("sub"));
        Path file = Files.createFile(dir.resolve("data.txt"));
        Path resolved = sandbox(false).resolve("sub/data.txt");
        assertThat(resolved).isEqualTo(file.toRealPath());
    }

    // Case 3: respectGitignore=false — matcher inactive

    @Test
    void gitignoreNotAppliedWhenFlagFalse() throws IOException {
        Files.writeString(root.resolve(".gitignore"), "*.log\n");
        Path logFile = Files.createFile(root.resolve("app.log"));
        // should NOT throw even though .gitignore says *.log
        Path resolved = sandbox(false).resolve("app.log");
        assertThat(resolved).isEqualTo(logFile.toRealPath());
    }

    @Test
    void gitignoreAppliedWhenFlagTrue() throws IOException {
        Files.writeString(root.resolve(".gitignore"), "*.log\n");
        Files.createFile(root.resolve("app.log"));
        assertThatThrownBy(() -> sandbox(true).resolve("app.log"))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("gitignored");
    }

    // Case 4: root() and outputLimits() accessors

    @Test
    void rootReturnsConfiguredRoot() {
        assertThat(sandbox(false).root()).isEqualTo(root.toAbsolutePath().normalize());
    }

    @Test
    void outputLimitsReturnsDefault() {
        assertThat(sandbox(false).outputLimits()).isEqualTo(OutputLimits.DEFAULT);
    }
}
