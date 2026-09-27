package dev.kof.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import dev.kof.compiler.Target;
import dev.kof.compiler.nat.NativeProfile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * B-1 (PLAN-BAREMETAL-BOOT): a superfície CLI {@code kof build --profile
 * host|freestanding}. Prova que o flag chega ao {@code CompilerDriver} — o build
 * freestanding reproduz o perfil libc-free (sem {@code PT_INTERP}) — e que
 * combinações inválidas são recusadas nomeadamente (R6), nunca no-op silencioso.
 */
class CmdBuildProfileTest {

    @Test
    void parseProfileMapsHostAndFreestanding() {
        assertEquals(NativeProfile.HOST, BuildProfileFlag.parse("host"));
        assertEquals(NativeProfile.FREESTANDING, BuildProfileFlag.parse("freestanding"));
        assertNull(BuildProfileFlag.parse("resident"));
    }

    @Test
    void profileErrorRefusesNonNativeTargetsAndBadValues() {
        assertNull(BuildProfileFlag.error("build", Target.NATIVE, "host"));
        assertNull(BuildProfileFlag.error("build", Target.NATIVE, "freestanding"));
        assertTrue(BuildProfileFlag.error("build", Target.JVM, "freestanding").contains("--target native"),
                "perfil em alvo JVM deve ser recusado");
        assertTrue(BuildProfileFlag.error("build", Target.NATIVE_RISCV64, "freestanding")
                .contains("--target native"), "perfil no cross deve ser recusado");
        assertTrue(BuildProfileFlag.error("build", Target.NATIVE, "resident").contains("invalid"),
                "valor fora de host|freestanding deve ser recusado");
    }

    @Test
    void buildFreestandingReachesLinkerProfile(@TempDir Path dir) throws Exception {
        assumeTrue(hasTool("as") && hasTool("ld") && hasTool("readelf"),
                "x86 toolchain/readelf ausentes");
        Path src = Files.createDirectories(dir.resolve("src"));
        Files.writeString(src.resolve("Main.kf"), "main() { println(42) }\n");
        Path out = dir.resolve("out");
        CmdBuild.run(new String[] { "build", src.toString(),
                "--target", "native", "--profile", "freestanding", "--output", out.toString() });
        Path bin = out.resolve("Default/Main");
        assertTrue(Files.exists(bin), "binario freestanding nao gerado em " + out);

        Process rp = new ProcessBuilder("readelf", "--program-headers", bin.toString())
                .redirectErrorStream(true).start();
        String ph = new String(rp.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(rp.waitFor(30, TimeUnit.SECONDS), "readelf nao terminou");
        assertFalse(ph.contains("INTERP"), "freestanding nao deve carregar PT_INTERP:\n" + ph);

        Process p = new ProcessBuilder(bin.toString()).redirectErrorStream(true).start();
        String outText = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
        assertEquals(0, p.waitFor(), "saida: " + outText);
        assertEquals("42", outText, "hello freestanding deve imprimir 42");
    }

    @Test
    void profileHostStillBuildsNative(@TempDir Path dir) throws Exception {
        assumeTrue(hasTool("as") && hasTool("ld"), "x86 toolchain ausente");
        Path src = Files.createDirectories(dir.resolve("src"));
        Files.writeString(src.resolve("Main.kf"), "main() { println(7) }\n");
        Path out = dir.resolve("out");
        CmdBuild.run(new String[] { "build", src.toString(),
                "--target", "native", "--profile", "host", "--output", out.toString() });
        Process p = new ProcessBuilder(out.resolve("Default/Main").toString())
                .redirectErrorStream(true).start();
        String outText = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
        assertEquals(0, p.waitFor(), "saida: " + outText);
        assertEquals("7", outText, "hello host deve imprimir 7");
    }

    private static boolean hasTool(String tool) {
        try {
            Process p = new ProcessBuilder(tool, "--version").start();
            return p.waitFor(10, TimeUnit.SECONDS) && p.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }
}
