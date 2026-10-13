package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class Probe2Test {
    @TempDir Path tmp;
    private final CompilerDriver driver = new CompilerDriver();

    @Test void probeCtors() throws Exception {
        Path f = tmp.resolve("p/Main.kf");
        Files.createDirectories(f.getParent());
        Files.writeString(f, """
            import kof.orm.history
            audited entity User {
                id: Long generated
                name: String
            }
            main() {
                var db = db.connect("sqlite:/tmp/histdbg-run/probe.db")
                orm.create<User>(db)
                orm.createHistory<User>(db)
                var pg = User.history(db, 7, 0, 1)
                println(pg.size)
                db.close(db)
            }
            """);
        Path out = tmp.resolve("out");
        var r = driver.compile(f, out, Target.NATIVE);
        System.out.println("COMPILE ok=" + r.success());
        for (var d : r.diagnostics().getDiagnostics()) System.out.println("DIAG " + d);
        if (r.success()) {
            String s = Files.readString(out.resolve("Default/Main.s"));
            System.out.println("CTORS-ENTRIES: " + s.split("kof_orm_ctors:")[1].split("Lormc_miss")[0].replaceAll("\n", " | "));
            System.out.println("REVISION-CLASS: " + (s.contains("Revision") ? "yes" : "no"));
            Process p2 = new ProcessBuilder(out.resolve("Default/Main").toString()).inheritIO().start();
            System.out.println("RUN exit=" + p2.waitFor());
        }
    }
}
