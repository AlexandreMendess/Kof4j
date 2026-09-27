package dev.kof.cli;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * X7-3 (fase 6 do §19.5): `kof debug --target native` = build do ELF com
 * DWARF + gdb do alvo apontado para o diretorio da FONTE Kof. O host da lane
 * nao tem gdb (medido 19/09) — a prova da CONSTRUCAO roda com stub-gdb no
 * env `KOF_GDB` (mesma formula de teste do `gh` em DeployPublish/KOF_PUBLISH_API
 * e do sysroot em KOF_CROSS_SYSROOT); o gdb REAL e exercitado onde ele existe
 * (runner ubuntu da CI tem gdb; o `--dwarf=decodedline` do ELF ja e travado
 * por NativeDwarfLineInfoTest).
 */
class KofDebugNativeTest {

    private record Cli(int exit, String out) {}

    private static Cli cli(Path workDir, Map<String, String> env, String... args) throws Exception {
        List<String> cmd = new ArrayList<>();
        cmd.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
        cmd.add("-cp");
        cmd.add(System.getProperty("java.class.path"));
        cmd.add("dev.kof.cli.Main");
        cmd.addAll(List.of(args));
        ProcessBuilder pb = new ProcessBuilder(cmd).directory(workDir.toFile());
        pb.redirectErrorStream(true);
        pb.environment().putAll(env);
        Process p = pb.start();
        try {
            String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(p.waitFor(120, TimeUnit.SECONDS), "timeout\n" + out);
            return new Cli(p.exitValue(), out);
        } finally {
            CliProcessTree.terminate(p);
        }
    }

    private static String stubGdb(Path dir) throws Exception {
        Path stub = dir.resolve("stub-gdb.sh");
        Files.writeString(stub, "#!/bin/sh\nprintf '%s\\n' \"$@\" > \"$KOF_GDB_ARGS_FILE\"\nexit 0\n");
        stub.toFile().setExecutable(true);
        return stub.toString();
    }

    @Test
    void nativeFaceBuildsElfAndDrivesGdbWithSourceDirectory(@TempDir Path dir) throws Exception {
        Path src = dir.resolve("Main.kf");
        Files.writeString(src, "main() {\n    println(\"dbg\")\n}\n");
        Path argsFile = dir.resolve("gdb-args.txt");
        Cli r = cli(dir, Map.of("KOF_GDB", stubGdb(dir), "KOF_GDB_ARGS_FILE", argsFile.toString()),
                "debug", "--target", "native", "Main.kf");
        assertEquals(0, r.exit(), "stub-gdb deve receber o launch:\n" + r.out());
        assertTrue(Files.exists(argsFile), "o stub nunca foi executado: " + r.out());
        String a = Files.readString(argsFile);
        assertTrue(a.contains("-q"), "silencioso: " + a);
        assertTrue(a.contains("set pagination off"), "batch-friendly: " + a);
        assertTrue(a.contains("directory " + dir.toAbsolutePath()),
                "o gdb precisa do diretorio da FONTE (o .file do DWARF e relativo): " + a);
        assertTrue(a.contains("Default/Main"), "o ELF construido: " + a);
    }

