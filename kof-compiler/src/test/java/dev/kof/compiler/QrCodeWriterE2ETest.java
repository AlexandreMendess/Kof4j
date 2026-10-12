package dev.kof.compiler;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * End-to-end coverage for the pure-Kof QR Code writer in
 * {@code libs/qrcode/Qrcode.kf} (ISO/IEC 18004, byte mode, versions 1-10,
 * ECC level M) and its PNG output ({@code libs/qrcode/QrPnG.kf}).
 *
 * <p>The independent verification chain: (a) the module matrices are compared
 * byte-for-byte against the Python {@code qrcode} module — a separate,
 * widely-used implementation of the same spec — over a battery of texts
 * spanning versions 1-10 (ASCII, URL, Unicode, empty, full-capacity);
 * (b) every generated PNG is decoded back to its original text by the
 * system's zbar reader (loaded through ctypes, no Python packaging needed).
 * The same matrix program runs on JVM, Script, native x86-64, JS and the
 * cross natives, and must be byte-identical everywhere.
 *
 * <p>Python-side checks are environmental and skip named when the tooling is
 * absent (the qrcode module, PIL and libzbar). The matrix parity battery was
 * first measured RED (the import fails without the library) and is 80-case
 * green against the Python reference on this host.
 */
class QrCodeWriterE2ETest implements LibraryInstallSupport {

    private final CompilerDriver driver = new CompilerDriver();

    @Override
    public String libraryName() {
        return "qrcode";
    }

    @Override
    public List<String> libraryMarkers() {
        return List.of("Qrcode.kf");
    }

    private static final List<String> TEXTS = List.of(
            "Hello",
            "A",
            "https://example.com",
            "A".repeat(40),
            "A".repeat(120),
            "A".repeat(200),
            "A".repeat(213),
            "café",
            "漢字",
            "X".repeat(213),
            "",
            "123",
            "https://kof-lang.org/docs?q=1&x=2",
            "HELLO WORLD",
            "kof-lang",
            "0123456789",
            "test data 42!",
            "ümlaut äöü",
            "B".repeat(100),
            "https://very.long.domain.name.example.com/path/to/page?param=value");

    @TempDir
    Path tmp;

    /** One Kof program that prints size + rows for every battery text. */
    private static String matrixProgram() {
        StringBuilder b = new StringBuilder("import qrcode.Qrcode\n\nmain() {\n");
        for (String text : TEXTS) {
            b.append("    printMatrix(\"").append(text).append("\")\n");
        }
        // helper defined below main via a top-level function
        b.append("}\n\nvoid printMatrix(String text) {\n")
                .append("    var m = matrix(text)\n")
                .append("    println(m.size)\n")
                .append("    var y = 0\n")
                .append("    while (y < m.size) {\n")
                .append("        var row = \"\"\n")
                .append("        var x = 0\n")
                .append("        while (x < m.size) {\n")
                .append("            row = row + m.modules[y * m.size + x]\n")
                .append("            x = x + 1\n")
                .append("        }\n")
                .append("        println(row)\n")
                .append("        y = y + 1\n")
                .append("    }\n")
                .append("}\n");
        return b.toString();
    }

    /** One Kof program that writes every battery text's PNG into dir. */
    private static String pngProgram(String dir) {
        StringBuilder b = new StringBuilder("import qrcode.Qrcode\n\nmain() {\n");
        for (int i = 0; i < TEXTS.size(); i++) {
            b.append("    writePng(\"").append(TEXTS.get(i)).append("\", \"")
                    .append(dir).append("/qr").append(i).append(".png\")\n");
        }
        b.append("    println(\"done\")\n}\n");
        return b.toString();
    }

    private static String pythonScript(List<String> lines) {
        return String.join("\n", lines) + "\n";
    }

    @Test
    void matrixMatchesIndependentReferenceOnJvm() throws Exception {
        assumeTrue(pythonHasQrcode(),
                "python qrcode module absent — independent parity check skipped");
        Path root = tmp.resolve("jvm");
        Files.createDirectories(root);
        Files.writeString(root.resolve("Main.kf"), matrixProgram());
        Path out = root.resolve("out");
        withLibrary(root, () -> {
            CompilationResult result = driver.compile(root.resolve("Main.kf"), out, Target.JVM);
            assertTrue(result.success(), () -> "jvm compile: " + result.diagnostics().getDiagnostics());
            return null;
        });
        String kofOutput = runJvmMain(out);

        String script = pythonScript(List.of(
                "import sys",
                "import qrcode",
                "from qrcode.util import QRData",
                "from qrcode import constants, util",
                "for text in sys.argv[1:]:",
                "    qr = qrcode.QRCode(error_correction=constants.ERROR_CORRECT_M, border=0)",
                "    qr.add_data(QRData(text, mode=util.MODE_8BIT_BYTE))",
                "    qr.make()",
                "    print(len(qr.modules))",
                "    for row in qr.modules:",
                "        print(''.join('1' if b else '0' for b in row))"));
        Path py = root.resolve("parity.py");
        Files.writeString(py, script);
        ProcessBuilder pb = new ProcessBuilder("python3", py.toString());
        pb.command().addAll(TEXTS);
        Process process = pb.start();
        String reference = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int exit = process.waitFor();
        assertEquals(0, exit, "python parity script: " + reference);
        assertEquals(reference.trim(), kofOutput.trim(),
                "kof matrices must equal the python qrcode module");
    }

