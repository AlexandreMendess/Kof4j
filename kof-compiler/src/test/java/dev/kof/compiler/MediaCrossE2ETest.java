package dev.kof.compiler;

import java.io.ByteArrayOutputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * D-FULL-PARITY-050 linha 4 FATIA 2A (26/09): faces Video do kof.media no
 * cross riscv64/aarch64 — mesma golden byte a byte do JVM oracle que
 * {@code MediaNativeE2ETest} cobra no x86, agora contra os ELF cruzados sob
 * qemu (regra 5: paridade cross-target). O runtime portado vive em
 * {@code NativeRiscvAsmMedia}/{@code NativeRiscvAsmMediaMp4}; aarch64 herda
 * do riscv pelo tradutor — por isso o E2E roda nos dois arcos. Audio/Image/
 * Mic seguem MEDIA001 declarado (fatias seguintes; R6 com codigo, nao
 * silencio). Toolchain/qemu ausentes = skip honesto (guarda da familia
 * {@code NativeRiscv64E2ETest}); a CI ubuntu executa.
 */
class MediaCrossE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    @TempDir Path tmp;

    // ── fixtures: MESMOS bytes do oracle x86 (nunca por memoria) ──────────

    private static int be32(byte[] b, int off, long v) {
        b[off] = (byte) (v >> 24); b[off + 1] = (byte) (v >> 16);
        b[off + 2] = (byte) (v >> 8); b[off + 3] = (byte) v;
        return off + 4;
    }

    private static void type4(byte[] b, int off, String t) {
        for (int i = 0; i < 4; i++) b[off + i] = (byte) t.charAt(i);
    }

    private static byte[] clipMp4() {
        byte[] b = new byte[20 + 32 + 108];
        be32(b, 0, 20); type4(b, 4, "ftyp"); type4(b, 8, "isom");
        be32(b, 20, 1); type4(b, 24, "free"); be32(b, 28, 0); be32(b, 32, 32);
        b[36] = (byte) 0xB8; b[37] = (byte) 0xFE; b[38] = 0x01; b[39] = (byte) 0x80;
        be32(b, 52, 108); type4(b, 56, "moov");
        be32(b, 60, 100); type4(b, 64, "mvhd");
        b[68] = 0;
        be32(b, 80, 1000);
        be32(b, 84, 3000);
        return b;
    }

    private static byte[] clipZeroSizeMp4() {
        byte[] b = new byte[16 + 108];
        be32(b, 0, 0); type4(b, 4, "free");
        be32(b, 16, 108); type4(b, 20, "moov");
        be32(b, 24, 100); type4(b, 28, "mvhd");
        be32(b, 44, 1000); be32(b, 48, 3000);
        return b;
    }

    // ── runners ────────────────────────────────────────────────────────────

    private String runJvm(Path src, Path out) throws Exception {
        CompilationResult r = driver.compile(src, out, Target.JVM);
        assertTrue(r.success(), "JVM must compile: " + r.diagnostics().getDiagnostics());
        var buf = new ByteArrayOutputStream();
        var old = System.out;
        System.setOut(new java.io.PrintStream(buf, true, StandardCharsets.UTF_8));
        try {
            ClassLoader cl = new URLClassLoader(new URL[]{out.toUri().toURL()}, getClass().getClassLoader());
            Class.forName("Default.Main", true, cl)
                    .getMethod("main", String[].class).invoke(null, (Object) new String[0]);
        } finally {
            System.setOut(old);
        }
        return buf.toString(StandardCharsets.UTF_8);
    }

    private String runCross(Target target, String arch, Path src, Path out) throws Exception {
        CompilationResult r = driver.compile(src, out, target);
        assertTrue(r.success(), arch + " must compile: " + r.diagnostics().getDiagnostics());
        Path bin = out.resolve("Default/Main");
        assertTrue(Files.exists(bin), arch + " binary should exist");
        ProcessBuilder pb = NativeRiscv64E2ETest.qemu(arch, bin);
        pb.directory(tmp.toFile());
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String s = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(p.waitFor(180, TimeUnit.SECONDS), arch + " qemu timeout");
        assertEquals(0, p.exitValue(), arch + " binary exit " + p.exitValue() + ":\n" + s);
        return s;
    }

    // ── golden byte a byte: JVM == riscv64 == aarch64 ─────────────────────

    @Test
    void videoFacesMatchJvmGoldenOnCross() throws Exception {
        assumeTrue(NativeRiscv64E2ETest.hasToolchain("riscv64"),
                "cross toolchain riscv64 + qemu ausente — pulando (NATIVE002)");
        Files.write(tmp.resolve("clip.mp4"), clipMp4());
        Files.write(tmp.resolve("clip0.mp4"), clipZeroSizeMp4());
        String t = tmp.toString().replace('\\', '/');
        Files.writeString(tmp.resolve("XMED.kf"), """
                main() {
                    var v = Video.open("%1$s/clip.mp4")
                    println(v.format())
                    println(v.durationMs())
                    println(v.size())
                    println(v.bytes())
                    println(v.path())
                    v.close()
                    try { v.size() } catch (String e) { println(e) }
                    try { Video.open("%1$s/nope.mp4") } catch (String e) { println(e) }
                    var z = Video.open("%1$s/clip0.mp4")
                    println(z.durationMs())
                    z.close()
                }
                """.formatted(t));
        String jvm = runJvm(tmp.resolve("XMED.kf"), tmp.resolve("o-xmed-jvm"));
        String riscv = runCross(Target.NATIVE_RISCV64, "riscv64",
                tmp.resolve("XMED.kf"), tmp.resolve("o-xmed-rv"));
        assertEquals(jvm, riscv, "Video riscv64 == oraculo JVM byte a byte (regra 5)");
        assumeTrue(NativeRiscv64E2ETest.hasToolchain("aarch64"),
                "cross toolchain aarch64 + qemu ausente — riscv ja provado");
        String aarch = runCross(Target.NATIVE_AARCH64, "aarch64",
                tmp.resolve("XMED.kf"), tmp.resolve("o-xmed-aa"));
        assertEquals(jvm, aarch, "Video aarch64 == oraculo JVM byte a byte (regra 5)");
    }

    // ── Audio (WAV PCM 16-bit) — fixtures MESMOS bytes do oracle x86 ──────

    private static void le16(byte[] b, int off, int v) {
        b[off] = (byte) v; b[off + 1] = (byte) (v >> 8);
    }

    private static void le32(byte[] b, int off, int v) {
        le16(b, off, v & 0xFFFF); le16(b, off + 2, v >> 16);
    }

    private static byte[] wav(int format, int channels, int rate, int bits, byte[] data) {
        byte[] b = new byte[44 + data.length];
        type4(b, 0, "RIFF"); le32(b, 4, 36 + data.length); type4(b, 8, "WAVE");
        type4(b, 12, "fmt "); le32(b, 16, 16);
        le16(b, 20, format); le16(b, 22, channels); le32(b, 24, rate);
        le32(b, 28, rate * channels * 2); le16(b, 32, channels * 2); le16(b, 34, bits);
        type4(b, 36, "data"); le32(b, 40, data.length);
        System.arraycopy(data, 0, b, 44, data.length);
        return b;
    }

    @Test
    void audioFacesMatchJvmGoldenOnCross() throws Exception {
        assumeTrue(NativeRiscv64E2ETest.hasToolchain("riscv64"),
                "cross toolchain riscv64 + qemu ausente — pulando (NATIVE002)");
        byte[] pcm = {0x12, 0x34, (byte) 0xDC, (byte) 0xFE, 0, 0,
                (byte) 0xFF, 0x7F, 0, (byte) 0x80, (byte) 0xFF, 0,
                (byte) 0x80, 0, 0, 0x01};
        byte[] note = wav(1, 1, 8000, 16, pcm);
        Files.write(tmp.resolve("note.wav"), note);
        Files.write(tmp.resolve("float.wav"), wav(3, 1, 8000, 16, pcm));
        Files.write(tmp.resolve("pcm24.wav"), wav(1, 1, 8000, 24, pcm));
        Files.write(tmp.resolve("bad.wav"), "not riff at all......".getBytes(StandardCharsets.US_ASCII));
        String t = tmp.toString().replace('\\', '/');
        Files.writeString(tmp.resolve("XAUD.kf"), """
                main() {
                    var a = Audio.openWav("%1$s/note.wav")
                    println(a.sampleRate())
                    println(a.durationMs())
                    println(a.pcmBytes())
                    println(a.saveWav("%1$s/nx/out.wav"))
                    try { Audio.openWav("%1$s/float.wav") } catch (String e) { println(e) }
                    try { Audio.openWav("%1$s/pcm24.wav") } catch (String e) { println(e) }
                    try { Audio.openWav("%1$s/bad.wav") } catch (String e) { println(e) }
                    var n = Audio.openWav("%1$s/note.wav")
                    println(n.saveWav("%1$s/nx/deeper/out2.wav"))
                    println(n.durationMs())
                }
                """.formatted(t));

        String jvm = runJvm(tmp.resolve("XAUD.kf"), tmp.resolve("o-xaud-jvm"));
        assertArrayEquals(note, Files.readAllBytes(tmp.resolve("nx/out.wav")),
                "JVM saveWav deve reproduzir o WAV canonico byte a byte");

        String riscv = runCross(Target.NATIVE_RISCV64, "riscv64",
                tmp.resolve("XAUD.kf"), tmp.resolve("o-xaud-rv"));
        assertEquals(jvm, riscv, "Audio riscv64 == oraculo JVM byte a byte (regra 5)");
        assertArrayEquals(note, Files.readAllBytes(tmp.resolve("nx/out.wav")),
                "riscv64 saveWav bytes");
        assertArrayEquals(note, Files.readAllBytes(tmp.resolve("nx/deeper/out2.wav")),
                "riscv64 saveWav/mkdirs recursivo bytes");

        assumeTrue(NativeRiscv64E2ETest.hasToolchain("aarch64"),
                "cross toolchain aarch64 + qemu ausente — riscv ja provado");
        String aarch = runCross(Target.NATIVE_AARCH64, "aarch64",
                tmp.resolve("XAUD.kf"), tmp.resolve("o-xaud-aa"));
        assertEquals(jvm, aarch, "Audio aarch64 == oraculo JVM byte a byte (regra 5)");
        assertArrayEquals(note, Files.readAllBytes(tmp.resolve("nx/out.wav")),
                "aarch64 saveWav bytes");
        assertArrayEquals(note, Files.readAllBytes(tmp.resolve("nx/deeper/out2.wav")),
                "aarch64 saveWav/mkdirs recursivo bytes");
    }
}