    @Test
    void nativeFaceWithoutGdbFailsHonestly(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("Main.kf"), "main() { println(\"oi\") }\n");
        Cli r = cli(dir, Map.of("KOF_GDB", "/nonexistent/kof-gdb-probe-xyz"),
                "debug", "--target", "native", "Main.kf");
        assertEquals(1, r.exit(), "sem gdb nao pode haver debug falso:\n" + r.out());
        assertTrue(r.out().contains("gdb") && r.out().contains("not available"),
                "diagnostico deve nomear a ferramenta ausente (R6): " + r.out());
    }

    @Test
    void jsFaceStaysAnHonestGap(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("Main.kf"), "main() { println(\"oi\") }\n");
        Cli r = cli(dir, Map.of(), "debug", "--target", "js", "Main.kf");
        assertEquals(1, r.exit(), r.out());
        assertTrue(r.out().contains("EMBEDDED"),
                "js roda no engine embutido — sem inspector p/ anexar; honesto: " + r.out());
    }

    private static boolean has(String... executables) {
        String path = System.getenv("PATH");
        if (path == null) {
            return false;
        }
        List<Path> dirs = new ArrayList<>();
        for (String d : path.split(java.io.File.pathSeparator)) {
            if (!d.isEmpty()) {
                dirs.add(Path.of(d));
            }
        }
        for (String e : executables) {
            boolean found = false;
            for (Path d : dirs) {
                if (Files.isExecutable(d.resolve(e))) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                return false;
            }
        }
        return true;
    }

    /**
     * `--break <linha>` = sessao batch scriptavel: com o gdb REAL + toolchain
     * x86-64 o breakpoint cai na LINHA Kof e o backtrace nomeia a funcao Kof.
     * Sem gdb/toolchain o caso vira skip honesto (o CI ubuntu roda).
     */
    @Test
    void nativeBatchBreaksAtKofLineAndShowsBacktrace(@TempDir Path dir) throws Exception {
        Assumptions.assumeTrue(has("gdb", "as", "ld"),
                "gdb + toolchain x86-64 ausentes — pulando (o CI ubuntu roda)");
        Path src = dir.resolve("Main.kf");
        Files.writeString(src, "main() {\n    println(\"dbg\")\n    var x = 41\n    println(x + 1)\n}\n");
        Cli r = cli(dir, Map.of(), "debug", "--target", "native", "--break", "4", "Main.kf");
        Assumptions.assumeFalse(r.out().contains("no ELF produced"),
                "toolchain nativa indisponivel nesta invocacao:\n" + r.out());
        assertEquals(0, r.exit(), "gdb batch deveria sair 0:\n" + r.out());
        assertTrue(r.out().contains("Main.kf:4"),
                "o ponto de parada cai na linha Kof 4, nunca no assembly:\n" + r.out());
        assertTrue(r.out().contains("main"), "o backtrace nomeia a funcao Kof:\n" + r.out());
        assertTrue(r.out().contains("dbg"),
                "o programa executou ate a linha 4 (o println da 2 ja saiu):\n" + r.out());
    }

    @Test
    void badBreakLineIsRejected(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("Main.kf"), "main() { println(\"oi\") }\n");
        Cli r = cli(dir, Map.of(), "debug", "--target", "native", "--break", "abc", "Main.kf");
        assertEquals(1, r.exit(), r.out());
        assertTrue(r.out().contains("--break expects a line number"), r.out());
    }

    @Test
    void breakOnJvmTargetIsRejectedHonestly(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("Main.kf"), "main() { println(\"oi\") }\n");
        Cli r = cli(dir, Map.of(), "debug", "--break", "4", "Main.kf");
        assertEquals(1, r.exit(), r.out());
        assertTrue(r.out().contains("only apply to --target native"), r.out());
    }

    @Test
    void unknownTargetAndFlagStrictnessHold(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("Main.kf"), "main() { println(\"oi\") }\n");
        Cli zig = cli(dir, Map.of(), "debug", "--target", "zig", "Main.kf");
        assertEquals(1, zig.exit(), "target inexistente deve recusar: " + zig.out());
        assertTrue(zig.out().contains("unknown --target"), zig.out());
        Cli noVal = cli(dir, Map.of(), "debug", "--target");
        assertEquals(1, noVal.exit(), noVal.out());
        Cli trailing = cli(dir, Map.of("KOF_GDB", "/no/gdb"), "debug", "--target", "native", "Main.kf", "--bogus");
        assertNotEquals(0, trailing.exit(), "--flag depois do arquivo ainda recusa (R6):\n" + trailing.out());
        assertTrue(trailing.out().contains("--bogus"), trailing.out());
    }
}