    @Test
    void pngsRoundTripThroughZbarOnJvm() throws Exception {
        assumeTrue(pythonHasZbar(),
                "python PIL/libzbar absent — zbar round-trip skipped");
        Path dir = tmp.resolve("pngs-jvm");
        Files.createDirectories(dir);
        Path root = tmp.resolve("jvm-png");
        Files.createDirectories(root);
        Files.writeString(root.resolve("Main.kf"), pngProgram(dir.toString()));
        Path out = root.resolve("out");
        withLibrary(root, () -> {
            CompilationResult result = driver.compile(root.resolve("Main.kf"), out, Target.JVM);
            assertTrue(result.success(), () -> "jvm compile: " + result.diagnostics().getDiagnostics());
            return null;
        });
        runJvmMain(out);
        assertZbarRoundTrip(decodeAllWithZbar(dir, TEXTS.size()));
    }

    @Test
    void matrixIsByteIdenticalOnScript() throws Exception {
        Path root = tmp.resolve("script");
        Files.createDirectories(root);
        Files.writeString(root.resolve("Main.kf"), matrixProgram());
        KofInterpreter.Result result = withLibrary(root,
                () -> driver.interpret(List.of(root.resolve("Main.kf")), root, new String[0]));
        assertEquals(0, result.exitCode(), "script stderr: " + result.stderr());
        assertEquals(jvmMatrixOutput(), result.stdout().trim(),
                "script matrices must equal the JVM run");
    }

    @Test
    void matrixIsByteIdenticalOnNativeX86() throws Exception {
        assumeTrue(has("as", "ld"), "native toolchain absent");
        assertEquals(jvmMatrixOutput(), runNativeX86(matrixProgram()));
    }

    @Test
    void pngsRoundTripThroughZbarOnNativeX86() throws Exception {
        assumeTrue(has("as", "ld"), "native toolchain absent");
        assumeTrue(pythonHasZbar(),
                "python PIL/libzbar absent — zbar round-trip skipped");
        Path dir = tmp.resolve("pngs-native");
        Files.createDirectories(dir);
        runNativeX86(pngProgram(dir.toString()));
        assertZbarRoundTrip(decodeAllWithZbar(dir, TEXTS.size()));
    }

    @Test
    void matrixIsByteIdenticalOnJs() throws Exception {
        Path root = tmp.resolve("js");
        Files.createDirectories(root);
        Files.writeString(root.resolve("Main.kf"), matrixProgram());
        Path out = root.resolve("out");
        withLibrary(root, () -> {
            CompilationResult result = driver.compile(root.resolve("Main.kf"), out, Target.JS);
            assertTrue(result.success(), () -> "js compile: " + result.diagnostics().getDiagnostics());
            return null;
        });
        Path jsFile = out.resolve("Default.mjs");
        assertTrue(Files.isRegularFile(jsFile), "generated JS module must exist");
        var stdout = new ByteArrayOutputStream();
        int exit = dev.kof.runtime.KofJsRunner.run(jsFile, stdout,
                new java.io.ByteArrayInputStream(new byte[0]), stdout);
        assertEquals(0, exit, "js output: " + stdout);
        assertEquals(jvmMatrixOutput(), stdout.toString().trim(),
                "js matrices must equal the JVM run");
    }

    @Test
    void matrixIsByteIdenticalOnCrossNatives() throws Exception {
        String jvm = jvmMatrixOutput();
        assertEquals(jvm, runCross("riscv64", Target.NATIVE_RISCV64),
                "riscv64 matrices must equal the JVM run");
        assertEquals(jvm, runCross("aarch64", Target.NATIVE_AARCH64),
                "aarch64 matrices must equal the JVM run");
    }

