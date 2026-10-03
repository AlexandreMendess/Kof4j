package dev.kof.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * X8 fatia 3 / G6 "next": "named suites by directory". `kof test <dir>` passa a
 * descer nos subdiretórios e trata cada diretório como uma suíte nomeada (nome =
 * caminho relativo a <dir>; "." = a raiz), com contagem por suíte somada ao
 * total. Aditivo: a descoberta de build/run segue não-recursiva.
 */
class CmdTestSuiteTest {

    private record Cli(int exit, String out) {}

    private static Cli cli(Path workDir, String... args) throws Exception {
        List<String> cmd = new ArrayList<>();
        cmd.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
        cmd.add("-cp");
        cmd.add(System.getProperty("java.class.path"));
        cmd.add("dev.kof.cli.Main");
        cmd.addAll(List.of(args));
        ProcessBuilder pb = new ProcessBuilder(cmd).directory(workDir.toFile());
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(p.waitFor(180, TimeUnit.SECONDS), "o proprio CLI nao pode hangar\n" + out);
        return new Cli(p.exitValue(), out);
    }

    @Test
    void directoryInputDiscoversSubdirectoriesAsNamedSuites(@TempDir Path dir) throws Exception {
        Path tests = dir.resolve("tests");
        Files.createDirectories(tests.resolve("unit"));
        Files.createDirectories(tests.resolve("integ"));
        Files.writeString(tests.resolve("smoke.kf"), "main() { println(\"smoke\") }\n");
        Files.writeString(tests.resolve("unit").resolve("math.kf"),
                "test \"soma\" {\n    assert(2 + 2 == 4)\n}\n");
        Files.writeString(tests.resolve("integ").resolve("io.kf"),
                "test \"roundtrip\" {\n    assert(\"a\" == \"a\")\n}\n");
        Cli r = cli(dir, "test", tests.toString());
        assertEquals(0, r.exit(), "todas as suites verdes devem exit 0:\n" + r.out());
        assertTrue(r.out().contains("suite unit: 1 passed, 0 failed"), r.out());
        assertTrue(r.out().contains("suite integ: 1 passed, 0 failed"), r.out());
        assertTrue(r.out().contains("suite .: 1 passed, 0 failed"), "raiz é uma suíte (arquivo sem `test`):\n" + r.out());
        assertTrue(r.out().contains("3 passed, 0 failed"), r.out());
    }

    @Test
    void packageInSubdirectoryUnderSeparateTestRootCompiles(@TempDir Path dir) throws Exception {
        // #708: `kof test src/test/kof` with the source in a package directory
        // (exemplo/CalculoTest.kf, `package exemplo`) must resolve PKG004
        // against the TEST ROOT, not the file's immediate directory (which
        // would make the expected package "").
        Path tests = dir.resolve("src/test/kof");
        Files.createDirectories(tests.resolve("exemplo"));
        Files.writeString(tests.resolve("exemplo/CalculoTest.kf"), """
                package exemplo
                test "soma" {
                    assert(2 + 3 == 5)
                }
                """);
        Cli r = cli(dir, "test", tests.toString(), "--target", "jvm");
        assertEquals(0, r.exit(), "pacote deve casar com a raiz de testes:\n" + r.out());
        assertTrue(r.out().contains("1 passed, 0 failed"), r.out());
    }

    @Test
    void helperFilesWithoutTestsOrMainAreSkippedHonestly(@TempDir Path dir) throws Exception {
        // §571: um módulo auxiliar (funções puras que outro arquivo importa)
        // não tem `test` nem `main`. Antes: "could not resolve main([String])"
        // e a corrida inteira falhava. Agora: nota honesta, sem contar como
        // suíte nem como falha.
        Path tests = dir.resolve("tests");
        Files.createDirectories(tests.resolve("sub"));
        Files.writeString(tests.resolve("helpers.kf"),
                "String rotulo(Int n) { return \"n=\" + n }\n");
        Files.writeString(tests.resolve("sub").resolve("a.kf"),
                "test \"ok\" {\n    assert(true)\n}\n");
        Cli r = cli(dir, "test", tests.toString());
        assertEquals(0, r.exit(), "auxiliar não pode derrubar a suíte:\n" + r.out());
        assertTrue(r.out().contains("SKIP " + tests.resolve("helpers.kf")
                + " (no tests, no main)"), "nota honesta do auxiliar:\n" + r.out());
        assertTrue(r.out().contains("1 passed, 0 failed, 1 skipped"),
                "o auxiliar é contado como skip:\n" + r.out());
        assertTrue(r.out().contains("PASS ok"), "o teste real roda:\n" + r.out());
    }

    @Test
    void aDirectoryWithOnlyHelperFilesIsNotASuccess(@TempDir Path dir) throws Exception {
        Path tests = dir.resolve("tests");
        Files.createDirectories(tests);
        Files.writeString(tests.resolve("helpers.kf"),
                "Int dobro(Int n) { return n * 2 }\n");
        Cli r = cli(dir, "test", tests.toString());
        assertEquals(1, r.exit(), "zero arquivos executáveis não é sucesso:\n" + r.out());
        assertTrue(r.out().contains("no runnable test or program file found"),
                "recusa nomeia a causa:\n" + r.out());
    }

    @Test
    void fileWithoutTestsOrMainIsRefusedNotAFalsePass(@TempDir Path dir) throws Exception {
        Path f = dir.resolve("helpers.kf");
        Files.writeString(f, "Int triplo(Int n) { return n * 3 }\n");
        Cli r = cli(dir, "test", f.toString());
        assertEquals(1, r.exit(), "um módulo auxiliar isolado não é uma suíte:\n" + r.out());
        assertTrue(r.out().contains("no runnable test or program file found"),
                "recusa nomeia a causa:\n" + r.out());
    }

    @Test
    void fileWithMainAndNoTestsStillRunsAsAProgram(@TempDir Path dir) throws Exception {
        // Contrato preservado (StructuredTestE2ETest.filesWithoutTestsCompileUnchanged):
        // sem testes mas com `main`, o programa roda normalmente.
        Path f = dir.resolve("Main.kf");
        Files.writeString(f, "main() { println(\"programa normal\") }\n");
        Cli r = cli(dir, "test", f.toString());
        assertEquals(0, r.exit(), "programa sem testes roda:\n" + r.out());
        assertTrue(r.out().contains("PASS " + f), "roda como programa:\n" + r.out());
        assertFalse(r.out().contains("SKIP"), "não é skip: tem main:\n" + r.out());
    }

    @Test
    void aFailingSuiteIsCountedPerSuiteAndFailsTheRun(@TempDir Path dir) throws Exception {
        Path tests = dir.resolve("tests");
        Files.createDirectories(tests.resolve("ok"));
        Files.createDirectories(tests.resolve("bad"));
        Files.writeString(tests.resolve("ok").resolve("a.kf"),
                "test \"ok\" {\n    assert(true)\n}\n");
        Files.writeString(tests.resolve("bad").resolve("b.kf"),
                "test \"bad\" {\n    assert(1 == 2, \"um nao eh dois\")\n}\n");
        Cli r = cli(dir, "test", tests.toString());
        assertEquals(1, r.exit(), "suíte com falha deve exit 1:\n" + r.out());
        assertTrue(r.out().contains("suite ok: 1 passed, 0 failed"), r.out());
        assertTrue(r.out().contains("suite bad: 0 passed, 1 failed"), r.out());
        assertTrue(r.out().contains("1 passed, 1 failed"), r.out());
    }
}
