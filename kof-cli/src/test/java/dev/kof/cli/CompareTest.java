package dev.kof.cli;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * kof compare — differential testing (docs/development/LEGACY_MIGRATION.md §8).
 * Runs legacy (.class) and Kof (.kf) with identical inputs and compares
 * stdout/exit code/stderr.
 */
class CompareTest extends CliJavacSupport {

    @Test
    void helpWorks() {
        assertEquals(0, Compare.run(new String[]{"compare", "--help"}));
    }

    @Test
    void pureComparisonLogic() {
        Compare.RunResult a = new Compare.RunResult(0, "hello\n", "", Map.of());
        Compare.RunResult b = new Compare.RunResult(0, "hello\n", "", Map.of());
        Compare.Verdict v = Compare.compare(a, b);
        assertTrue(v.equivalent());

        Compare.Verdict diverged = Compare.compare(a, new Compare.RunResult(0, "world\n", "", Map.of()));
        assertFalse(diverged.equivalent());
        assertEquals(Compare.Channel.DIVERGENT, diverged.stdout());
        assertEquals(Compare.Channel.EQUIVALENT, diverged.exit());
    }

    @Test
    void typedExceptionCatchesFalseGreen() {
        // §8 Fase G RED-first: both sides exit 1 with empty stdout, but the
        // failure MODE diverges (NPE vs ArrayIndexOO). The old verdict saw
        // 1 == 1 and called it equivalent — a false green. The typed-exception
        // channel must flip it to divergent.
        Compare.RunResult legacy = new Compare.RunResult(1, "",
                "Exception in thread \"main\" java.lang.NullPointerException\n\tat App.main(App.java:7)", Map.of());
        Compare.RunResult kof = new Compare.RunResult(1, "",
                "Exception in thread \"main\" java.lang.ArrayIndexOutOfBoundsException: Index 0\n\tat App.main(App.kf:9)", Map.of());
        Compare.Verdict v = Compare.compare(legacy, kof);
        assertEquals(Compare.Channel.DIVERGENT, v.exceptions(),
                "different exception types = divergent failure mode");
        assertFalse(v.equivalent(), "the false green must be closed");
    }

    @Test
    void typedExceptionSameTypeEquivalentDespiteStackNoise() {
        // both sides throw the SAME type: the raw stack text differs (file
        // name/line noise) but the typed channel is equivalent
        Compare.RunResult legacy = new Compare.RunResult(1, "",
                "Exception in thread \"main\" java.lang.ArithmeticException: / by zero\n\tat Div.main(Div.java:3)", Map.of());
        Compare.RunResult kof = new Compare.RunResult(1, "",
                "Exception in thread \"main\" java.lang.ArithmeticException: / by zero\n\tat Div.main(Div.kf:11)", Map.of());
        Compare.Verdict v = Compare.compare(legacy, kof);
        assertEquals(Compare.Channel.EQUIVALENT, v.exceptions(),
                "same exception type = equivalent failure mode (stack text is noise)");
        assertTrue(v.equivalent());
    }

    @Test
    void typedExceptionThrowVersusCleanExitDiverges() {
        // one side threw, the other exited cleanly via exit(1): same exit
        // code, different failure MODE
        Compare.RunResult legacy = new Compare.RunResult(1, "",
                "Exception in thread \"main\" java.lang.IllegalStateException: bad", Map.of());
        Compare.RunResult kof = new Compare.RunResult(1, "", "", Map.of());
        Compare.Verdict v = Compare.compare(legacy, kof);
        assertEquals(Compare.Channel.DIVERGENT, v.exceptions());
        assertFalse(v.equivalent());
    }