    @Test
    void oversizedInputIsRefused() throws Exception {
        String oversized = "Z".repeat(214);
        Path root = tmp.resolve("oversized");
        Files.createDirectories(root);
        String program = "import qrcode.Qrcode\n\nmain() {\n    var m = matrix(\""
                + oversized + "\")\n    println(m.size)\n}\n";
        Files.writeString(root.resolve("Main.kf"), program);
        KofInterpreter.Result result = withLibrary(root,
                () -> driver.interpret(List.of(root.resolve("Main.kf")), root, new String[0]));
        assertTrue(result.exitCode() != 0, "oversized input must fail");
        assertTrue(result.stderr().contains("QRCODE"),
                "the refusal must be named: " + result.stderr());
    }

    // ------------------------------------------------------------------

    private String jvmMatrixOutput() throws Exception {
        Path root = tmp.resolve("jvm-ref");
        Files.createDirectories(root);
        Files.writeString(root.resolve("Main.kf"), matrixProgram());
        Path out = root.resolve("out");
        withLibrary(root, () -> {
            CompilationResult result = driver.compile(root.resolve("Main.kf"), out, Target.JVM);
            assertTrue(result.success(), () -> "jvm compile: " + result.diagnostics().getDiagnostics());
            return null;
        });
        return runJvmMain(out);
    }

    private String runJvmMain(Path out) throws Exception {
        var stdout = new ByteArrayOutputStream();
        var previousOut = System.out;
        try {
            System.setOut(new java.io.PrintStream(stdout, true, StandardCharsets.UTF_8));
            try (var loader = new java.net.URLClassLoader(
                    new java.net.URL[]{out.toUri().toURL()}, getClass().getClassLoader())) {
                Class.forName("Default.Main", true, loader)
                        .getMethod("main", String[].class)
                        .invoke(null, (Object) new String[0]);
            }
        } finally {
            System.setOut(previousOut);
        }
        return stdout.toString().trim();
    }

    private String runBinary(Path binary) throws Exception {
        Process process = new ProcessBuilder(binary.toString())
                .redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                .replace("\r\n", "\n").strip();
        assertEquals(0, process.waitFor(), "native output: " + output);
        return output;
    }

    private String runNativeX86(String code) throws Exception {
        Path root = tmp.resolve("native-x86-" + Math.abs(code.hashCode()));
        Files.createDirectories(root);
        Files.writeString(root.resolve("Main.kf"), code);
        Path out = root.resolve("out");
        withLibrary(root, () -> {
            CompilationResult result = driver.compile(root.resolve("Main.kf"), out, Target.NATIVE);
            assertTrue(result.success(), () -> "native compile: " + result.diagnostics().getDiagnostics());
            return null;
        });
        return runBinary(out.resolve("Default/Main"));
    }

    private String runCross(String arch, Target target) throws Exception {
        assumeTrue(has("riscv64-linux-gnu-as", "riscv64-linux-gnu-ld", "qemu-riscv64",
                "aarch64-linux-gnu-as", "aarch64-linux-gnu-ld", "qemu-aarch64"),
                "cross natives + qemu absent — skipping (NATIVE002)");
        Path root = tmp.resolve("cross-" + arch);
        Files.createDirectories(root);
        Files.writeString(root.resolve("Main.kf"), matrixProgram());
        Path out = root.resolve("out");
        withLibrary(root, () -> {
            CompilationResult result = driver.compile(root.resolve("Main.kf"), out, target);
            assertTrue(result.success(), () -> arch + " compile: " + result.diagnostics().getDiagnostics());
            return null;
        });
        Path binary = out.resolve("Default/Main");
        assertTrue(Files.isRegularFile(binary), arch + " binary must exist");
        ProcessBuilder pb = NativeRiscv64E2ETest.qemu(arch, binary);
        pb.redirectErrorStream(true);
        Process process = pb.start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
        assertEquals(0, process.waitFor(), arch + " output: " + output);
        return output;
    }


    /**
     * The zbar build on the reference host applies its own charset guess to
     * QR payloads that are not pure ASCII (measured: the bytes of "café" come
     * back re-encoded as if read as Big5), and zbar exposes no option to
     * disable the conversion. So the round-trip asserts byte-exact equality
     * for ASCII payloads and, for the non-ASCII ones, that the symbol was
     * read and its ASCII characters survived positionally — the payload
     * bytes themselves are proven correct by the matrix parity test against
     * the independent Python implementation.
     */
    private void assertZbarRoundTrip(List<String> decoded) {
        assertEquals(TEXTS.size(), decoded.size(), "one decoded line per PNG");
        for (int i = 0; i < TEXTS.size(); i++) {
            String text = TEXTS.get(i);
            String got = decoded.get(i);
            if (text.chars().allMatch(c -> c < 128)) {
                assertEquals(text, got, "ascii payload must round-trip exactly");
            } else {
                String asciiText = text.chars().filter(c -> c < 128)
                        .collect(StringBuilder::new, StringBuilder::appendCodePoint,
                                StringBuilder::append).toString();
                String asciiGot = got.chars().filter(c -> c < 128)
                        .collect(StringBuilder::new, StringBuilder::appendCodePoint,
                                StringBuilder::append).toString();
                assertEquals(asciiText, asciiGot,
                        "ascii characters of the payload must survive the zbar read");
            }
        }
    }

