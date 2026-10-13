package dev.kof.compiler;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static dev.kof.compiler.Av1DecodeChromaSupport.expectedText;
import static dev.kof.compiler.Av1DecodeChromaSupport.golden;
import static dev.kof.compiler.Av1DecodeChromaSupport.kofProbe;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end coverage for AVIF slice 3ai (image-vision front, plan §34): the
 * CHROMA plane composition — {@code av1DecodeChromaBlock} /
 * {@code av1DecodeChromaPlane} in {@code libs/image/Av1DecodeChroma.kf}, driven
 * from {@code libs/image/Av1DecodeTileRecon.kf} when {@code numPlanes > 1}.
 *
 * <p>libaom's {@code decode_token_recon_block} runs {@code plane = 0} then U then
 * V for the SAME mode-info block. Chroma's transform size is
 * {@code av1_get_max_uv_txsize} — ONE per block, not subdivided (slice 3ah) — its
 * transform type is DERIVED from the UV mode (libaom transmits {@code tx_type}
 * for the luma plane only, so no type symbol is read), and each plane carries its
 * own entropy-context arrays.
 *
 * <p>The oracle is the instrumented chroma build (hand-written encoder + libaom's
 * own decoder, both printing every symbol; self-checked, so encoder and decoder
 * agree symbol for symbol). Its fourth case holds TWO mode-info blocks side by
 * side, so the second block's chroma reads the contexts the first wrote — the
 * multi-block shape whose absence hid the oracle defect recorded as
 * {@code known-bugs} §682.
 */
class Av1DecodeChromaE2ETest implements LibraryInstallSupport {

    private final CompilerDriver driver = new CompilerDriver();

    @TempDir Path tmp;

    @Test
    void chromaGoldenIsPinned() {
        assertTrue(golden().startsWith("C 0 "), "golden must be the chroma oracle dump");
        assertEquals(5, dev.kof.compiler.Av1DecodeChromaSupport.allCaseIndices().size(),
                "five oracle cases (the multi-block one and the CFL one)");
    }

    private static final String PLANES_RESOURCE = "/av1_decode_chroma_planes.txt.b64";

    private static String planesGolden() {
        return Av1DecodeChromaSupport.resourceText(PLANES_RESOURCE,
                Av1DecodeChromaSupport.PLANES_SHA256);
    }

    /** The oracle records only — the `Z` checksum lines are compared separately. */
    private static String stripZ(String out) {
        List<String> keep = new ArrayList<>();
        for (String line : out.split("\n")) {
            /* `Z`/`L`/`W` are the reconstructed-sample captures; the oracle
             * comparison below is about the DECODE records only. */
            if (line.startsWith("Z ") || line.startsWith("L ") || line.startsWith("W ")) continue;
            keep.add(line);
        }
        return String.join("\n", keep);
    }

    private static String zLines(String out) {
        List<String> z = new ArrayList<>();
        for (String line : out.split("\n")) {
            if (line.startsWith("Z ")) z.add(line);
        }
        return String.join("\n", z);
    }

    @Test
    void chromaPlanesArePinned() throws Exception {
        /* Characterization lock on the CHROMA planes: the oracle does not
         * reconstruct, so this is not an independent reference — it exists so the
         * CFL window geometry (origin + border offset) cannot regress silently. */
        assertEquals(planesGolden(), zLines(runJvm(kofProbe())));
    }

    private static final String PIXELS_RESOURCE = "/av1_decode_chroma_pixels_golden.txt.b64";
    private static final String PIXELS_SHA256 =
        "6968751d1e5feb3d2f49c1a4517dde19052dfd007448f869b761433651ac480d";

    /** The oracle's raw reconstructed samples for the CFL case (libaom's own
     * inverse transform + CFL prediction). */
    private static String pixelsGolden() {
        return Av1DecodeChromaSupport.resourceText(PIXELS_RESOURCE, PIXELS_SHA256);
    }

    private static String pixelLines(String out) {
        List<String> keep = new ArrayList<>();
        for (String line : out.split("\n")) {
            if (line.startsWith("L 3 ") || line.startsWith("W 3 ")) keep.add(line);
        }
        return String.join("\n", keep);
    }

