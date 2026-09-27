package dev.kof.compiler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import dev.kof.compiler.nat.NativeProfile;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * B-6.1 (PLAN-BAREMETAL-BOOT) — anéis de privilégio x86_64, fatia 1: o perfil
 * {@code uefi-ring} instala a GDT/IDT/TSS PRÓPRIOS do Kof no {@code _start} do
 * boot path UEFI (B-2), recarrega {@code CS} para o seletor do Kof via
 * {@code lretq}, e prova o handler ring0 com um {@code int3} controlado — só
 * então imprime {@code KO-RING IDT OK} via {@code ConOut}; depois restaura o
 * estado do firmware. Sem superfície Kof (rule 6): é a maquinaria habilitadora,
 * não o domínio CPL1 (B-6.2).
 *
 * <p>Receita de link: idêntica à B-2 (ELF estático → {@code objcopy
 * --target=pei-x86-64 --subsystem=10}); o perfil {@code uefi-ring} herda o
 * boot path e o PE do perfil {@code uefi} (que fica INTOCADO — o teste
 * negativo cobre que a string de anéis não vaza para {@code uefi} puro).
 */
class RingPrivilegeE2ETest {

    private static final String HELLO = """
            main() {
                println("KO-RING MAIN")
            }
            """;

    /** B-6.2b: `ring1(task)` executa `task` em CPL1 (só toca memória) e o
     *  estado sobrevive ao retorno ao ring0. */
    private static final String RING1_SRC = """
            class Mark {
                static Int value = 0
            }
            void task() {
                Mark.value = 41
            }
            main() {
                ring1(task)
                println(Mark.value)
                println("KO-RING MAIN")
            }
            """;