    @Test
    void migratedProgramExceptionTypePreserved(@TempDir Path dir) throws Exception {
        // E2E: the legacy divides by zero, the migrated Kof does the same —
        // both throw ArithmeticException and the verdict stays equivalent
        // even though the stack frames name different files/lines
        Path javaFile = dir.resolve("Div.java");
        Files.writeString(javaFile, """
                public class Div {
                    public static void main(String[] args) {
                        int x = 10 / args.length;
                        System.out.println(x);
                    }
                }
                """);
        javac(javaFile, dir);

        Path kofFile = dir.resolve("Div.kf");
        Files.writeString(kofFile, """
                class Div {
                    static void main(String[] arg0) {
                        var v1 = (10 / arg0.length)
                        println(v1)
                        return
                    }
                }
                """);

        Compare.RunResult legacy = Compare.runLegacy(dir.resolve("Div.class"), List.of(), null);
        Compare.RunResult kof = Compare.runKof(kofFile, "Div", List.of(), null);
        assertEquals(1, legacy.exitCode());
        assertEquals(1, kof.exitCode());
        assertEquals("java.lang.ArithmeticException", Compare.exceptionType(legacy.stderr()));
        assertEquals("java.lang.ArithmeticException", Compare.exceptionType(kof.stderr()));
        assertTrue(Compare.compare(legacy, kof).equivalent(),
                "same typed failure = equivalent despite stack-frame noise");
    }

    @Test
    void dbMutationsEquivalent(@TempDir Path dir) throws Exception {
        // §8 Fase G DB-mutations face (MEASURED first: an H2 file DB run twice
        // with the identical program produces DIFFERENT .mv.db bytes — the
        // store is driver-internal; the DB observable is the query digest in
        // stdout). The legacy JDBC program and the migrated Kof idiom insert
        // the same rows into their OWN file DBs (separate sandboxes) and print
        // the SAME digest; the H2 driver rides the SAME extra classpath on
        // both sides.
        String h2Jar = DbDrivers.ensureDriver("com.h2database:h2:2.5.250").get(0).toString();

        Path javaFile = dir.resolve("DbLeg.java");
        Files.writeString(javaFile, """
                import java.sql.*;
                public class DbLeg {
                    public static void main(String[] args) throws Exception {
                        Connection c = DriverManager.getConnection("jdbc:h2:file:./appdb", "sa", "");
                        Statement s = c.createStatement();
                        s.execute("create table t(id int, name varchar(20))");
                        s.execute("insert into t values (1, 'alpha')");
                        s.execute("insert into t values (2, 'beta')");
                        s.execute("insert into t values (3, 'gamma')");
                        ResultSet r = s.executeQuery("select id, name from t order by id");
                        StringBuilder b = new StringBuilder();
                        while (r.next()) b.append(r.getInt(1)).append('=').append(r.getString(2)).append(';');
                        System.out.println(b);
                    }
                }
                """);
        javac(javaFile, dir);

        Path kofFile = dir.resolve("DbKof.kf");
        Files.writeString(kofFile, """
                record Row(Int id, String name)

                class DbKof {
                    static void main(String[] arg0) {
                        var db = db.connect("jdbc:h2:file:./appdb")
                        db.execute(db, "create table t(id int, name varchar(20))")
                        db.execute(db, "insert into t values (?, ?)", 1, "alpha")
                        db.execute(db, "insert into t values (?, ?)", 2, "beta")
                        db.execute(db, "insert into t values (?, ?)", 3, "gamma")
                        var rows = db.query(db, "select id, name from t order by id")
                        for (var r in rows) {
                            var row = json.decode<Row>(r)
                            print(row.id + "=" + row.name + ";")
                        }
                        println("")
                    }
                }
                """);

        Compare.RunResult legacy = Compare.runLegacy(dir.resolve("DbLeg.class"), List.of(), null, h2Jar);
        Compare.RunResult kof = Compare.runKof(kofFile, "DbKof", List.of(), null, h2Jar);
        assertEquals("1=alpha;2=beta;3=gamma;", legacy.stdout());
        assertEquals("1=alpha;2=beta;3=gamma;", kof.stdout());
        Compare.Verdict v = Compare.compare(legacy, kof);
        assertTrue(v.equivalent(),
                "same DB mutations must compare equivalent (digest observable, store bytes excluded): " + v);
    }

