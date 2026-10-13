package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * #819 — a switch EXPRESSION whose arms return different subclasses of a
 * sealed hierarchy OTHER than the subject's compiled to invalid bytecode: the
 * sibling join was widened to java/lang/Object in the generated areturn
 * (VerifyError: Bad return type — "Object is not assignable to <supertype>")
 * while `kof check` passed. The join must be the arms' common supertype (the
 * sealed base), matching the statement-form behavior that always worked.
 */
class SealedSwitchJoinSealedHierarchyTest extends SemanticResolutionSupport {

    private static final String SRC = """
            sealed class Base
            class A(Int x) extends Base
            class B(Int x) extends Base
            sealed class Out
            class X(Int v) extends Out
            class Y(Int v) extends Out

            Out converte(Base b) {
                return switch (b) {
                    case A a -> X(a.x())
                    case B bb -> Y(bb.x())
                }
            }

            main() {
                println("x=" + converte(A(1)))
                println("y=" + converte(B(2)))
            }
            """;

    @Test
    void siblingSubclassJoinCompilesAndLoads(@TempDir Path tmp) throws Exception {
        Path source = tmp.resolve("P819.kf");
        Files.writeString(source, SRC);
        CompilationResult r = driver.compile(source, tmp.resolve("out-run"), Target.JVM);
        assertTrue(r.success(), "o join de irmãos deve ser o supertipo selado, "
                + "não Object (VerifyError pré-fix): " + r.diagnostics().getDiagnostics());
        Process p = new ProcessBuilder("java", "-cp", tmp.resolve("out-run").toString(), "Default.Main")
                .redirectErrorStream(true).start();
        String os = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        assertEquals(0, p.waitFor(), "classe deve carregar e rodar: " + os);
        assertTrue(os.contains("X") && os.contains("Y"), "converte(A)→X e converte(B)→Y: " + os);
    }
}
