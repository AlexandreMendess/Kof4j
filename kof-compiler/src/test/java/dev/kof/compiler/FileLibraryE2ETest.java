package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end coverage for the pure-Kof streaming library in {@code libs/file}
 * (kof.file slice 1, {@code D-KOF-FILE-GO}). Proves incremental chunk reading
 * and constant-memory chunked copy on the JVM, over the existing
 * {@code File.readRange} primitive — no new syntax, no compiler change.
 */
class FileLibraryE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    @TempDir Path tmp;

    @Test
    void streamsInChunksAndCopiesWithoutLoadingTheWholeFile() throws Exception {
        Path src = tmp.resolve("source.txt");
        Path dst = tmp.resolve("dest.txt");
        String output = runKof("""
            import file.FileStream

            main() {
                File("%s").writeText("0123456789")

                var stream = FileStream("%s", 4)
                var chunks = 0
                var total = 0
                var chunk = stream.readChunk()
                while (chunk != null) {
                    chunks = chunks + 1
                    total = total + chunk.length
                    chunk = stream.readChunk()
                }
                println(chunks)
                println(total)
                println(stream.done())
                println(stream.position())

                var copied = copyStream("%s", "%s", 3)
                println(copied)
                println(File("%s").readText())
                println(File("%s").readText())
            }
            """.formatted(src.toString().replace('\\', '/'),
                src.toString().replace('\\', '/'),
                src.toString().replace('\\', '/'),
                dst.toString().replace('\\', '/'),
                src.toString().replace('\\', '/'),
                dst.toString().replace('\\', '/')));
        assertEquals("3\n10\ntrue\n10\n10\n0123456789\n0123456789", output,
                () -> "unexpected streaming output: " + output);
        assertEquals(Files.readString(src), Files.readString(dst),
                "chunked copy must be byte-identical");
    }

    @Test
    void emptyFileEndsImmediately() throws Exception {
        Path src = tmp.resolve("empty.txt");
        String output = runKof("""
            import file.FileStream

            main() {
                File("%s").writeText("")
                var stream = FileStream("%s", 16)
                var chunk = stream.readChunk()
                println(chunk == null)
            }
            """.formatted(src.toString().replace('\\', '/'),
                src.toString().replace('\\', '/')));
        assertEquals("true", output, "empty file must yield null on the first read");
    }

    private String runKof(String code) throws Exception {
        Path installRoot = tmp.resolve("kof-install");
        copyLibrary(installRoot.resolve("lib/kof-libs"));
        Path source = tmp.resolve("Main.kf");
        Files.writeString(source, code);
        Path out = Files.createTempDirectory(tmp, "file-out-");
        String previousInstallDir = System.getProperty("kof.install.dir");
        CompilationResult result;
        System.setProperty("kof.install.dir", installRoot.toString());
        try {
            result = driver.compile(source, out, Target.JVM);
        } finally {
            if (previousInstallDir == null) System.clearProperty("kof.install.dir");
            else System.setProperty("kof.install.dir", previousInstallDir);
        }
        assertTrue(result.success(), () -> "compile: " + result.diagnostics().getDiagnostics());

        var stdout = new java.io.ByteArrayOutputStream();
        var previousOut = System.out;
        try {
            System.setOut(new java.io.PrintStream(stdout, true, java.nio.charset.StandardCharsets.UTF_8));
            try (var loader = new URLClassLoader(
                    new java.net.URL[]{out.toUri().toURL()}, getClass().getClassLoader())) {
                Class.forName("Default.Main", true, loader)
                        .getMethod("main", String[].class)
                        .invoke(null, (Object) new String[0]);
            }
        } finally {
            System.setOut(previousOut);
        }
        return stdout.toString(java.nio.charset.StandardCharsets.UTF_8).strip();
    }

    private static void copyLibrary(Path destinationRoot) throws Exception {
        Path sourceRoot = findLibraryRoot();
        try (var files = Files.walk(sourceRoot)) {
            for (Path source : files.filter(Files::isRegularFile).toList()) {
                Path destination = destinationRoot.resolve("file")
                        .resolve(sourceRoot.relativize(source));
                Files.createDirectories(destination.getParent());
                Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    private static Path findLibraryRoot() {
        Path workingDirectory = Path.of("").toAbsolutePath().normalize();
        Path fromRepository = workingDirectory.resolve("libs/file");
        if (Files.isRegularFile(fromRepository.resolve("FileStream.kf"))) return fromRepository;

        Path fromModule = workingDirectory.resolve("../libs/file").normalize();
        if (Files.isRegularFile(fromModule.resolve("FileStream.kf"))) return fromModule;

        throw new IllegalStateException("libs/file not found from " + workingDirectory);
    }
}