    @Test
    void dbMutationsDivergentDetected(@TempDir Path dir) throws Exception {
        // the migrated side inserts ONE different row: the digest observable
        // must flip the verdict to divergent
        String h2Jar = DbDrivers.ensureDriver("com.h2database:h2:2.5.250").get(0).toString();

        Path javaFile = dir.resolve("DbLeg.java");
        Files.writeString(javaFile, """
                import java.sql.*;
                public class DbLeg {
                    public static void main(String[] args) throws Exception {
                        Connection c = DriverManager.getConnection("jdbc:h2:file:./appdb", "sa", "");
                        Statement s = c.createStatement();
                        s.execute("create table t(id int, name varchar(20))");
                        s.execute("insert into t values (1, 'alpha')");
                        s.execute("insert into t values (2, 'beta')");
                        s.execute("insert into t values (3, 'gamma')");
                        ResultSet r = s.executeQuery("select id, name from t order by id");
                        StringBuilder b = new StringBuilder();
                        while (r.next()) b.append(r.getInt(1)).append('=').append(r.getString(2)).append(';');
                        System.out.println(b);
                    }
                }
                """);
        javac(javaFile, dir);

        Path kofFile = dir.resolve("DbKof.kf");
        Files.writeString(kofFile, """
                record Row(Int id, String name)

                class DbKof {
                    static void main(String[] arg0) {
                        var db = db.connect("jdbc:h2:file:./appdb")
                        db.execute(db, "create table t(id int, name varchar(20))")
                        db.execute(db, "insert into t values (?, ?)", 1, "alpha")
                        db.execute(db, "insert into t values (?, ?)", 2, "beta")
                        db.execute(db, "insert into t values (?, ?)", 3, "GAMMA")
                        var rows = db.query(db, "select id, name from t order by id")
                        for (var r in rows) {
                            var row = json.decode<Row>(r)
                            print(row.id + "=" + row.name + ";")
                        }
                        println("")
                    }
                }
                """);

        Compare.RunResult legacy = Compare.runLegacy(dir.resolve("DbLeg.class"), List.of(), null, h2Jar);
        Compare.RunResult kof = Compare.runKof(kofFile, "DbKof", List.of(), null, h2Jar);
        assertNotEquals(legacy.stdout(), kof.stdout());
        Compare.Verdict v = Compare.compare(legacy, kof);
        assertEquals(Compare.Channel.DIVERGENT, v.stdout(), "a different DB mutation must diverge");
        assertFalse(v.equivalent());
    }

    @Test
    void noThrowNoExceptionChannel() {
        // clean runs: the typed channel is equivalent (null == null)
        Compare.RunResult a = new Compare.RunResult(0, "ok\n", "", Map.of());
        Compare.RunResult b = new Compare.RunResult(0, "ok\n", "", Map.of());
        Compare.Verdict v = Compare.compare(a, b);
        assertEquals(Compare.Channel.EQUIVALENT, v.exceptions());
        assertTrue(v.equivalent());
    }

    @Test
    void helloEquivalent(@TempDir Path dir) throws Exception {
        Path javaFile = dir.resolve("Hello.java");
        Files.writeString(javaFile, """
                public class Hello {
                    public static void main(String[] args) {
                        System.out.println("hello");
                    }
                }
                """);
        javac(javaFile, dir);

        Path kofFile = dir.resolve("hello.kf");
        Files.writeString(kofFile, "main() {\n    println(\"hello\")\n}\n");

        Compare.RunResult legacy = Compare.runLegacy(dir.resolve("Hello.class"), List.of(), null);
        Compare.RunResult kof = Compare.runKof(kofFile, List.of(), null);
        assertEquals("hello", legacy.stdout());
        assertEquals("hello", kof.stdout());
        assertTrue(Compare.compare(legacy, kof).equivalent());
    }

    @Test
    void addWithArgsEquivalent(@TempDir Path dir) throws Exception {
        Path javaFile = dir.resolve("Add.java");
        Files.writeString(javaFile, """
                public class Add {
                    public static void main(String[] args) {
                        int a = Integer.parseInt(args[0]);
                        int b = Integer.parseInt(args[1]);
                        System.out.println(a + b);
                    }
                }
                """);
        javac(javaFile, dir);

        Path kofFile = dir.resolve("add.kf");
        Files.writeString(kofFile, """
                main() {
                    var a = 2
                    var b = 3
                    println(a + b)
                }
                """);

        // Compare with no args: legacy uses fixed args [2,3], kof uses literals.
        Compare.RunResult legacy = Compare.runLegacy(dir.resolve("Add.class"), List.of("2", "3"), null);
        Compare.RunResult kof = Compare.runKof(kofFile, List.of(), null);
        assertEquals("5", legacy.stdout());
        assertEquals("5", kof.stdout());
        assertTrue(Compare.compare(legacy, kof).equivalent());
    }