    private static boolean hasTool(String tool, String... args) {
        String[] cmd = new String[args.length + 1];
        cmd[0] = tool;
        System.arraycopy(args, 0, cmd, 1, args.length);
        try {
            Process p = new ProcessBuilder(cmd).start();
            return p.waitFor(10, TimeUnit.SECONDS) && p.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private static Path ovmfPrefix() {
        String env = System.getenv("KOF_OVMF_HOME");
        if (env != null && Files.isRegularFile(Path.of(env, "usr/share/OVMF/OVMF_CODE_4M.fd"))) {
            return Path.of(env);
        }
        Path home = Path.of(System.getProperty("user.home"), ".local/share/kof-ovmf");
        if (Files.isRegularFile(home.resolve("usr/share/OVMF/OVMF_CODE_4M.fd"))) {
            return home;
        }
        return null;
    }

    private static Path findOvmfCode() {
        Path p = ovmfPrefix();
        if (p != null) return p.resolve("usr/share/OVMF/OVMF_CODE_4M.fd");
        Path sys = Path.of("/usr/share/OVMF/OVMF_CODE_4M.fd");
        return Files.isRegularFile(sys) ? sys : null;
    }

    private static Path findQemu() {
        if (hasTool("qemu-system-x86_64", "--version")) return Path.of("qemu-system-x86_64");
        Path p = ovmfPrefix();
        if (p != null) return p.resolve("usr/bin/qemu-system-x86_64");
        return null;
    }

    private Path build(Path tempDir, NativeProfile profile) throws IOException {
        return buildSource(tempDir, profile, HELLO);
    }

    private Path buildSource(Path tempDir, NativeProfile profile, String src) throws IOException {
        CompilerDriver driver = new CompilerDriver();
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, src);
        Path outDir = tempDir.resolve("out-" + profile);
        CompilationResult result = driver.compile(source, outDir, Target.NATIVE, profile);
        assertEquals(true, result.success(),
                "compile " + profile + " inesperado: " + result.diagnostics().getDiagnostics());
        return outDir.resolve("Default/Main");
    }

    private Path makeEsp(Path tempDir, Path peBinary) throws IOException, InterruptedException {
        Path esp = tempDir.resolve("esp.img");
        Files.deleteIfExists(esp);
        run(5_000, "mformat", "-i", esp.toString(), "-C", "-T", "16384", "::");
        run(5_000, "mmd", "-i", esp.toString(), "::/EFI");
        run(5_000, "mmd", "-i", esp.toString(), "::/EFI/BOOT");
        run(10_000, "mcopy", "-i", esp.toString(), peBinary.toString(), "::/EFI/BOOT/BOOTX64.EFI");
        return esp;
    }

    private void run(long timeoutMs, String... cmd) throws IOException, InterruptedException {
        // §511: sob carga, o timeout de parede de uma ferramenta de disco (mtools)
        // é flake ambiental — um processo novo passa; o segundo timeout é falha real.
        for (int attempt = 1; attempt <= 2; attempt++) {
            Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            if (p.waitFor(timeoutMs, TimeUnit.MILLISECONDS) && p.exitValue() == 0) {
                return;
            }
            p.destroyForcibly();
            p.waitFor(10, TimeUnit.SECONDS);
        }
        fail("falhou (2 tentativas): " + String.join(" ", cmd));
    }

    /** Boota o PE sob OVMF e devolve o texto do serial. Até 3 tentativas com o
     *  boot LENTO preservado (§511): a espera é progress-aware — enquanto o
     *  serial cresce continua (teto de 240 s por tentativa), estagnado por 60 s
     *  aborta cedo (mesmo ponto de abort da janela antiga, nunca pior) e a
     *  tentativa seguinte começa limpa. O ESP é construído UMA vez — retry de
     *  boot não refaz o trabalho do mtools sob carga (que era parte do flake).
     *  Falha real falha nas três. Janela por tentativa: idle 60 s, cap 240 s. */
    private String bootOvmf(Path tempDir, Path peBinary, String expected) throws Exception {
        Path code = findOvmfCode();
        Path qemu = findQemu();
        assumeTrue(code != null, "OVMF ausente (KOF_OVMF_HOME ou ~/.local/share/kof-ovmf)");
        assumeTrue(qemu != null, "qemu-system-x86_64 ausente");
        assumeTrue(hasTool("mformat", "-V") || hasTool("mformat", "--help"), "mtools ausente");
        Path esp = makeEsp(tempDir, peBinary);
        String text = "";
        for (int attempt = 1; attempt <= 3; attempt++) {
            String t = tryBoot(tempDir, esp, expected, code, qemu, attempt);
            if (t.contains(expected)) return t;
            if (t.length() > text.length()) text = t;
        }
        return text;
    }

    private String tryBoot(Path tempDir, Path esp, String expected, Path code, Path qemu,
            int attempt) throws Exception {
        Path vars = tempDir.resolve("vars-" + attempt + ".fd");
        Files.copy(code.resolveSibling("OVMF_VARS_4M.fd"), vars);
        Path ser = tempDir.resolve("ser-" + attempt + ".log");

        java.util.List<String> cmd = new java.util.ArrayList<>();
        boolean prefixQemu = ovmfPrefix() != null && qemu.startsWith(ovmfPrefix());
        if (prefixQemu) {
            cmd.add(ovmfPrefix().resolve("usr/lib/x86_64-linux-gnu/ld-linux-x86-64.so.2").toString());
            cmd.add("--library-path");
            cmd.add(ovmfPrefix().resolve("usr/lib/x86_64-linux-gnu").toString());
        }
        cmd.add(qemu.toString());
        if (ovmfPrefix() != null) cmd.addAll(java.util.List.of(
                "-L", ovmfPrefix().resolve("usr/share/qemu").toString()));
        cmd.addAll(java.util.List.of(
                "-machine", "q35", "-m", "256",
                "-display", "none", "-nodefaults", "-net", "none",
                "-serial", "file:" + ser,
                "-drive", "if=pflash,format=raw,readonly=on,file=" + code,
                "-drive", "if=pflash,format=raw,file=" + vars,
                "-drive", "file=" + esp + ",format=raw,media=disk"));

        Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        String text = "";
        try {
            text = OvmfSerialWait.untilDone(ser, t -> t.contains(expected), 60_000, 240_000);
        } finally {
            p.destroyForcibly();
            p.waitFor(10, TimeUnit.SECONDS);
        }
        return text;
    }

    @Test
    void ringProfileBootsAndProvesOwnedIdtUnderOvmf(@TempDir Path tempDir) throws Exception {
        assumeTrue(hasTool("as", "--version") && hasTool("ld", "--version")
                && hasTool("objcopy", "--version"), "toolchain binutils ausente");
        Path bin = build(tempDir, NativeProfile.UEFI_RING);
        String text = bootOvmf(tempDir, bin, "KO-RING1 CPL1 OK");
        assertTrue(text.contains("KO-RING IDT OK"),
                "OVMF nao provou a IDT ring0 do Kof. Fim do log: "
                        + text.substring(Math.max(0, text.length() - 400)));
        // B-6.2a: a transição CPL0->CPL1 executou código com RPL=1 e voltou.
        assertTrue(text.contains("KO-RING1 CPL1 OK"),
                "OVMF nao provou a entrada CPL1 (ring1). Fim do log: "
                        + text.substring(Math.max(0, text.length() - 400)));
        // o programa Kof segue rodando normalmente após a restauração.
        assertTrue(text.contains("KO-RING MAIN"),
                "main nao rodou apos o self-test de anéis. Fim do log: "
                        + text.substring(Math.max(0, text.length() - 400)));
    }

    @Test
    void plainUefiProfileDoesNotEmitRingProof(@TempDir Path tempDir) throws Exception {
        assumeTrue(hasTool("as", "--version") && hasTool("ld", "--version")
                && hasTool("objcopy", "--version"), "toolchain binutils ausente");
        Path bin = build(tempDir, NativeProfile.UEFI);
        String text = bootOvmf(tempDir, bin, "KO-RING MAIN");
        assertTrue(text.contains("KO-RING MAIN"), "UEFI puro deve rodar normalmente");
        assertFalse(text.contains("KO-RING IDT OK"),
                "a maquinaria de anéis NAO pode vazar para o perfil uefi puro (R6)");
        assertFalse(text.contains("KO-RING1 CPL1 OK"),
                "a maquinaria de CPL1 NAO pode vazar para o perfil uefi puro (R6)");
    }

    /** B-6.2b: o builtin `ring1(fn)` executa a funcao Kof em CPL1 e o estado
     *  (um campo estatico) sobrevive; o programa segue no ring0. */
    @Test
    void ring1BuiltinRunsKofFunctionAtCpl1(@TempDir Path tempDir) throws Exception {
        assumeTrue(hasTool("as", "--version") && hasTool("ld", "--version")
                && hasTool("objcopy", "--version"), "toolchain binutils ausente");
        Path bin = buildSource(tempDir, NativeProfile.UEFI_RING, RING1_SRC);
        String text = bootOvmf(tempDir, bin, "KO-RING MAIN");
        assertTrue(text.contains("KO-RING1 CPL1 OK"),
                "OVMF nao provou a entrada CPL1. Fim do log: "
                        + text.substring(Math.max(0, text.length() - 400)));
        assertTrue(text.contains("41"),
                "a funcao Kof `ring1(task)` nao rodou em CPL1 (Mark.value != 41). Fim: "
                        + text.substring(Math.max(0, text.length() - 400)));
        assertTrue(text.contains("KO-RING MAIN"),
                "main nao completou apos o ring1(fn). Fim do log: "
                        + text.substring(Math.max(0, text.length() - 400)));
    }

    /** B-6.3: uma instrucao privilegiada (`cli`) executada em CPL1 levanta #GP,
     *  capturado pelo handler ring0 e reportado — nunca hang silencioso — e a
     *  sabotagem (descritor ring1 zerado) faz a propria entrada CPL1 faltar,
     *  provando que o nivel e ENFORCED, nao decorativo. */
    @Test
    void ring1PrivilegedInstructionAndSabotageProveEnforcement(@TempDir Path tempDir) throws Exception {
        assumeTrue(hasTool("as", "--version") && hasTool("ld", "--version")
                && hasTool("objcopy", "--version"), "toolchain binutils ausente");
        Path bin = build(tempDir, NativeProfile.UEFI_RING);
        String text = bootOvmf(tempDir, bin, "KO-RING MAIN");
        assertTrue(text.contains("KO-RING1 GP OK"),
                "OVMF nao provou o #GP capturado em CPL1. Fim do log: "
                        + text.substring(Math.max(0, text.length() - 400)));
        assertTrue(text.contains("KO-RING1 SABOTAGE OK"),
                "a sabotagem do descritor ring1 nao faltou a entrada CPL1. Fim: "
                        + text.substring(Math.max(0, text.length() - 400)));
        assertTrue(text.contains("KO-RING MAIN"),
                "main nao completou apos as provas B-6.3. Fim do log: "
                        + text.substring(Math.max(0, text.length() - 400)));
    }

    /** B-6.2b: fora do perfil ring, `ring1(fn)` e um gap NOMEADO (NATIVE003) —
     *  nunca silencioso (R6/R7). */
    @Test
    void ring1BuiltinOutsideRingProfileIsNamedGap(@TempDir Path tempDir) throws Exception {
        for (NativeProfile p : new NativeProfile[] { NativeProfile.UEFI, NativeProfile.HOST }) {
            CompilerDriver driver = new CompilerDriver();
            Path source = tempDir.resolve("Main-" + p + ".kf");
            Files.writeString(source, RING1_SRC);
            CompilationResult result = driver.compile(source, tempDir.resolve("out-" + p),
                    Target.NATIVE, p);
            assertFalse(result.success(), "ring1(fn) sob " + p + " devia falhar (NATIVE003)");
            String diags = result.diagnostics().getDiagnostics().toString();
            assertTrue(diags.contains("NATIVE003"),
                    "o gap `ring1(fn)` sob " + p + " devia ser NOMEADO NATIVE003 (R6), veio: " + diags);
        }
    }
}
