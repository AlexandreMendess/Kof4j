package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AVIF slice 3ay — the container/header/tile-group walk over a REAL AVIF file.
 *
 * <p>Every AVIF fixture in the suite before this one is SYNTHESISED by the test
 * (hand-built {@code ftyp}/{@code meta}/{@code mdat} boxes). This one is a real
 * encoder's output: {@code resources/avif_real_64.avif} was produced by
 * ffmpeg's {@code libaom-av1} encoder through the {@code avif} muxer
 * ({@code ffmpeg -f lavfi -i testsrc=size=64x64 -frames:v 1 -pix_fmt yuv420p
 * -c:v libaom-av1 -crlf 40 -cpu-used 8 -still-picture 1 -f avif}). It pins the
 * whole read path — metadata, the frame header (including the slice-3ax
 * {@code lossless}/{@code txModeSelect}/{@code reducedTxSet} facts), and the
 * tile-group walk with its per-tile payload — against bytes the Kof code did
 * not write. The reconstruction of the tile itself is the NEXT slice; the
 * decode-only probe below stops at the payload so this test never claims a
 * pixel result it does not check.
 */
class AvifRealFileE2ETest implements LibraryInstallSupport {

    @Override
    public String libraryName() { return "image"; }

    @Override
    public List<String> libraryMarkers() { return List.of("Av1Symbol.kf"); }

    private final CompilerDriver driver = new CompilerDriver();

    @TempDir Path tmp;

    /**
     * The real-file facts, pinned. {@code meta} = the {@code ispe}/brand row,
     * {@code hdr} = the frame header (with the 3ax facts), {@code groups} = the
     * tile-group walk, {@code payload} = the first tile's byte length and head.
     */
    private static final String GOLDEN =
            "meta 64 64 avif 1 false false\n"
          + "hdr t=0 w=64 h=64 tiles=1x1 hb=7 bq=160 ll=0 tx=1 rts=0\n"
          + "groups n=1 start=0 end=0 count=1\n"
          + "payload n=1 len=293 head=220 133 40 245 33 125 18 124 164 116 147 164";

    private static String probe(String path) {
        return """
import image.Avif
import image.AvifFrame
import image.AvifGroup

main() {
    var path = "%s"
    var md = readAvifMetadata(path)
    println("meta " + md.width + " " + md.height + " " + md.brand + " " + md.primaryId
        + " " + md.monochrome + " " + md.hasAlpha)
    var h = readAvifFrameHeader(path)
    println("hdr t=" + h.frameType + " w=" + h.codedWidth + " h=" + h.codedHeight
        + " tiles=" + h.tileColumns + "x" + h.tileRows + " hb=" + h.headerBytes
        + " bq=" + h.baseQIdx + " ll=" + h.lossless + " tx=" + h.txModeSelect
        + " rts=" + h.reducedTxSet)
    var groups = readAvifTileGroups(path)
    var g = groups.get(0)
    println("groups n=" + groups.size + " start=" + g.tgStart + " end=" + g.tgEnd
        + " count=" + g.tileCount)
    var payloads = readAvifTilePayloads(path)
    var p = payloads.get(0)
    var head = ""
    var k = 0
    while (k < p.size && k < 12) {
        head = head + p[k]
        if (k < 11) { head = head + " " }
        k = k + 1
    }
    println("payload n=" + payloads.size + " len=" + p.size + " head=" + head)
}
""".formatted(path);
    }

    /** Copy the fixture to a temp dir so the absolute path is stable per run. */
    private Path fixture() throws Exception {
        Path f = tmp.resolve("real.avif");
        Files.copy(Path.of("src/test/resources/avif_real_64.avif"), f);
        return f;
    }

    private String runJvm(String code) throws Exception {
        Path root = tmp.resolve("jvm-" + Math.abs(code.hashCode()));
        Files.createDirectories(root);
        Files.writeString(root.resolve("Main.kf"), code);
        Path out = root.resolve("out");
        CompilationResult result = withLibrary(root, () -> driver.compile(root.resolve("Main.kf"), out, Target.JVM));
        assertTrue(result.success(), () -> "compile: " + result.diagnostics().getDiagnostics());
        var stdout = new ByteArrayOutputStream();
        var previousOut = System.out;
        try {
            System.setOut(new java.io.PrintStream(stdout, true, StandardCharsets.UTF_8));
            try (var loader = new java.net.URLClassLoader(new java.net.URL[]{out.toUri().toURL()}, getClass().getClassLoader())) {
                Class.forName("Default.Main", true, loader).getMethod("main", String[].class).invoke(null, (Object) new String[0]);
            }
        } finally {
            System.setOut(previousOut);
        }
        return stdout.toString(StandardCharsets.UTF_8).strip();
    }

    @Test
    void realAvifWalkOnJvm() throws Exception {
        assertEquals(GOLDEN, runJvm(probe(fixture().toString())));
    }

    @Test
    void realAvifWalkOnScript() throws Exception {
        Path root = tmp.resolve("script");
        Files.createDirectories(root);
        Files.writeString(root.resolve("Main.kf"), probe(fixture().toString()));
        KofInterpreter.Result result = withLibrary(root,
                () -> driver.interpret(List.of(root.resolve("Main.kf")), root, new String[0]));
        assertEquals(0, result.exitCode(), "script output: " + result.stdout());
        assertEquals(GOLDEN, result.stdout().strip());
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