    @Test
    void divergenceDetected(@TempDir Path dir) throws Exception {
        Path javaFile = dir.resolve("Diff.java");
        Files.writeString(javaFile, """
                public class Diff {
                    public static void main(String[] args) {
                        System.out.println("original");
                    }
                }
                """);
        javac(javaFile, dir);

        Path kofFile = dir.resolve("diff.kf");
        Files.writeString(kofFile, "main() {\n    println(\"ko f\")\n}\n");

        Compare.RunResult legacy = Compare.runLegacy(dir.resolve("Diff.class"), List.of(), null);
        Compare.RunResult kof = Compare.runKof(kofFile, List.of(), null);
        Verify.difference(Compare.compare(legacy, kof));
    }

    @Test
    void fileSideEffectsCompared(@TempDir Path dir) throws Exception {
        Path javaFile = dir.resolve("Write.java");
        Files.writeString(javaFile, """
                import java.nio.file.*;
                public class Write {
                    public static void main(String[] args) throws Exception {
                        Files.writeString(Path.of("out.txt"), "kof");
                    }
                }
                """);
        javac(javaFile, dir);

        // Kof equivalente escreve o MESMO arquivo
        Path kofSame = dir.resolve("same.kf");
        Files.writeString(kofSame, """
                main() {
                    writeFile("out.txt", "kof")
                }
                """);
        Compare.RunResult legacy = Compare.runLegacy(dir.resolve("Write.class"), List.of(), null);
        Compare.RunResult kof = Compare.runKof(kofSame, List.of(), null);
        assertTrue(Compare.compare(legacy, kof).equivalent(), "files iguais devem ser equivalentes");

        // Kof escreve conteúdo DIFERENTE
        Path kofDiff = dir.resolve("diff.kf");
        Files.writeString(kofDiff, """
                main() {
                    writeFile("out.txt", "outro")
                }
                """);
        Compare.RunResult kofD = Compare.runKof(kofDiff, List.of(), null);
        assertFalse(Compare.compare(legacy, kofD).equivalent(), "conteúdo diferente deve divergir");
    }


    /** Small helper to assert, without static imports, that stdout diverged. */
    private static final class Verify {
        static void difference(Compare.Verdict v) {
            assertFalse(v.equivalent(), "expected divergence");
            assertEquals(Compare.Channel.DIVERGENT, v.stdout());
        }
    }

    @Test
    void flagWithoutValueIsUsageErrorNotThrow() {
        // CodeQL java/index-out-of-bounds (Compare.java:77-78): `--stdin` /
        // `--arg` no fim liam args[++i] sem checar (AIOOBE). Agora usage+exit 1.
        assertEquals(1, Compare.run(new String[]{"compare", "a.class", "b.kf", "--stdin"}),
                "stdin sem valor deve ser usage error, nunca throw");
        assertEquals(1, Compare.run(new String[]{"compare", "a.class", "b.kf", "--arg"}),
                "arg sem valor deve ser usage error, nunca throw");
        assertEquals(1, Compare.run(new String[]{"compare", "a.class", "b.kf", "--arg", "x", "--stdin"}),
                "stdin sem valor no fim deve ser usage error, nunca throw");
    }

    @Test
    void unknownOptionIsFatalNotSilentlyRun() {
        // #431: antes o `default` só imprimia "unknown option" e a comparação
        // seguia, podendo sair 0. Agora é erro fatal (exit 1), como o restante
        // da família (KofDebug/CliFlagStrictness).
        assertEquals(1, Compare.run(new String[]{"compare", "a.class", "b.kf", "--bogus"}),
                "opcao desconhecida deve ser fatal (exit 1)");
    }
}