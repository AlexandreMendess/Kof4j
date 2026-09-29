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
 * End-to-end coverage for the pure-Kof image metadata reader in
 * {@code libs/image} (image-vision front, first slice): format + pixel
 * dimensions from the header of PNG, GIF, BMP (INFO and CORE headers), JPEG and
 * the three WEBP variants, plus the truncated/unsupported errors. Fixtures are
 * hand-built byte headers (no codec). No compiler change. JVM, Native x86-64 +
 * riscv64 (qemu) and Script run the real golden; JS inherits the
 * {@code IOJS001} compile-time gap.
 */
class ImageMetadataE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    @TempDir Path tmp;

    private static final String GOLDEN = String.join("\n",
            "PNG:5x7",
            "GIF:3x4",
            "BMP:9x11",
            "BMP:2x3",
            "JPEG:20x10",
            "WEBP:6x8",
            "WEBP:13x9",
            "WEBP:100x50",
            "TIFF:12x34",
            "TIFF:12x34",
            "ICO:16x32",
            "PPM:5x7",
            "QOI:13x9",
            "isImage=true",
            "notImage=false");

    @Test
    void readsMetadataOnJvm() throws Exception {
        Path dir = imageFixtures(tmp.resolve("jvm-fixtures"));
        assertEquals(GOLDEN, runJvm(probe(dir)));
    }

    @Test
    void unsupportedFormatThrows() throws Exception {
        Path bad = tmp.resolve("bad.bin");
        Files.write(bad, new byte[]{0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09});
        assertEquals("IMAGE: unsupported image format", runJvm(errorProbe(bad)));
    }

    @Test
    void truncatedHeaderThrows() throws Exception {
        Path shortFile = tmp.resolve("short.bin");
        Files.write(shortFile, new byte[]{0x01, 0x02});
        assertEquals("IMAGE: file too short for a valid header", runJvm(errorProbe(shortFile)));
    }

    @Test
    void readsMetadataOnScript() throws Exception {
        Path root = tmp.resolve("script-image");
        Files.createDirectories(root);
        Path dir = imageFixtures(root.resolve("fixtures"));
        Files.writeString(root.resolve("Main.kf"), probe(dir));
        KofInterpreter.Result result = withLibrary(root,
                () -> driver.interpret(List.of(root.resolve("Main.kf")), root, new String[0]));
        assertEquals(0, result.exitCode(), "script output: " + result.stdout());
        assertEquals(GOLDEN, result.stdout().strip());
    }

    @Test
    void readsMetadataOnNativeX86() throws Exception {
        Assumptions.assumeTrue(System.getProperty("os.name").toLowerCase().contains("linux"),
                "Native x86-64 requires the Linux assembler/linker toolchain");
        Path dir = imageFixtures(tmp.resolve("x86-fixtures"));
        assertEquals(GOLDEN, runNativeX86(probe(dir)));
    }

    @Test
    void readsMetadataOnNativeRiscv64() throws Exception {
        Assumptions.assumeTrue(has("riscv64-linux-gnu-as", "riscv64-linux-gnu-ld", "qemu-riscv64"),
                "cross riscv64 + qemu absent — skipping (NATIVE002)");
        Path dir = imageFixtures(tmp.resolve("riscv-fixtures"));
        assertEquals(GOLDEN, runCrossCode("riscv64", Target.NATIVE_RISCV64, probe(dir)));
    }

    @Test
    void readRangeGapOnJsApplies() throws Exception {
        Path root = tmp.resolve("js-image");
        Files.createDirectories(root);
        Path dir = imageFixtures(root.resolve("fixtures"));
        Files.writeString(root.resolve("Main.kf"), probe(dir));
        Path out = root.resolve("out");
        CompilationResult result = withLibrary(root,
                () -> compile(root.resolve("Main.kf"), out, Target.JS));
        assertTrue(!result.success(), "JS must refuse the missing kof.io readRange binding (R6)");
        String diag = result.diagnostics().getDiagnostics().toString();
        assertTrue(diag.contains("IOJS001"),
                () -> "expected the explicit IOJS001 gap diagnostic, got: " + diag);
    }

    private static String probe(Path dir) {
        String base = path(dir);
        return """
            import image.Image

            String one(String p) {
                var img = Image(p)
                return img.format() + ":" + img.width() + "x" + img.height()
            }

            String flag(Bool b) {
                if (b) {
                    return "true"
                }
                return "false"
            }

            main() {
                var base = "%s"
                println(one(base + "/a.png"))
                println(one(base + "/b.gif"))
                println(one(base + "/c.bmp"))
                println(one(base + "/d.bmp"))
                println(one(base + "/e.jpg"))
                println(one(base + "/f.webp"))
                println(one(base + "/g.webp"))
                println(one(base + "/h.webp"))
                println(one(base + "/i.tif"))
                println(one(base + "/j.tif"))
                println(one(base + "/k.ico"))
                println(one(base + "/l.ppm"))
                println(one(base + "/m.qoi"))
                println("isImage=" + flag(isImage(base + "/a.png")))
                println("notImage=" + flag(isImage(base + "/bad.bin")))
            }
            """.formatted(base);
    }

    private static String errorProbe(Path src) {
        return """
            import image.Image

            main() {
                try {
                    var img = Image("%s")
                    println(img.format())
                } catch (String e) {
                    println(e)
                }
            }
            """.formatted(path(src));
    }

    private static Path imageFixtures(Path dir) throws Exception {
        Files.createDirectories(dir);
        Files.write(dir.resolve("a.png"), png(5, 7));
        Files.write(dir.resolve("b.gif"), gif(3, 4));
        Files.write(dir.resolve("c.bmp"), bmpInfo(9, 11));
        Files.write(dir.resolve("d.bmp"), bmpCore(2, 3));
        Files.write(dir.resolve("e.jpg"), jpeg(20, 10));
        Files.write(dir.resolve("f.webp"), webpVp8(6, 8));
        Files.write(dir.resolve("g.webp"), webpVp8l(13, 9));
        Files.write(dir.resolve("h.webp"), webpVp8x(100, 50));
        Files.write(dir.resolve("i.tif"), tiff(12, 34, true));
        Files.write(dir.resolve("j.tif"), tiff(12, 34, false));
        Files.write(dir.resolve("k.ico"), ico(16, 32));
        Files.write(dir.resolve("l.ppm"), ppm(5, 7));
        Files.write(dir.resolve("m.qoi"), qoi(13, 9));
        Files.write(dir.resolve("bad.bin"), new byte[]{0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07});
        return dir;
    }

    private static byte[] png(int w, int h) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A});
        out.write(new byte[]{0, 0, 0, 13, 'I', 'H', 'D', 'R'});
        out.write(be32(w));
        out.write(be32(h));
        out.write(new byte[]{8, 2, 0, 0, 0, 0, 0, 0, 0});
        out.write(new byte[]{0, 0, 0, 0});
        return out.toByteArray();
    }

    private static byte[] gif(int w, int h) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write("GIF89a".getBytes(StandardCharsets.US_ASCII));
        out.write(le16(w));
        out.write(le16(h));
        out.write(new byte[]{0x00, 0x00, 0x00});
        return out.toByteArray();
    }

    private static byte[] bmpInfo(int w, int h) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(new byte[]{'B', 'M'});
        out.write(new byte[]{0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0});
        out.write(le32(40));
        out.write(le32(w));
        out.write(le32(h));
        out.write(new byte[]{1, 0, 24, 0});
        return out.toByteArray();
    }

    private static byte[] bmpCore(int w, int h) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(new byte[]{'B', 'M'});
        out.write(new byte[]{0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0});
        out.write(le32(12));
        out.write(le16(w));
        out.write(le16(h));
        out.write(new byte[]{1, 0, 24, 0});
        return out.toByteArray();
    }

    private static byte[] jpeg(int w, int h) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(new byte[]{(byte) 0xFF, (byte) 0xD8});
        out.write(new byte[]{(byte) 0xFF, (byte) 0xE0, 0x00, 0x10});
        for (int i = 0; i < 14; i++) out.write(0x00);
        out.write(new byte[]{(byte) 0xFF, (byte) 0xC0, 0x00, 0x11, 0x08});
        out.write(be16(h));
        out.write(be16(w));
        out.write(new byte[]{0x03, 0, 0, 0});
        return out.toByteArray();
    }

    private static byte[] webpVp8(int w, int h) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write("RIFF".getBytes(StandardCharsets.US_ASCII));
        out.write(le32(0));
        out.write("WEBP".getBytes(StandardCharsets.US_ASCII));
        out.write("VP8 ".getBytes(StandardCharsets.US_ASCII));
        out.write(le32(0));
        out.write(new byte[]{0x00, 0x00, 0x00});
        out.write(new byte[]{(byte) 0x9D, 0x01, 0x2A});
        out.write(le16(w));
        out.write(le16(h));
        return out.toByteArray();
    }

    private static byte[] webpVp8l(int w, int h) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write("RIFF".getBytes(StandardCharsets.US_ASCII));
        out.write(le32(0));
        out.write("WEBP".getBytes(StandardCharsets.US_ASCII));
        out.write("VP8L".getBytes(StandardCharsets.US_ASCII));
        out.write(le32(0));
        out.write(0x2F);
        int bits = ((w - 1) & 0x3FFF) | (((h - 1) & 0x3FFF) << 14);
        out.write(le32(bits));
        return out.toByteArray();
    }

    private static byte[] webpVp8x(int w, int h) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write("RIFF".getBytes(StandardCharsets.US_ASCII));
        out.write(le32(0));
        out.write("WEBP".getBytes(StandardCharsets.US_ASCII));
        out.write("VP8X".getBytes(StandardCharsets.US_ASCII));
        out.write(le32(0));
        out.write(new byte[]{0, 0, 0, 0});
        out.write(le24(w - 1));
        out.write(le24(h - 1));
        return out.toByteArray();
    }

    private static byte[] tiff(int w, int h, boolean little) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        if (little) {
            out.write(new byte[]{'I', 'I', 0x2A, 0x00, 0x08, 0x00, 0x00, 0x00});
            out.write(new byte[]{0x02, 0x00});
            out.write(new byte[]{0x00, 0x01, 0x03, 0x00, 0x01, 0x00, 0x00, 0x00});
            out.write(le32(w));
            out.write(new byte[]{0x01, 0x01, 0x03, 0x00, 0x01, 0x00, 0x00, 0x00});
            out.write(le32(h));
        } else {
            out.write(new byte[]{'M', 'M', 0x00, 0x2A, 0x00, 0x00, 0x00, 0x08});
            out.write(new byte[]{0x00, 0x02});
            out.write(new byte[]{0x01, 0x00, 0x00, 0x03, 0x00, 0x00, 0x00, 0x01});
            out.write(new byte[]{0x00, (byte) w, 0x00, 0x00});
            out.write(new byte[]{0x01, 0x01, 0x00, 0x03, 0x00, 0x00, 0x00, 0x01});
            out.write(new byte[]{0x00, (byte) h, 0x00, 0x00});
        }
        out.write(new byte[]{0x00, 0x00, 0x00, 0x00});
        return out.toByteArray();
    }

    private static byte[] ico(int w, int h) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(new byte[]{0x00, 0x00, 0x01, 0x00, 0x01, 0x00});
        out.write(new byte[]{(byte) (w & 0xFF), (byte) (h & 0xFF)});
        out.write(new byte[]{0x00, 0x00, 0x02, 0x00});
        out.write(le32(40));
        out.write(le32(22));
        return out.toByteArray();
    }

    private static byte[] ppm(int w, int h) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(("P6\n" + w + " " + h + "\n255\n").getBytes(StandardCharsets.US_ASCII));
        for (int i = 0; i < w * h * 3; i++) out.write(0x00);
        return out.toByteArray();
    }

    private static byte[] qoi(int w, int h) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write("qoif".getBytes(StandardCharsets.US_ASCII));
        out.write(be32(w));
        out.write(be32(h));
        out.write(new byte[]{4, 0, 1, 0});
        return out.toByteArray();
    }

    private static byte[] be16(int v) {
        return new byte[]{(byte) ((v >> 8) & 0xFF), (byte) (v & 0xFF)};
    }

    private static byte[] be32(int v) {
        return new byte[]{(byte) ((v >> 24) & 0xFF), (byte) ((v >> 16) & 0xFF),
                (byte) ((v >> 8) & 0xFF), (byte) (v & 0xFF)};
    }

    private static byte[] le16(int v) {
        return new byte[]{(byte) (v & 0xFF), (byte) ((v >> 8) & 0xFF)};
    }

    private static byte[] le24(int v) {
        return new byte[]{(byte) (v & 0xFF), (byte) ((v >> 8) & 0xFF), (byte) ((v >> 16) & 0xFF)};
    }

    private static byte[] le32(int v) {
        return new byte[]{(byte) (v & 0xFF), (byte) ((v >> 8) & 0xFF),
                (byte) ((v >> 16) & 0xFF), (byte) ((v >> 24) & 0xFF)};
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
        if (Files.isRegularFile(fromRepository.resolve("Image.kf"))) return fromRepository;

        Path fromModule = workingDirectory.resolve("../libs/image").normalize();
        if (Files.isRegularFile(fromModule.resolve("Image.kf"))) return fromModule;

        throw new IllegalStateException("libs/image not found from " + workingDirectory);
    }
}
