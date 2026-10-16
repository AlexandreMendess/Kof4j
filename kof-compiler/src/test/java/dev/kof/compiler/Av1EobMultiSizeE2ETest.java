package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Coverage for the AV1 EOB multi-size class ({@code libs/image/Av1Coeffs.kf},
 * {@code Av1Coeffs.eobMultiSize}) — previously UNTESTED. The value selects WHICH
 * and HOW MANY symbols the {@code eob_pt} read consumes (5..11 symbols, via the
 * {@code eob_flag_cdfNN} row), so a wrong entry for one transform size desyncs
 * every block of that size — the open {@code 3bd} symptom.
 *
 * <p>The golden is libaom's own table, fetched from the source rather than
 * recalled: {@code av1/common/common_data.h}, {@code txsize_log2_minus4
 * [TX_SIZES_ALL]} (the spec's §9.3.3 {@code txsize_log2_minus4}) —
 * {@code {0,2,4,6,6, 1,1,3,3,5,5,6,6, 2,2,4,4, 5,5}} in the SPEC's TX-size
 * order (each rectangle paired with its transpose). Kof's formula
 * ({@code 17||18 -> 5} for TX_16X64/TX_64X16, {@code TX_SIZE_SQR_UP == 4 -> 6},
 * else {@code min(wl,5)+min(hl,5)-4}) must reproduce all nineteen entries.
 */
class Av1EobMultiSizeE2ETest implements LibraryInstallSupport {

    @Override
    public String libraryName() { return "image"; }

    @Override
    public List<String> libraryMarkers() { return List.of("Av1Symbol.kf"); }

    /** libaom `txsize_log2_minus4[TX_SIZES_ALL]`, spec TX-size order. */
    private static final int[] LIBAOM = {
        0, 2, 4, 6, 6, 1, 1, 3, 3, 5, 5, 6, 6, 2, 2, 4, 4, 5, 5,
    };

    private final CompilerDriver driver = new CompilerDriver();

    @TempDir Path tmp;

    private static String golden() {
        var sb = new StringBuilder();
        for (var i = 0; i < LIBAOM.length; i++) {
            if (i > 0) sb.append('\n');
            sb.append("multi ").append(i).append(' ').append(LIBAOM[i]);
        }
        return sb.toString();
    }

    @Test
    void av1EobMultiSizeMatchesLibaomOnJvm() throws Exception {
        assertEquals(golden(), runJvm(probe()));
    }

    @Test
    void av1EobMultiSizeMatchesLibaomOnScript() throws Exception {
        Path root = tmp.resolve("script");
        Files.createDirectories(root);
        Files.writeString(root.resolve("Main.kf"), probe());
        KofInterpreter.Result result = withLibrary(root,
                () -> driver.interpret(List.of(root.resolve("Main.kf")), root, new String[0]));
        assertEquals(0, result.exitCode(), "script output: " + result.stdout());
        assertEquals(golden(), result.stdout().strip());
    }

    /** Reads `eobMultiSize(i)` for every transform size off a live instance. */
    private static String probe() {
        return """
import image.Av1Coeffs
import image.Av1Symbol

main() {
    var sym = Av1Symbol(new Int[8])
    var store = Av1CoeffCdfStore(0)
    var i = 0
    while (i < 19) {
        var c = Av1Coeffs(sym, store, i, 0, 0)
        println("multi " + i + " " + c.eobMultiSize(i))
        i = i + 1
    }
}
""";
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
