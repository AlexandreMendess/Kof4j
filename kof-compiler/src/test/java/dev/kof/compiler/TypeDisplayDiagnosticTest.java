package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** Audit 0.6.0 DIAG-02: SEM012/SEM021/ctor-SEM014 interpolated the raw
 *  {@code Type.toString()} — {@code cannot assign ClassType[packageName=,
 *  name=B, typeArguments=[]] to ClassType[...]} — while the #324 contract
 *  (SEM010/SEM014-method/SEM036) already renders human-readable names via
 *  {@code Type.display(...)}. The six sites now display; this pins the
 *  user-visible guarantee: NO diagnostic message carries
 *  {@code ClassType[}/{@code PrimitiveType[}/{@code NullableType[}. */
class TypeDisplayDiagnosticTest {

    @TempDir
    Path tmp;

    private final CompilerDriver driver = new CompilerDriver();

    private String diagnosticsOf(String tag, String src) throws Exception {
        Path file = tmp.resolve(tag + "/Main.kf");
        Files.createDirectories(file.getParent());
        Files.writeString(file, src);
        return driver.compile(file, tmp.resolve("out-" + tag), Target.JVM)
                .diagnostics().getDiagnostics().toString();
    }

    private static final String RAW = "raw type representation leaked";

    @Test
    void sem012ClassAssignUsesDisplayNames() throws Exception {
        String d = diagnosticsOf("sem012", """
            class A { }
            class B { }
            main() { var a: A = A()
                     var b: B = B()
                     a = b }
            """);
        assertTrue(d.contains("SEM012"), "esperava SEM012, veio: " + d);
        assertTrue(d.contains("cannot assign B to A"), "SEM012 ilegivel: " + d);
        assertFalse(d.contains("ClassType["), RAW + ": " + d);
    }

    @Test
    void sem021VarDeclUsesDisplayNames() throws Exception {
        String d = diagnosticsOf("sem021", """
            main() { var s: String = 1 }
            """);
        assertTrue(d.contains("SEM021"), "esperava SEM021, veio: " + d);
        assertTrue(d.contains("cannot assign Int to 's: String'"), "SEM021 ilegivel: " + d);
        assertFalse(d.contains("PrimitiveType["), RAW + ": " + d);
    }

    @Test
    void ctorSem014UsesDisplayNames() throws Exception {
        String d = diagnosticsOf("ctor014", """
            class Box { Box(Int v) { } }
            main() { var b = new Box("s") }
            """);
        assertTrue(d.contains("SEM014"), "esperava SEM014, veio: " + d);
        assertFalse(d.contains("PrimitiveType[") || d.contains("ClassType["),
                RAW + ": " + d);
    }
}
