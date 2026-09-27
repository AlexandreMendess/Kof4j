package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * #464 — isolamento do compound-assignment de Float em precisão simples
 * (a face que o tradutor aarch64 rodava como double: `fadd.s` → `fadd d0`
 * + `fmov w9, s0` = NaN; raiz corrigida em §342/f9119550 — este arquivo é
 * a PROVA pedida pela issue, que faltava: o repro exato com `Float` NU e a
 * matriz Q3 completa, cada linha do programa sozinha exercita UM caminho
 * do lowering). O §342 provava o `+=` dentro de um programa misto; aqui
 * cada operador composto (`+= -= *= /=`), `Float` local de outro `Float`
 * (pressão de registradores), `+=` em for, três `+=` sequenciais, o gêmeo
 * `Float? +=` (nu e em loop) e a mistura Double/Float são travados
 * individualmente contra o oráculo JVM. Riscv64/aarch64 consomem
 * PROGRAM/GOLDEN (parity rule 5): riscv já era verde — espelho = trava de
 * paridade; aarch64 era o alvo vermelho.
 */
class FloatCompoundAssignE2ETest {

    // Each line below is one #464 isolation row / Q3 edge (see class doc).
    static final String PROGRAM = """
            main() {
                Float x = 2.5
                x += 1.0
                println(x)
                Float s = 5.0
                s -= 1.5
                println(s)
                Float m = 2.5
                m *= 2.0
                println(m)
                Float d = 5.0
                d /= 2.0
                println(d)
                Float a = 1.5
                Float b = 2.25
                a += b
                b += a
                println(a)
                println(b)
                Float acc = 0.0
                for (var i = 0; i < 4; i++) { acc += 0.5 }
                println(acc)
                Float q = 1.0
                q += 1.0
                q += 1.0
                q += 1.0
                println(q)
                Float? n = 2.5
                n += 1.0
                println(n)
                Float? r2 = 1.0
                r2 += 2.0
                r2 += 3.0
                println(r2)
                Float? lp = 0.25
                for (var i = 0; i < 3; i++) { lp += 0.25 }
                println(lp)
                var dd = 2.5
                Float f2 = 1.0
                dd += 1.0
                f2 += 2.0
                f2 *= dd
                println(f2)
            }
            """;

    // Golden MEDIDO no oráculo JVM (java -cp <jvmout> Default.Main), 20/09.
    static final String GOLDEN =
            "3.5\n3.5\n5.0\n2.5\n3.75\n6.0\n2.0\n4.0\n3.5\n6.0\n1.0\n10.5";

    private final CompilerDriver driver = new CompilerDriver();

    @Test
    void floatCompoundAssignMatrixOnJvm(@TempDir Path tempDir) throws Exception {
        Path source = tempDir.resolve("F.kf");
        Files.writeString(source, PROGRAM);
        Path outDir = tempDir.resolve("outJVM");
        CompilationResult r = driver.compile(source, outDir, Target.JVM);
        assertTrue(r.success(), "must compile: " + r.diagnostics().getDiagnostics());
        String javaCmd = TestJdk.javaBin();
        Process p = new ProcessBuilder(javaCmd, "-cp", outDir.toString(), "Default.Main")
                .redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes()).replace("\r\n", "\n").trim();
        assertEquals(0, p.waitFor(), "JVM run must exit 0, got:\n" + out);
        assertEquals(GOLDEN, out, "JVM oracle golden (medido)");
    }

    @Test
    void floatCompoundAssignMatrixOnNativeX86(@TempDir Path tempDir) throws Exception {
        Path source = tempDir.resolve("N.kf");
        Files.writeString(source, PROGRAM);
        Path outDir = tempDir.resolve("outN");
        CompilationResult r = driver.compile(source, outDir, Target.NATIVE);
        assertTrue(r.success(), "native x86_64 compile: " + r.diagnostics().getDiagnostics());
        Path bin = outDir.resolve("Default/Main");
        assertTrue(Files.exists(bin), "binary should exist");
        Process p = new ProcessBuilder(bin.toString()).redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes()).replace("\r\n", "\n").trim();
        assertEquals(0, p.waitFor(), "native x86_64 run must exit 0, got:\n" + out);
        assertEquals(GOLDEN, out, "native x86_64 output must match the JVM oracle (rule 5)");
    }
}