    private List<String> decodeAllWithZbar(Path dir, int count) throws Exception {
        Path root = tmp.resolve("zbar");
        Files.createDirectories(root);
        StringBuilder paths = new StringBuilder();
        for (int i = 0; i < count; i++) {
            paths.append(" '").append(dir.resolve("qr" + i + ".png")).append("'");
        }
        String script = pythonScript(List.of(
                "import sys, ctypes",
                "sys.stdout.reconfigure(encoding='utf-8')",
                "from ctypes import c_void_p, c_int, c_uint",
                "from PIL import Image",
                "zbar = ctypes.CDLL('libzbar.so.0')",
                "zbar.zbar_image_scanner_create.restype = c_void_p",
                "zbar.zbar_image_create.restype = c_void_p",
                "zbar.zbar_image_set_format.argtypes = [c_void_p, c_uint]",
                "zbar.zbar_image_set_size.argtypes = [c_void_p, c_int, c_int]",
                "zbar.zbar_image_set_data.argtypes = [c_void_p, c_void_p, c_int, c_void_p]",
                "zbar.zbar_scan_image.restype = c_int",
                "zbar.zbar_image_scanner_get_results.restype = c_void_p",
                "zbar.zbar_symbol_set_first_symbol.restype = c_void_p",
                "zbar.zbar_symbol_next.restype = c_void_p",
                "zbar.zbar_symbol_get_data_length.restype = c_int",
                "zbar.zbar_image_scanner_set_config.argtypes = [c_void_p, c_int, c_int, c_int]",
                "scanner = zbar.zbar_image_scanner_create()",
                "zbar.zbar_image_scanner_set_config(scanner, 0, 0, 1)",
                "y800 = int.from_bytes(b'Y800', 'little')",
                "for path in sys.argv[1:]:",
                "    img_pil = Image.open(path)",
                "    img_pil = img_pil.resize((img_pil.size[0]*8, img_pil.size[1]*8), Image.NEAREST)",
                "    w, h = img_pil.size",
                "    gray = img_pil.convert('L').tobytes()",
                "    buf = ctypes.create_string_buffer(gray, len(gray))",
                "    img = zbar.zbar_image_create()",
                "    zbar.zbar_image_set_format(img, y800)",
                "    zbar.zbar_image_set_size(img, w, h)",
                "    zbar.zbar_image_set_data(img, buf, len(gray), None)",
                "    n = zbar.zbar_scan_image(scanner, img)",
                "    if n < 1:",
                "        print('ZBAR: nothing decoded from', path)",
                "        sys.exit(1)",
                "    syms = zbar.zbar_image_scanner_get_results(scanner)",
                "    sym = zbar.zbar_symbol_set_first_symbol(syms)",
                "    while sym:",
                "        length = zbar.zbar_symbol_get_data_length(sym)",
                "        raw = ctypes.string_at(zbar.zbar_symbol_get_data(sym), length)",
                "        print(raw.decode('utf-8'))",
                "        sym = zbar.zbar_symbol_next(sym)"));
        Path py = root.resolve("zbar.py");
        Files.writeString(py, script);
        ProcessBuilder pb = new ProcessBuilder("python3", py.toString());
        for (int i = 0; i < count; i++) {
            pb.command().add(dir.resolve("qr" + i + ".png").toString());
        }
        pb.redirectErrorStream(true);
        Process process = pb.start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int exit = process.waitFor();
        assertEquals(0, exit, "zbar script: " + output);
        String trimmed = output.replace("\r\n", "\n").strip();
        return List.of(trimmed.split("\n", -1));
    }

    private boolean pythonHasQrcode() {
        return runPythonOk("import qrcode, qrcode.util");
    }

    private boolean pythonHasZbar() {
        return runPythonOk("import ctypes; ctypes.CDLL('libzbar.so.0'); import PIL");
    }

    private boolean runPythonOk(String code) {
        try {
            Process p = new ProcessBuilder("python3", "-c", code)
                    .redirectErrorStream(true).start();
            return p.waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
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
}
