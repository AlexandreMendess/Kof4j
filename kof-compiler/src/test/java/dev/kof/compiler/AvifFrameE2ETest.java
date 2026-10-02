package dev.kof.compiler;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

import static dev.kof.compiler.AvifFrameSupport.errorFixtures;
import static dev.kof.compiler.AvifFrameSupport.errorProbe;
import static dev.kof.compiler.AvifFrameSupport.fixtures;
import static dev.kof.compiler.AvifFrameSupport.javaFrameFacts;
import static dev.kof.compiler.AvifFrameSupport.javaFrameFactsError;
import static dev.kof.compiler.AvifFrameSupport.probe;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end coverage for AVIF slice 2d (image-vision front, plan §34):
 * the pure-Kof frame-header prefix walk {@code libs/image/AvifFrame.kf}
 * parses {@code frame_header_obu}/{@code uncompressed_header} per the AV1
 * Bitstream Specification §5.9.2/§5.9.5/§5.9.6 (quoted from the spec PDF
 * read on the dev host 02/10): frame type, show flag, screen-content and
 * force-mv signalling, frame-id width, size override + coded size,
 * superres refusal, render size and the allow_intrabc stop point. Fixtures
 * are hand-built byte-exactly to the spec (no AVIF encoder exists on the
 * host — measured); METADATA ONLY — {@code decodeRaster} keeps refusing
 * AVIF, and the tile-group/loop-filter/quantization syntax past
 * allow_intrabc is a documented refusal frontier, not a silent pass.
 */
