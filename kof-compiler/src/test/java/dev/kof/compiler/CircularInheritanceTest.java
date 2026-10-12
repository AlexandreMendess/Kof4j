package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** #821 — herança circular (`class A extends A`, ciclos de 2/3) travava o
 *  compilador em loop infinito (DoS, sem diagnóstico). Deve emitir um
 *  diagnóstico honesto em compile-time e nunca hangar. */
class CircularInheritanceTest extends SemanticResolutionSupport {

    private void refuses(Path tmp, String src) throws Exception {
        Path f = tmp.resolve("C821.kf");
        Files.writeString(f, src);
        CompilationResult r = driver.compile(f, tmp.resolve("out"), Target.JVM);
        assertFalse(r.success(), "herança circular deve ser recusada em compile-time");
        assertTrue(r.diagnostics().getDiagnostics().stream()
                        .anyMatch(d -> d.code() != null && d.code().startsWith("SEM")),
                "deve emitir um diagnóstico SEM nomeado: " + r.diagnostics().getDiagnostics());
    }

    @Test
    void directSelfCycleRefused(@TempDir Path tmp) throws Exception {
        refuses(tmp, "class A extends A { }\n\nmain() { }");
    }

    @Test
    void twoClassCycleRefused(@TempDir Path tmp) throws Exception {
        refuses(tmp, "class A extends B { }\nclass B extends A { }\n\nmain() { }");
    }

    @Test
    void threeClassCycleRefused(@TempDir Path tmp) throws Exception {
        refuses(tmp, "class A extends B { }\nclass B extends C { }\nclass C extends A { }\n\nmain() { }");
    }

    @Test
    void classWithoutExtendsStillCompiles(@TempDir Path tmp) throws Exception {
        // the walk feeds cls.superClass() into simpleName; a class WITHOUT an
        // explicit extends has a null super — the raw replace crashed COMP002
        // (internal error) on EVERY such class. Compiles clean now.
        Path f = tmp.resolve("C821b.kf");
        Files.writeString(f, "class Plain { }\n\nmain() { }");
        CompilationResult r = driver.compile(f, tmp.resolve("out"), Target.JVM);
        assertTrue(r.success(), "a class without extends must compile: " + r.diagnostics().getDiagnostics());
    }
}