    /**
     * END-TO-END PIXEL REFERENCE for the CFL composition: the driver's
     * reconstructed LUMA and CHROMA against libaom's, sample for sample. This is
     * the check the rolling checksum could not be — and it is what caught the
     * inverted CFL alpha sign in `readBodyBd` (`signU == 2` negated where libaom
     * negates on `signU == 1`).
     */
    @Test
    void chromaAndLumaPixelsMatchLibaom() throws Exception {
        assertEquals(pixelsGolden(), pixelLines(runJvm(kofProbe())));
    }

    @Test
    void av1ChromaOnJvm() throws Exception {
        assertEquals(expectedText(), stripZ(runJvm(kofProbe())));
    }

    @Test
    void av1ChromaOnScript() throws Exception {
        Path root = tmp.resolve("script");
        Files.createDirectories(root);
        Files.writeString(root.resolve("Main.kf"), kofProbe());
        KofInterpreter.Result result = withLibrary(root,
                () -> driver.interpret(List.of(root.resolve("Main.kf")), root, new String[0]));
        assertEquals(0, result.exitCode(), "script output: " + result.stdout());
        assertEquals(expectedText(), stripZ(result.stdout().strip()));
    }

    @Test
    void av1ChromaOnNativeX86() throws Exception {
        Assumptions.assumeTrue(has("as", "ld"), "native toolchain absent");
        assertEquals(expectedText(), stripZ(runNative("native-x86", Target.NATIVE, kofProbe())));
    }

    @Test
    void av1ChromaOnNativeRiscv64() throws Exception {
        Assumptions.assumeTrue(has("riscv64-linux-gnu-as", "riscv64-linux-gnu-ld", "qemu-riscv64"),
                "cross riscv64 + qemu absent — skipping (NATIVE002)");
        assertEquals(expectedText(),
                stripZ(runCross("riscv64", Target.NATIVE_RISCV64, kofProbe())));
    }

    @Test
    void av1ChromaOnNativeAarch64() throws Exception {
        Assumptions.assumeTrue(has("aarch64-linux-gnu-as", "aarch64-linux-gnu-ld", "qemu-aarch64"),
                "cross aarch64 + qemu absent — skipping (NATIVE002)");
        assertEquals(expectedText(),
                stripZ(runCross("aarch64", Target.NATIVE_AARCH64, kofProbe())));
    }

    private String runJvm(String code) throws Exception {
        Path root = tmp.resolve("jvm-" + Math.abs(code.hashCode()));
        Files.createDirectories(root);
        Path source = root.resolve("Main.kf");
        Files.writeString(source, code);
        Path out = root.resolve("out");
        CompilationResult result = withLibrary(root, () -> driver.compile(source, out, Target.JVM));
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

    private String runNative(String tag, Target target, String code) throws Exception {
        Path root = tmp.resolve(tag + "-" + Math.abs(code.hashCode()));
        Files.createDirectories(root);
        Files.writeString(root.resolve("Main.kf"), code);
        Path out = root.resolve("out");
        withLibrary(root, () -> {
            CompilationResult result = driver.compile(root.resolve("Main.kf"), out, target);
            assertTrue(result.success(),
                    () -> tag + " compile: " + result.diagnostics().getDiagnostics());
            return null;
        });
        return runBinary(out.resolve("Default/Main"), tag);
    }

    private String runCross(String arch, Target target, String code) throws Exception {
        Path root = tmp.resolve("cross-" + arch + "-" + Math.abs(code.hashCode()));
        Files.createDirectories(root);
        Files.writeString(root.resolve("Main.kf"), code);
        Path out = root.resolve("out");
        withLibrary(root, () -> {
            CompilationResult result = driver.compile(root.resolve("Main.kf"), out, target);
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

    private String runBinary(Path binary, String tag) throws Exception {
        Process process = new ProcessBuilder(binary.toString())
                .redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                .replace("\r\n", "\n").strip();
        assertEquals(0, process.waitFor(), tag + " output: " + output);
        return output;
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

    @Override
    public String libraryName() {
        return "image";
    }

    @Override
    public List<String> libraryMarkers() {
        return List.of("Av1DecodeTileRecon.kf");
    }
}