class AvifFrameE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    @TempDir Path tmp;

    private static final String GOLDEN = String.join("\n",
            "red-k t=1 show=1 err=1 ov=0 w=32 h=32 rd=0 rw=32 rh=32",
            "nr-k t=1 show=1 err=1 ov=1 w=5 h=3 rd=0 rw=5 rh=3",
            "rend t=1 show=1 err=1 ov=0 w=32 h=32 rd=1 rw=11 rh=6");

    @Test
    void avifFrameHeaderOnJvm() throws Exception {
        Path dir = fixtures(tmp.resolve("jvm-fixtures"));
        assertEquals(GOLDEN, runJvm(probe(dir)));
    }

    @Test
    void avifFrameHeaderOnScript() throws Exception {
        Path root = tmp.resolve("script-frame");
        Files.createDirectories(root);
        Path dir = fixtures(root.resolve("fixtures"));
        Files.writeString(root.resolve("Main.kf"), probe(dir));
        KofInterpreter.Result result = withLibrary(root,
                () -> driver.interpret(List.of(root.resolve("Main.kf")), root, new String[0]));
        assertEquals(0, result.exitCode(), "script output: " + result.stdout());
        assertEquals(GOLDEN, result.stdout().strip());
    }

    @Test
    void avifFrameHeaderOnNativeX86() throws Exception {
        Assumptions.assumeTrue(has("as", "ld"), "native toolchain absent");
        Path dir = fixtures(tmp.resolve("native-fixtures"));
        assertEquals(GOLDEN, runNativeX86(probe(dir)));
    }

    @Test
    void avifFrameHeaderOnNativeRiscv64() throws Exception {
        Assumptions.assumeTrue(has("riscv64-linux-gnu-as", "riscv64-linux-gnu-ld", "qemu-riscv64"),
                "cross riscv64 + qemu absent — skipping (NATIVE002)");
        Path dir = fixtures(tmp.resolve("riscv-fixtures"));
        assertEquals(GOLDEN, runCrossCode("riscv64", Target.NATIVE_RISCV64, probe(dir)));
    }

    @Test
    void avifFrameHeaderOnNativeAarch64() throws Exception {
        Assumptions.assumeTrue(has("aarch64-linux-gnu-as", "aarch64-linux-gnu-ld", "qemu-aarch64"),
                "cross aarch64 + qemu absent — skipping (NATIVE002)");
        Path dir = fixtures(tmp.resolve("aarch-fixtures"));
        assertEquals(GOLDEN, runCrossCode("aarch64", Target.NATIVE_AARCH64, probe(dir)));
    }

    @Test
    void frameReaderRefusesOnJs() throws Exception {
        Path root = tmp.resolve("js-frame");
        Files.createDirectories(root);
        Path dir = fixtures(root.resolve("fixtures"));
        Files.writeString(root.resolve("Main.kf"), probe(dir));
        Path out = root.resolve("out");
        CompilationResult result = withLibrary(root,
                () -> compile(root.resolve("Main.kf"), out, Target.JS));
        assertTrue(!result.success(), "JS must refuse the missing kof.io readRange binding (R6)");
        assertTrue(result.diagnostics().getDiagnostics().toString().contains("IOJS001"),
                () -> "expected IOJS001, got: " + result.diagnostics().getDiagnostics());
    }

    @Test
    void frameRefusalsAreHonest() throws Exception {
        Path root = tmp.resolve("frame-errors");
        Files.createDirectories(root);
        Path dir = errorFixtures(root);
        String goldens = String.join("\n",
                "IMAGE: avif frame show-existing not covered",
                "IMAGE: avif inter frame not covered",
                "IMAGE: avif intra block copy not covered",
                "IMAGE: avif frame size-with-refs not covered",
                "IMAGE: truncated avif frame header",
                "IMAGE: avif item has no frame header");
        assertEquals(goldens, runJvm(errorProbe(dir)));
    }

    @Test
    void javaFrameReaderAgreesOnFixtures() throws Exception {
        // second independent implementation: plain-Java AV1 5.9 prefix walk
        // vs the pure-Kof reader over the same fixtures and same refusals
        Path dir = fixtures(tmp.resolve("xcheck-fixtures"));
        String kof = runJvm(probe(dir));
        String java = String.join("\n",
                "red-k " + javaFrameFacts(dir.resolve("red-k.avif")),
                "nr-k " + javaFrameFacts(dir.resolve("nr-k.avif")),
                "rend " + javaFrameFacts(dir.resolve("rend.avif")));
        assertEquals(kof, java);
        assertEquals(GOLDEN, java);

        Path errDir = errorFixtures(tmp.resolve("xcheck-errors"));
        String javaErrors = String.join("\n",
                "showexisting:" + javaFrameFactsError(errDir.resolve("showexisting.avif")),
                "inter:" + javaFrameFactsError(errDir.resolve("inter.avif")),
                "intrabc:" + javaFrameFactsError(errDir.resolve("intrabc.avif")),
                "sizerefs:" + javaFrameFactsError(errDir.resolve("sizerefs.avif")),
                "trunc:" + javaFrameFactsError(errDir.resolve("trunc.avif")),
                "noframe:" + javaFrameFactsError(errDir.resolve("noframe.avif")));
        String expectedErrors = String.join("\n",
                "showexisting:REFUSED:showexisting",
                "inter:REFUSED:inter",
                "intrabc:REFUSED:intrabc",
                "sizerefs:REFUSED:sizerefs",
                "trunc:REFUSED:trunc",
                "noframe:REFUSED:noframe");
        assertEquals(expectedErrors, javaErrors);
    }

    private String runJvm(String code) throws Exception {
        Path root = tmp.resolve("jvm-" + Math.abs(code.hashCode()));
        Files.createDirectories(root);
        Path source = root.resolve("Main.kf");
        Files.writeString(source, code);
        Path out = root.resolve("out");
        CompilationResult result = withLibrary(root, () -> compile(source, out, Target.JVM));
        assertTrue(result.success(), () -> "compile: " + result.diagnostics().getDiagnostics());
        var stdout = new ByteArrayOutputStream();
        var previousOut = System.out;
        try {
            System.setOut(new java.io.PrintStream(stdout, true, StandardCharsets.UTF_8));
            try (var loader = new URLClassLoader(
                    new java.net.URL[]{out.toUri().toURL()}, getClass().getClassLoader())) {
                Class.forName("Default.Main", true, loader)
                        .getMethod("main", String[].class)
                        .invoke(null, (Object) new String[0]);
            }
        } finally {
            System.setOut(previousOut);
        }
        return stdout.toString(StandardCharsets.UTF_8).strip();
    }

    private String runNativeX86(String code) throws Exception {
        Path root = tmp.resolve("native-x86-" + Math.abs(code.hashCode()));
        Files.createDirectories(root);
        Files.writeString(root.resolve("Main.kf"), code);
        Path out = root.resolve("out");
        withLibrary(root, () -> {
            CompilationResult result = compile(root.resolve("Main.kf"), out, Target.NATIVE);
            assertTrue(result.success(),
                    () -> "native compile: " + result.diagnostics().getDiagnostics());
            return null;
        });
        return runBinary(out.resolve("Default/Main"));
    }

    private String runCrossCode(String arch, Target target, String code) throws Exception {
        Path root = tmp.resolve("cross-" + arch + "-" + Math.abs(code.hashCode()));
        Files.createDirectories(root);
        Files.writeString(root.resolve("Main.kf"), code);
        Path out = root.resolve("out");
        withLibrary(root, () -> {
            CompilationResult result = compile(root.resolve("Main.kf"), out, target);
            assertTrue(result.success(), arch + " compile: " + result.diagnostics().getDiagnostics());
            return null;
        });
        Path binary = out.resolve("Default/Main");
        assertTrue(Files.isRegularFile(binary), arch + " binary must exist");
        ProcessBuilder pb = NativeRiscv64E2ETest.qemu(arch, binary);
        pb.redirectErrorStream(true);
        Process process = pb.start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                .replace("\r\n", "\n").strip();
        assertEquals(0, process.waitFor(), arch + " output: " + output);
        return output;
    }

    private String runBinary(Path binary) throws Exception {
        Process process = new ProcessBuilder(binary.toString())
                .redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                .replace("\r\n", "\n").strip();
        assertEquals(0, process.waitFor(), "native output: " + output);
        return output;
    }

    private CompilationResult compile(Path source, Path out, Target target) {
        return driver.compile(source, out, target);
    }

    private static boolean has(String... cmds) {
        for (String c : cmds) {
            try {
                Process p = new ProcessBuilder("sh", "-c", "command -v " + c)
                        .redirectErrorStream(true).start();
                if (p.waitFor() != 0) return false;
            } catch (Exception e) {
                return false;
            }
        }
        return true;
    }

    private <T> T withLibrary(Path root, CheckedSupplier<T> action) throws Exception {
        copyLibrary(root.resolve("kof-install/lib/kof-libs"));
        String previous = System.getProperty("kof.install.dir");
        System.setProperty("kof.install.dir", root.resolve("kof-install").toString());
        try {
            return action.get();
        } finally {
            if (previous == null) System.clearProperty("kof.install.dir");
            else System.setProperty("kof.install.dir", previous);
        }
    }

    @FunctionalInterface
    private interface CheckedSupplier<T> {
        T get() throws Exception;
    }

    private static void copyLibrary(Path destinationRoot) throws Exception {
        Path sourceRoot = findLibraryRoot();
        try (var files = Files.walk(sourceRoot)) {
            for (Path source : files.filter(Files::isRegularFile).toList()) {
                Path destination = destinationRoot.resolve("image")
                        .resolve(sourceRoot.relativize(source));
                Files.createDirectories(destination.getParent());
                Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    private static Path findLibraryRoot() {
        Path workingDirectory = Path.of("").toAbsolutePath().normalize();
        Path fromRepository = workingDirectory.resolve("libs/image");
        if (Files.isRegularFile(fromRepository.resolve("Avif.kf"))) return fromRepository;

        Path fromModule = workingDirectory.resolve("../libs/image").normalize();
        if (Files.isRegularFile(fromModule.resolve("Avif.kf"))) return fromModule;

        throw new IllegalStateException("libs/image not found from " + workingDirectory);
    }
}
