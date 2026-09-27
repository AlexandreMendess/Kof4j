package dev.kof.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.Assumptions;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * §371 (issue #550): o cenario da distribuicao real — {@code kof build
 * hello.kf --target native.risc|native.arm} executado de um diretorio SEM a
 * arvore do projeto (foi assim que o bug apareceu: loader de slices lendo o
 * fonte {@code .java} do runtime por caminho RELATIVO ao CWD → "prune
 * DESABILITADO" → runtime completo → {@code usesDb} → {@code -lsqlite3} →
 * COMP001 no sysroot cross, ate em hello.kf).
 *
 * <p>Precedente de execucao do CLI sem jar (mesma classepath do fork, cwd
 * externo): {@code CmdBuildFatTest.startCli}. Aqui o filho e lancado de um
 * {@link TempDir} que contem apenas o {@code hello.kf} — RED no codigo antigo
 * (o recurso empacotado nao existia e o arquivo nao estava la) e GREEN com o
 * classpath-first do {@code RuntimeSourceLoader}. Ferramenta/qemu ausentes →
 * {@code assumeTrue} (skip honesto, NATIVE002 — nunca falso-verde).</p>
 */
class ShippedCliCrossSmokeTest {

    private static String javaBin() {
        return Path.of(System.getProperty("java.home"), "bin", "java").toString();
    }

    private static boolean has(String... cmds) {
        // #555 (java/relative-path-command): sonda o PATH direto, sem spawnar
        // `sh -c command -v` — mesmo padrao do KofDebugNativeTest (#931).
        String path = System.getenv("PATH");
        if (path == null) {
            return false;
        }
        for (String c : cmds) {
            boolean found = false;
            for (String dir : path.split(java.io.File.pathSeparator)) {
                if (java.nio.file.Files.isExecutable(java.nio.file.Path.of(dir, c))) {
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

    private static String sysrootFor(String arch) {
        String env = System.getenv("KOF_CROSS_SYSROOT");
        String loader = arch.equals("riscv64")
                ? "ld-linux-riscv64-lp64d.so.1" : "ld-linux-aarch64.so.1";
        if (env != null && !env.isBlank()
                && Files.exists(Path.of(env, "usr", arch + "-linux-gnu", "lib", loader))) {
            return env + "/usr/" + arch + "-linux-gnu";
        }
        Path system = Path.of("/usr", arch + "-linux-gnu");
        if (Files.exists(system.resolve("lib").resolve(loader))) return system.toString();
        return null;
    }

    private void crossSmokeFromForeignCwd(String cliTarget, String arch, Path dir)
            throws Exception {
        Assumptions.assumeTrue(
                has(arch + "-linux-gnu-as", arch + "-linux-gnu-ld")
                        && (has("qemu-" + arch) || has("qemu-" + arch + "-static")),
                "cross toolchain " + arch + " + qemu ausente — pulando (NATIVE002)");
        // O diretorio do "usuario": APENAS o fonte. Nenhum caminho do
        // projeto resolve relativo ao CWD aqui — exatamente o jar shipped.
        Files.writeString(dir.resolve("hello.kf"), "main() { println(\"hi\") }\n");
        List<String> cmd = new ArrayList<>();
        cmd.add(javaBin());
        cmd.add("-cp");
        cmd.add(System.getProperty("java.class.path"));
        cmd.add("dev.kof.cli.Main");
        cmd.addAll(List.of("build", "hello.kf", "--target", cliTarget, "--output", "out"));
        Process build = new ProcessBuilder(cmd).directory(dir.toFile())
                .redirectErrorStream(true).start();
        String out = new String(build.getInputStream().readAllBytes(),
                StandardCharsets.UTF_8);
        assertTrue(build.waitFor(180, TimeUnit.SECONDS), "build timeout\n" + out);
        assertEquals(0, build.exitValue(),
                "build " + cliTarget + " rc (cwd fora da arvore):\n" + out);
        assertFalse(out.contains("DESABILITADO"),
                "o prune nao pode desligar do classpath (§371):\n" + out);
        assertTrue(out.contains("runtime prune"),
                "esperado prune ATIVO (assinatura 'runtime prune'):\n" + out);
        Path bin = dir.resolve("out").resolve("Default").resolve("Main");
        assertTrue(Files.exists(bin), "binario cross ausente:\n" + out);

        String qemu = has("qemu-" + arch) ? "qemu-" + arch : "qemu-" + arch + "-static";
        ProcessBuilder pb = new ProcessBuilder(qemu, bin.toString());
        pb.directory(dir.toFile());
        pb.redirectErrorStream(true);
        String prefix = sysrootFor(arch);
        if (prefix != null) pb.environment().put("QEMU_LD_PREFIX", prefix);
        Process run = pb.start();
        String runOut = new String(run.getInputStream().readAllBytes(),
                StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
        assertTrue(run.waitFor(60, TimeUnit.SECONDS), "qemu timeout");
        assertEquals(0, run.exitValue(), "qemu rc (" + arch + "): " + runOut);
        assertEquals("hi", runOut, "saida do binario " + arch + " sob qemu");
    }

    @Test
    void nativeRiscFromForeignCwdRunsUnderQemu(@TempDir Path dir) throws Exception {
        crossSmokeFromForeignCwd("native.risc", "riscv64", dir);
    }

    @Test
    void nativeArmFromForeignCwdRunsUnderQemu(@TempDir Path dir) throws Exception {
        crossSmokeFromForeignCwd("native.arm", "aarch64", dir);
    }
}
