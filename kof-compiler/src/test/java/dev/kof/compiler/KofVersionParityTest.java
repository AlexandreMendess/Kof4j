package dev.kof.compiler;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Packaging guard for the #814 family (known-bugs §655, audit `VER-01`): the
 * 0.6.0 cut bumped VERSION+pom but skipped version.properties, so the published
 * CLI reported 0.5.0-beta while the distribution was 0.6.0. The source of truth
 * is the VERSION file at the repository root (scripts/bump-version.sh header);
 * this test pins that the packaged properties and the KofVersion runtime face
 * the CLI prints can never ship diverged from it again.
 */
class KofVersionParityTest {

    private static Path findRepoRoot() throws IOException {
        Path dir = Path.of("").toAbsolutePath();
        while (dir != null && !Files.exists(dir.resolve("VERSION"))) {
            dir = dir.getParent();
        }
        if (dir == null) {
            throw new IllegalStateException("repo root with the VERSION file not found from "
                    + Path.of("").toAbsolutePath());
        }
        return dir;
    }

    private static Properties readPackagedProps(Path root) throws IOException {
        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(
                root.resolve("kof-compiler/src/main/resources/dev/kof/version.properties"))) {
            props.load(in);
        }
        return props;
    }

    @Test
    void packagedVersionPropertiesAgreesWithTheRepoVersion() throws IOException {
        Path root = findRepoRoot();
        String version = Files.readString(root.resolve("VERSION")).trim();
        Properties props = readPackagedProps(root);
        assertEquals(version, props.getProperty("kof.version"), "kof.version diverges from VERSION");
        assertEquals(version, props.getProperty("kof.compiler.version"), "kof.compiler.version diverges from VERSION");
        assertEquals(version, props.getProperty("kof.runtime.version"), "kof.runtime.version diverges from VERSION");
        assertEquals(version, props.getProperty("kof.stdlib.version"), "kof.stdlib.version diverges from VERSION");
    }

    @Test
    void kofVersionRuntimeFaceReportsTheRepoVersion() throws IOException {
        String version = Files.readString(findRepoRoot().resolve("VERSION")).trim();
        assertEquals(version, KofVersion.version(), "KofVersion.version() (the `kof version` face) diverges from VERSION");
        assertEquals(version, KofVersion.compiler(), "KofVersion.compiler() diverges from VERSION");
        assertEquals(version, KofVersion.runtime(), "KofVersion.runtime() diverges from VERSION");
        assertEquals(version, KofVersion.stdlib(), "KofVersion.stdlib() diverges from VERSION");
    }
}
