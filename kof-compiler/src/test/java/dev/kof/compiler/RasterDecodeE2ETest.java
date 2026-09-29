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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end coverage for the pure-Kof raw raster decoder in {@code libs/image}
 * (image-vision pixel slice, provisional {@code Raster} surface): PNM P5/P6 and
 * farbfeld, bounded samples, plus the unsupported/oversized errors. Fixtures are
 * hand-built byte images (no codec). No compiler change. JVM, Native x86-64 +
 * riscv64 (qemu) and Script run the real golden; JS inherits the {@code IOJS001}
 * compile-time gap.
 */
class RasterDecodeE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    @TempDir Path tmp;

    private static final String GOLDEN = String.join("\n",
            "PPM:2x2 ch=3",
            "px=1,2,3,4,5,6,7,8,9,10,11,12",
            "PGM:3x1 ch=1",
            "px=10,20,30",
            "farbfeld:2x2 ch=4",
            "px=10,20,30,40,50,60,70,80,90,100,110,120,130,140,150,160",
            "PPM:1x2 ch=3",
            "px=4,5,6,10,11,12",
            "PPM:4x4 ch=3",
            "px=1,2,3,1,2,3,4,5,6,4,5,6,1,2,3,1,2,3,4,5,6,4,5,6,7,8,9,7,8,9,10,11,12,10,11,12,7,8,9,7,8,9,10,11,12,10,11,12",
            "PPM:2x2 ch=3",
            "px=4,5,6,1,2,3,10,11,12,7,8,9",
            "PPM:2x2 ch=3",
            "px=7,8,9,10,11,12,1,2,3,4,5,6",
            "PPM:2x2 ch=3",
            "px=7,8,9,1,2,3,10,11,12,4,5,6");

    @Test
    void decodesRasterOnJvm() throws Exception {
        Path dir = rasterFixtures(tmp.resolve("jvm-rasters"));
        assertEquals(GOLDEN, runJvm(rasterProbe(dir)));
    }

    @Test
    void rasterUnsupportedFormatThrows() throws Exception {
        Path src = tmp.resolve("a.png");
        Files.write(src, new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A,
                0, 0, 0, 13, 'I', 'H', 'D', 'R', 0, 0, 0, 2, 0, 0, 0, 2, 8, 2, 0, 0, 0});
        assertEquals("IMAGE: raster decode is not supported for PNG", runJvm(errorProbe(src)));
    }

    @Test
    void oversizedRasterThrows() throws Exception {
        Path src = tmp.resolve("big.ppm");
        Files.write(src, ("P6\n200 200\n255\n").getBytes(StandardCharsets.US_ASCII));
        assertEquals("IMAGE: raster too large for this slice (max 16384 samples)",
                runJvm(errorProbe(src)));
    }

    @Test
    void decodesRasterOnScript() throws Exception {
        Path root = tmp.resolve("script-raster");
        Files.createDirectories(root);
        Path dir = rasterFixtures(root.resolve("fixtures"));
        Files.writeString(root.resolve("Main.kf"), rasterProbe(dir));
        KofInterpreter.Result result = withLibrary(root,
                () -> driver.interpret(List.of(root.resolve("Main.kf")), root, new String[0]));
        assertEquals(0, result.exitCode(), "script output: " + result.stdout());
        assertEquals(GOLDEN, result.stdout().strip());
    }

    @Test
    void decodesRasterOnNativeX86() throws Exception {
        Assumptions.assumeTrue(System.getProperty("os.name").toLowerCase().contains("linux"),
                "Native x86-64 requires the Linux assembler/linker toolchain");
        Path dir = rasterFixtures(tmp.resolve("x86-rasters"));
        assertEquals(GOLDEN, runNativeX86(rasterProbe(dir)));
    }

    @Test
    void decodesRasterOnNativeRiscv64() throws Exception {
        Assumptions.assumeTrue(has("riscv64-linux-gnu-as", "riscv64-linux-gnu-ld", "qemu-riscv64"),
                "cross riscv64 + qemu absent — skipping (NATIVE002)");
        Path dir = rasterFixtures(tmp.resolve("riscv-rasters"));
        assertEquals(GOLDEN, runCrossCode("riscv64", Target.NATIVE_RISCV64, rasterProbe(dir)));
    }

    @Test
    void rasterReadRangeGapOnJs() throws Exception {
        Path root = tmp.resolve("js-raster");
        Files.createDirectories(root);
        Path dir = rasterFixtures(root.resolve("fixtures"));
        Files.writeString(root.resolve("Main.kf"), rasterProbe(dir));
        Path out = root.resolve("out");
        CompilationResult result = withLibrary(root,
                () -> compile(root.resolve("Main.kf"), out, Target.JS));
        assertTrue(!result.success(), "JS must refuse the missing kof.io readRange binding (R6)");
        String diag = result.diagnostics().getDiagnostics().toString();
        assertTrue(diag.contains("IOJS001"),
                () -> "expected the explicit IOJS001 gap diagnostic, got: " + diag);
    }

    private static String rasterProbe(Path dir) {
        String base = path(dir);
        return """
            import image.Raster

            String dump(Raster r) {
                var out = r.format + ":" + r.width + "x" + r.height + " ch=" + r.channels + "\\npx="
                var i = 0
                while (i < r.samples.length) {
                    if (i > 0) {
                        out = out + ","
                    }
                    out = out + r.samples[i]
                    i = i + 1
                }
                return out
            }

            main() {
                var base = "%s"
                println(dump(decodeRaster(base + "/rgb.ppm")))
                println(dump(decodeRaster(base + "/gray.pgm")))
                println(dump(decodeRaster(base + "/rgba.ff")))
                var rgb = decodeRaster(base + "/rgb.ppm")
                println(dump(cropRaster(rgb, 1, 0, 1, 2)))
                println(dump(resizeNearest(rgb, 4, 4)))
                println(dump(flipHorizontal(rgb)))
                println(dump(flipVertical(rgb)))
                println(dump(rotate90(rgb)))
            }
            """.formatted(base);
    }

    private static String errorProbe(Path src) {
        return """
            import image.Raster

            main() {
                try {
                    var r = decodeRaster("%s")
                    println(r.format)
                } catch (String e) {
                    println(e)
                }
            }
            """.formatted(path(src));
    }

    private static Path rasterFixtures(Path dir) throws Exception {
        Files.createDirectories(dir);
        Files.write(dir.resolve("rgb.ppm"), rasterPpm());
        Files.write(dir.resolve("gray.pgm"), rasterPgm());
        Files.write(dir.resolve("rgba.ff"), rasterFarbfeld());
        return dir;
    }

    private static byte[] rasterPpm() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write("P6\n2 2\n255\n".getBytes(StandardCharsets.US_ASCII));
        out.write(new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12});
        return out.toByteArray();
    }

    private static byte[] rasterPgm() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write("P5\n3 1\n255\n".getBytes(StandardCharsets.US_ASCII));
        out.write(new byte[]{10, 20, 30});
        return out.toByteArray();
    }

    private static byte[] rasterFarbfeld() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write("farbfeld".getBytes(StandardCharsets.US_ASCII));
        out.write(new byte[]{0, 0, 0, 2, 0, 0, 0, 2});
        int[] vals = {10, 20, 30, 40, 50, 60, 70, 80, 90, 100, 110, 120, 130, 140, 150, 160};
        for (int v : vals) {
            out.write(v & 0xFF);
            out.write(v & 0xFF);
        }
        return out.toByteArray();
    }

    private static String path(Path p) {
        return p.toString().replace('\\', '/');
    }

    private String runJvm(String code) throws Exception {
        Path root = tmp.resolve("jvm-" + Math.abs(code.hashCode()));
        Files.createDirectories(root);
        Path source = root.resolve("Main.kf");
        Files.writeString(source, code);
        Path out = root.resolve("out");
        CompilationResult result = withLibrary(root, () -> compile(source, out, Target.JVM));
        assertTrue(result.success(), () -> "compile: " + result.diagnostics().getDiagnostics());

        var stdout = new java.io.ByteArrayOutputStream();
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
            assertTrue(result.success(),
                    () -> arch + " compile: " + result.diagnostics().getDiagnostics());
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

    private static String runBinary(Path binary) throws Exception {
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
        if (Files.isRegularFile(fromRepository.resolve("Raster.kf"))) return fromRepository;

        Path fromModule = workingDirectory.resolve("../libs/image").normalize();
        if (Files.isRegularFile(fromModule.resolve("Raster.kf"))) return fromModule;

        throw new IllegalStateException("libs/image not found from " + workingDirectory);
    }
}
