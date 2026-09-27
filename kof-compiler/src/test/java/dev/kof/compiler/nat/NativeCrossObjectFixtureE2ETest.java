package dev.kof.compiler.nat;

import dev.kof.compiler.CompilationResult;
import dev.kof.compiler.CompilerDriver;
import dev.kof.compiler.Target;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Fatia C4-x ({@code docs/development/kof-c-cross.md}): um `extern` cuja
 * {@code library()} é um objeto {@code .o} já montado PARA A ARCH entra
 * posicional no {@code ld} cross (preservando o path), em vez de virar
 * {@code -l:<basename>}. É o mecanismo que liga a fixture C gerada pelo
 * {@code kof-c-compiler} cross ao binário nativo — sem ele a `library()` de
 * caminho perdia a extensão .o.
 *
 * <p>Prova E2E real: a fixture é montada com o {@code as} cross e o binário Kof
 * (compilado pelo driver) chama o símbolo do objeto sob qemu. Sem toolchain →
 * skip honesto (NATIVE002).
 */
class NativeCrossObjectFixtureE2ETest {

    private static boolean has(String... cmds) {
        for (String c : cmds) {
            try {
                Process p = new ProcessBuilder("sh", "-c", "command -v " + c)
                        .redirectErrorStream(true).start();
                String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
                if (p.waitFor() != 0 || out.isEmpty()) return false;
            } catch (Exception e) {
                return false;
            }
        }
        return true;
    }

    private static final String FIXTURE_RISCV = """
            .text
            .globl add
            add:
                add a0, a0, a1
                ret
            """;

    private static final String FIXTURE_AARCH = """
            .text
            .globl add
            add:
                add x0, x0, x1
                ret
            """;

    private static void run(String... cmd) throws IOException {
        ProcessBuilder pb = new ProcessBuilder(cmd).redirectErrorStream(true);
        Process p = pb.start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        try {
            assertEquals(0, p.waitFor(), "comando falhou: " + String.join(" ", cmd) + "\n" + out);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException(e);
        }
    }

    private String compileAndRun(String arch, Target target, String fixtureAsm,
                                 String fixtureAs, Path tmp) throws IOException {
        Path s = tmp.resolve("fixture.s");
        Path o = tmp.resolve("fixture.o");
        Files.writeString(s, fixtureAsm);
        run(fixtureAs, "-o", o.toString(), s.toString());

        Path src = tmp.resolve("Main.kf");
        Files.writeString(src, """
                extern "%s" add(Int a, Int b): Int
                main() { println(add(20, 22)) }
                """.formatted(o.toString()));

        CompilationResult r = new CompilerDriver().compile(src, tmp.resolve("out"), target);
        assertTrue(r.success(), "compile " + arch + " com fixture .o: " + r.diagnostics().getDiagnostics());
        Path bin = tmp.resolve("out/Default/Main");
        assertTrue(Files.exists(bin), "binário ausente em " + arch);

        ProcessBuilder pb = new ProcessBuilder("qemu-" + arch, bin.toString()).redirectErrorStream(true);
        pb.environment().put("QEMU_LD_PREFIX", NativeCrossLink.qemuPrefixFor(arch));
        Process p = pb.start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
        try {
            assertEquals(0, p.waitFor(), arch + " exit != 0: '" + out + "'");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException(e);
        }
        return out;
    }

    @Test
    void riscv64LinksObjectFixtureViaLibraryPath(@TempDir Path tmp) throws IOException {
        Assumptions.assumeTrue(has("riscv64-linux-gnu-as", "riscv64-linux-gnu-ld", "qemu-riscv64"),
                "toolchain riscv64 ausente — pulando (NATIVE002)");
        Assumptions.assumeTrue(NativeCrossLink.sysrootFor("riscv64") != null,
                "libc riscv64-cross ausente (KOF_CROSS_SYSROOT) — pulando");
        assertEquals("42", compileAndRun("riscv64", Target.NATIVE_RISCV64,
                FIXTURE_RISCV, "riscv64-linux-gnu-as", tmp));
    }

    @Test
    void aarch64LinksObjectFixtureViaLibraryPath(@TempDir Path tmp) throws IOException {
        Assumptions.assumeTrue(has("aarch64-linux-gnu-as", "aarch64-linux-gnu-ld", "qemu-aarch64"),
                "toolchain aarch64 ausente — pulando (NATIVE002)");
        Assumptions.assumeTrue(NativeCrossLink.sysrootFor("aarch64") != null,
                "libc aarch64-cross ausente (KOF_CROSS_SYSROOT) — pulando");
        assertEquals("42", compileAndRun("aarch64", Target.NATIVE_AARCH64,
                FIXTURE_AARCH, "aarch64-linux-gnu-as", tmp));
    }

    @Test
    void ldArgsPlaceObjectFixtureBeforeLibs() {
        // ordem: objeto principal, -lc, depois a fixture posicional — o ld
        // resolve o símbolo porque objetos são sempre incluídos.
        String[] a = NativeCrossLink.ldArgs("riscv64-linux-gnu-ld", Path.of("/tmp/b"),
                Path.of("/tmp/b.o"), "riscv64", true, "/s", false, List.of("/tmp/fixture.o"));
        List<String> l = List.of(a);
        assertTrue(l.contains("/tmp/fixture.o"), "fixture deve entrar posicional: " + l);
        assertTrue(l.contains("-lc"), "ffi força dinâmico com -lc: " + l);
    }
}
