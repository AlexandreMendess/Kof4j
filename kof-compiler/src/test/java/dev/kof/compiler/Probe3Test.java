package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class Probe3Test {
    @TempDir Path tmp;
    private final CompilerDriver driver = new CompilerDriver();

    private String run(String tag, String src) throws Exception {
        Path f = tmp.resolve(tag + "/Main.kf");
        Files.createDirectories(f.getParent());
        Files.writeString(f, src);
        Path out = Path.of("/tmp/rtprobe/w3-" + tag);
        var r = driver.compile(f, out, Target.JVM);
        if (!r.success()) return "COMPILE-FAIL: " + r.diagnostics().getDiagnostics();
        String cp = out + File.pathSeparator + findH2();
        Process p = new ProcessBuilder("java", "-Dfile.encoding=UTF-8", "-cp", cp, "Default.Main")
                .redirectErrorStream(true).start();
        String o = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        return "ec=" + p.waitFor() + " out=[" + o + "]";
    }

    private String findH2() {
        for (String e : System.getProperty("java.class.path").split(File.pathSeparator))
            if (e.contains("h2") && e.endsWith(".jar")) return e;
        throw new IllegalStateException("no h2");
    }

    @Test void bisect() throws Exception {
        // (a) record não-injetado com componentes nullable + lista
        System.out.println("A local-list-nullable-record: " + run("a", """
            record Par(String? previous, String? current)
            main() {
                var cs = listOf(Par("a", "b"))
                println(cs.size)
            }
            """));
        // (b) o record injetado FieldChange, sem lista
        System.out.println("B injected-record-member: " + run("b", """
            import kof.orm.history
            main() {
                var f = FieldChange(1, "x", "a", "b")
                println(f.field)
            }
            """));
        // (c) lista do record injetado (o repro do §676 sem ORM)
        System.out.println("C injected-list-size: " + run("c", """
            import kof.orm.history
            main() {
                var cs = listOf(FieldChange(1, "x", "a", "b"))
                println(cs.size)
            }
            """));
    }
}
