package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * §388-A — faces de bytes do `kof.io`: {@code File.writeBytes(listOf(...))}
 * compilava GREEN e morria no runtime (JVM {@code VerifyError} na trilha de
 * reflection do Run; Script rc=1 silencioso; JS escrevia os bytes mas
 * devolvia o truthy errado). O contrato (training/language/io.md,
 * {@code KofIo.instanceMethod}) é {@code Int[]}; receber {@code List<Int>}
 * é violação de contrato aceita em silêncio = R6 quebrado. Este teste fixa
 * o SEM099 (família SEM098/§357, mas SEM GATE DE ALVO: todos os targets
 * quebram no runtime, nenhum behavior que rodava é cortado — regra 2 do
 * freeze não protege o que já morria).
 */
class IoArrayArgE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    private Path write(Path tmp, String src) throws IOException {
        Path f = tmp.resolve("Main-" + System.nanoTime() + ".kf");
        Files.writeString(f, src);
        return f;
    }

    private static final String LIST_WRITE = """
            main() {
                val g = File("b.bin")
                println(g.writeBytes(listOf(65, 66)))
            }
            """;

    @Test
    void writeBytesListArgIsSem099OnJvm(@TempDir Path tmp) throws IOException {
        CompilationResult r = driver.compile(write(tmp, LIST_WRITE), tmp.resolve("out-list"), Target.JVM);
        assertFalse(r.success(), "List<Int> em parâmetro Int[] deve ser rejeitado no JVM: "
                + r.diagnostics().getDiagnostics());
        assertTrue(r.diagnostics().getDiagnostics().stream()
                        .anyMatch(d -> "SEM099".equals(d.code())),
                "esperava SEM099, foi: " + r.diagnostics().getDiagnostics());
    }

    @Test
    void appendBytesBoundListVarIsSem099OnJvm(@TempDir Path tmp) throws IOException {
        CompilationResult r = driver.compile(write(tmp, """
                main() {
                    val xs = listOf(65, 66)
                    val g = File("b.bin")
                    println(g.appendBytes(xs))
                }
                """), tmp.resolve("out-var"), Target.JVM);
        assertFalse(r.success(), "var List<Int> também é violação de Int[]: "
                + r.diagnostics().getDiagnostics());
        assertTrue(r.diagnostics().getDiagnostics().stream()
                        .anyMatch(d -> "SEM099".equals(d.code())),
                "esperava SEM099, foi: " + r.diagnostics().getDiagnostics());
    }

    @Test
    void writeBytesListArgIsSem099OnJs(@TempDir Path tmp) throws IOException {
        CompilationResult r = driver.compile(write(tmp, LIST_WRITE), tmp.resolve("out-js"), Target.JS);
        assertFalse(r.success(), "JS escrevia os bytes com truthy errado — também é runtime quebrado: "
                + r.diagnostics().getDiagnostics());
        assertTrue(r.diagnostics().getDiagnostics().stream()
                        .anyMatch(d -> "SEM099".equals(d.code())),
                "esperava SEM099 no JS, foi: " + r.diagnostics().getDiagnostics());
    }

    @Test
    void writeBytesListArgFailsHonestlyOnScript(@TempDir Path tmp) throws IOException {
        KofInterpretException ex = assertThrows(KofInterpretException.class, () ->
                driver.interpret(java.util.List.of(write(tmp, LIST_WRITE)), tmp, new String[0]),
                "Script tinha rc=1 mudo; agora precisa falhar cedo");
        assertTrue(ex.getMessage().contains("SEM099"),
                "mensagem do interpret sem o código: " + ex.getMessage());
    }

    @Test
    void realIntArrayStillWorksOnJvm(@TempDir Path tmp) throws IOException {
        Path file = write(tmp, """
                main() {
                    val arr = new Int[2]
                    arr[0] = 65
                    arr[1] = 66
                    println(File("a.bin").writeBytes(arr))
                }
                """);
        Path outDir = tmp.resolve("out-ok");
        CompilationResult r = driver.compile(file, outDir, Target.JVM);
        assertTrue(r.success(), "workaround documentado (io.md) não pode regredir: "
                + r.diagnostics().getDiagnostics());
    }
}
