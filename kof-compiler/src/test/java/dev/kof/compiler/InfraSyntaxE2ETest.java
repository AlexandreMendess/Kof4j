package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * linha 3.2 do plano Makealive ({@code docs/development/makealive-plan.md});
 * {@code DECISIONS.md} §D-MAKEALIVE-SYNTAX (21/09). Prova que o bloco
 * declarativo {@code infra "prod" { ... }} é AÇÚCAR PURO: desugara em
 * {@code design(): Infrastructure} e produz bytes IDÊNTICOS ao gêmeo escrito
 * à mão — sem keyword/token/tipo/runtime novo (o golden de núcleo,
 * {@link LanguageCoreSurfaceTest}, continua verde por construção).
 */
class InfraSyntaxE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    @TempDir Path tmp;

    private record Run(boolean ok, String output) {}

    private Run runJvm(Path src, Path out) throws Exception {
        CompilationResult r = driver.compile(src, out, Target.JVM);
        if (!r.success()) return new Run(false, diags(r));
        var oldOut = System.out;
        var buf = new ByteArrayOutputStream();
        System.setOut(new java.io.PrintStream(buf, true));
        try {
            var cl = new URLClassLoader(new java.net.URL[]{out.toUri().toURL()},
                    getClass().getClassLoader());
            Class.forName("Default.Main", true, cl)
                    .getMethod("main", String[].class).invoke(null, (Object) new String[0]);
            return new Run(true, buf.toString());
        } catch (java.lang.reflect.InvocationTargetException e) {
            return new Run(false, "THROW: " + e.getCause());
        } finally {
            System.setOut(oldOut);
        }
    }

    private Run runJs(Path src, Path out) throws Exception {
        CompilationResult r = driver.compile(src, out, Target.JS);
        if (!r.success()) return new Run(false, diags(r));
        var buf = new ByteArrayOutputStream();
        try {
            int rc = dev.kof.runtime.KofJsRunner.run(
                    out.resolve("Default.mjs"), buf,
                    new ByteArrayInputStream(new byte[0]), buf);
            return new Run(rc == 0, buf.toString());
        } catch (Exception e) {
            return new Run(false, "THROW: " + e.getMessage() + "\n" + buf);
        }
    }

    private static String diags(CompilationResult r) {
        StringBuilder sb = new StringBuilder();
        r.diagnostics().getDiagnostics().forEach(d -> sb.append(d.message()).append("\n"));
        return sb.toString();
    }

    private static final String BODY = """
        var p = plan(design(), State())
        println("creates=" + p.creates.size())
        var i = 0
        while (i < p.creates.size()) {
            println(p.creates.get(i))
            i = i + 1
        }
        """;

    /** O bloco `infra` desugara em `design()`; a saída tem de bater byte a byte
     *  com o `design()` imperativo escrito à mão (JVM==JS, Native compila). */
    @Test
    void infraBlockMatchesHandWrittenDesign() throws Exception {
        String declarative = """
            import kof.makealive

            infra "prod" {
                resource("service", "web")
                prop("web", "region", "us-east")
                requires("web", "db")
                resource("db", "db")
                prop("db", "engine", "postgres")
            }

            main() {
            """ + BODY + "}\n";

        String imperative = """
            import kof.makealive

            Infrastructure design() {
                var d = Infrastructure("prod")
                d.resource("service", "web")
                d.prop("web", "region", "us-east")
                d.requires("web", "db")
                d.resource("db", "db")
                d.prop("db", "engine", "postgres")
                return d
            }

            main() {
            """ + BODY + "}\n";

        Path dec = tmp.resolve("Decl.kf");
        Path imp = tmp.resolve("Imp.kf");
        Files.writeString(dec, declarative);
        Files.writeString(imp, imperative);

        Run decJvm = runJvm(dec, tmp.resolve("o-dec-jvm"));
        assertTrue(decJvm.ok(), () -> "JVM declarativo falhou: " + decJvm.output());
        Run impJvm = runJvm(imp, tmp.resolve("o-imp-jvm"));
        assertTrue(impJvm.ok(), () -> "JVM imperativo falhou: " + impJvm.output());
        assertEquals(impJvm.output(), decJvm.output(),
                "o bloco `infra` deve produzir o MESMO design do gêmeo imperativo");

        Run decJs = runJs(dec, tmp.resolve("o-dec-js"));
        assertTrue(decJs.ok(), () -> "JS declarativo falhou: " + decJs.output());
        assertEquals(decJvm.output(), decJs.output(), "JVM/JS divergem no bloco `infra`");

        assertTrue(decJvm.output().contains("creates=2"), () -> decJvm.output());
        assertTrue(decJvm.output().contains("db"), () -> decJvm.output());
        assertTrue(decJvm.output().contains("web"), () -> decJvm.output());

        CompilationResult nativeR = driver.compile(dec, tmp.resolve("o-dec-native"), Target.NATIVE);
        assertTrue(nativeR.success(), () -> "Native deve compilar: " + diags(nativeR));
    }

    /** Corpo fora das faces do host é erro honesto (R6), nunca silêncio. */
    @Test
    void infraBodyRejectsNonBuilderStatement() throws Exception {
        String bad = """
            import kof.makealive

            infra "prod" {
                var x = 1
            }

            main() { println("nunca") }
            """;
        Path src = tmp.resolve("Bad.kf");
        Files.writeString(src, bad);
        CompilationResult r = driver.compile(src, tmp.resolve("o-bad"), Target.JVM);
        assertFalse(r.success(), "corpo inválido no `infra` deve falhar");
        assertTrue(diags(r).contains("infra body accepts only builder calls"),
                () -> "esperava o diagnóstico do corpo do `infra`: " + diags(r));
    }
}
