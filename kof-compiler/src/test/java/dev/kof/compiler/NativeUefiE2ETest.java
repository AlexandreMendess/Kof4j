package dev.kof.compiler;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
 * B-2 (PLAN-BAREMETAL-BOOT) — perfil UEFI (x86_64): o compilador emite uma
 * aplicação PE32+ ({@code objcopy --target=pei-x86-64 --subsystem=10} sobre o
 * ELF estático do perfil freestanding) com entry MS x64 que imprime via
 * {@code SystemTable->ConOut->OutputString} e sai via
 * {@code BootServices->Exit}. Aceitação do plano: o OVMF (TianoCore) dá boot
 * no {@code \\EFI\\BOOT\\BOOTX64.EFI} num ESP FAT e o hello aparece no console
 * — prova headless pelo serial capturado do qemu (ConOut do OVMF espelha no
 * COM1; medição B-2). A capacidade libc (concurrency) continua RECUSADA
 * (herda o gate NATIVE003 do freestanding).
 *
 * <p>Receita de link medida (não derivada): ELF estático sem PLT/GOT (crt0
 * GNU-EFI via -shared manda a chamada por PLT não-relocado → #UD), dummy
 * {@code .reloc} de 10 bytes (PageRVA=0, BlockSize=10, uma entrada ABSOLUTE —
 * o loader EDK2 recusa dir vazio), {@code ConOut = ST+64} (não ST+56 — o
 * offset 56 é ConsoleOutHandle, um handle, não um ponteiro).
 */
class NativeUefiE2ETest {

    private static final String HELLO = """
            main() {
                println("KO-UEFI OK")
            }
            """;

    private static final String CONCURRENT = """
            Int compute() { return 42 }
            main() {
                val r = spawn compute()
                println(await r)
            }
            """;

    /** B-5 (D-BAREMETAL-BODIES): time.now() pela face UEFI lê um epoch MODERNO
     *  (> 2020) via RuntimeServices->GetTime — não a recusa da fatia anterior. */
    private static final String TIME = """
            main() {
                println(time.now() > 1600000000000)
            }
            """;

    /** B-5: time.sleep() pela face UEFI dorme de verdade (BootServices->Stall) —
     *  provado pela parede: dormir 1.1 s tem de avançar o RTC em >= 1 s. */
    private static final String TIME_SLEEP = """
            main() {
                var before = time.now()
                time.sleep(1100)
                var after = time.now()
                println(after - before >= 1000)
            }
            """;

    /** B-5: random.* pela face UEFI tem corpo REAL (RDRAND/TSC + xorshift64),
     *  não a recusa que travava o app. Duas amostras de 10^9 não colidem
     *  (falso-vermelho ~10^-9). */
    private static final String RANDOM = """
            main() {
                var a = random.randomInt(1000000000)
                var b = random.randomInt(1000000000)
                println(a != b)
            }
            """;

    /** B-5: uma família ainda sem corpo no UEFI (rede → kof_plat_net_*) recusa
     *  de forma NOMEADA (R6) e o app PARA — o spin da recusa impede o
     *  fall-through que crashava o OVMF. */
    private static final String NET_REFUSE = """
            main() {
                println("BEFORE")
                println(http.get("http://example.com"))
                println("AFTER")
            }
            """;

    /** B-5: kof_plat_time_mono no UEFI é real (TSC calibrado por Stall) —
     *  dormir 60 ms dentro de um span dá durationMicros >= 10000. */
    private static final String MONO = """
            main() {
                var h = observability.spanStart("op")
                time.sleep(60)
                var j = observability.spanEnd(h)
                println(j)
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

    /** Prefixo do toolchain UEFI extraído sem root (padrão da cross kof-cross),
     *  ou null se só houver qemu do sistema. */
    private static Path ovmfPrefix() {
        String env = System.getenv("KOF_OVMF_HOME");
        if (env != null && Files.isRegularFile(Path.of(env, "usr/share/OVMF/OVMF_CODE_4M.fd"))) {
            return Path.of(env);
        }
        Path home = Path.of(System.getProperty("user.home"),
                ".local/share/kof-ovmf");
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

    private Path build(Path tempDir, String program, boolean expectSuccess) throws IOException {
        CompilerDriver driver = new CompilerDriver();
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, program);
        Path outDir = tempDir.resolve("out");
        CompilationResult result = driver.compile(source, outDir, Target.NATIVE, NativeProfile.UEFI);
        assertEquals(expectSuccess, result.success(),
                "compile UEFI inesperado: " + result.diagnostics().getDiagnostics());
        return outDir.resolve("Default/Main");
    }

    /** FAT ESP com \EFI\BOOT\BOOTX64.EFI via mtools (superfloppy — o UEFI
     *  monta FAT em LBA0 sem partição). */
    private Path makeEsp(Path tempDir, Path peBinary) throws IOException, InterruptedException {
        Path esp = tempDir.resolve("esp.img");
        run(5_000, "mformat", "-i", esp.toString(), "-C", "-T", "16384", "::");
        run(5_000, "mmd", "-i", esp.toString(), "::/EFI");
        run(5_000, "mmd", "-i", esp.toString(), "::/EFI/BOOT");
        run(10_000, "mcopy", "-i", esp.toString(), peBinary.toString(),
                "::/EFI/BOOT/BOOTX64.EFI");
        return esp;
    }

    private void run(long timeoutMs, String... cmd)
            throws IOException, InterruptedException {
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

    @Test
    void uefiArtifactIsPe32Plus(@TempDir Path tempDir) throws IOException {
        assumeTrue(hasTool("as", "--version") && hasTool("ld", "--version")
                && hasTool("objcopy", "--version"), "toolchain binutils ausente");
        Path bin = build(tempDir, HELLO, true);
        byte[] b = Files.readAllBytes(bin);
        assertTrue(b.length > 2 && b[0] == 'M' && b[1] == 'Z',
                "artefato UEFI deveria ser PE32+ (MZ), tem " + b.length + " bytes");
        int pe = -1;
        for (int i = 0; i + 4 <= b.length && i < 0x200; i++) {
            if (b[i] == 'P' && b[i + 1] == 'E' && b[i + 2] == 0 && b[i + 3] == 0) {
                pe = i;
                break;
            }
        }
        assertTrue(pe >= 0, "assinatura PE ausente");
        // PE32+ = OptionalHeader Magic 0x020B (little-endian, logo após COFF header).
        int magic = (b[pe + 24] & 0xff) | ((b[pe + 25] & 0xff) << 8);
        assertEquals(0x020b, magic, "OptionalHeader Magic deve ser PE32+ (0x020B)");
        // Subsystem EFI application = 10 (offset do Subsystem no PE32+:
        // COFF 20 bytes + OptionalHeader: 112 bytes até Subsystem).
        int subsystem = (b[pe + 24 + 68] & 0xff) | ((b[pe + 24 + 69] & 0xff) << 8);
        assertEquals(10, subsystem, "Subsystem deve ser EFI application (10)");
    }

    @Test
    void uefiRefusesLibcCapabilityWithDiagnostic(@TempDir Path tempDir) throws IOException {
        assumeTrue(hasTool("as", "--version") && hasTool("ld", "--version")
                && hasTool("objcopy", "--version"), "toolchain binutils ausente");
        CompilerDriver driver = new CompilerDriver();
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, CONCURRENT);
        Path outDir = tempDir.resolve("out");
        CompilationResult result = driver.compile(source, outDir, Target.NATIVE, NativeProfile.UEFI);
        assertTrue(!result.success(), "concurrency em UEFI deve ser recusada");
        String diags = result.diagnostics().getDiagnostics().toString();
        assertTrue(diags.contains("NATIVE003"),
                "recusa deve citar NATIVE003, veio: " + diags);
    }

    /** Bota o PE32+ num ESP FAT sob OVMF e devolve o serial capturado, parando
     *  assim que {@code expected} aparece (bounded — lição §418: nunca suíte
     *  pendurada). §511: ESP construída UMA vez (retry de boot não refaz o
     *  mtools sob carga) e até 2 tentativas com espera progress-aware — boot
     *  lento-mas-progressando é preservado (cap 240 s por tentativa), boot
     *  estagnado aborta no idle de 60 s (nunca pior que o fixed antigo de
     *  150 s) e tenta de novo limpo; falha real falha nas duas. */
    private String bootUnderOvmf(Path tempDir, Path bin, String expected) throws Exception {
        Path code = findOvmfCode();
        Path qemu = findQemu();
        assumeTrue(code != null, "OVMF ausente (KOF_OVMF_HOME ou ~/.local/share/kof-ovmf)");
        assumeTrue(qemu != null, "qemu-system-x86_64 ausente");
        assumeTrue(hasTool("mformat", "--help") || hasTool("mformat", "-V"),
                "mtools ausente");
        Path esp = makeEsp(tempDir, bin);
        String text = "";
        for (int attempt = 1; attempt <= 2; attempt++) {
            String t = tryBootUnderOvmf(tempDir, esp, expected, code, qemu, attempt);
            if (ready(t, expected)) return t;
            if (t.length() > text.length()) text = t;
        }
        return text;
    }

    private static boolean ready(String text, String expected) {
        return expected == null
                ? text.contains("true") || text.contains("false")
                : text.contains(expected);
    }

    private String tryBootUnderOvmf(Path tempDir, Path esp, String expected, Path code,
            Path qemu, int attempt) throws Exception {
        Path vars = tempDir.resolve("vars-" + attempt + ".fd");
        Files.copy(code.resolveSibling("OVMF_VARS_4M.fd"), vars);
        Path ser = tempDir.resolve("ser-" + attempt + ".log");

        java.util.List<String> cmd = new java.util.ArrayList<>();
        boolean prefixQemu = ovmfPrefix() != null && qemu.startsWith(ovmfPrefix());
        if (prefixQemu) {
            // qemu do prefixo precisa do ld.so + libs do próprio rootfs
            // (glibc do host mais velha que a do rootfs = crash no start).
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
        try {
            return OvmfSerialWait.untilDone(ser, text -> ready(text, expected), 60_000, 240_000);
        } finally {
            p.destroyForcibly();
            p.waitFor(10, TimeUnit.SECONDS);
        }
    }

    @Test
    void uefiHelloPrintsViaConOutUnderOvmf(@TempDir Path tempDir) throws Exception {
        assumeTrue(hasTool("as", "--version") && hasTool("ld", "--version")
                && hasTool("objcopy", "--version"), "toolchain binutils ausente");
        Path bin = build(tempDir, HELLO, true);
        String text = bootUnderOvmf(tempDir, bin, "KO-UEFI OK");
        assertTrue(text.contains("KO-UEFI OK"),
                "OVMF nao imprimiu 'KO-UEFI OK' no serial. Fim do log: "
                        + text.substring(Math.max(0, text.length() - 400)));
    }

    @Test
    void uefiTimeNowRunsBareUnderOvmf(@TempDir Path tempDir) throws Exception {
        assumeTrue(hasTool("as", "--version") && hasTool("ld", "--version")
                && hasTool("objcopy", "--version"), "toolchain binutils ausente");
        Path bin = build(tempDir, TIME, true);
        String text = bootUnderOvmf(tempDir, bin, null);
        assertTrue(text.contains("true"),
                "time.now() no UEFI nao leu um epoch moderno de GetTime. Log: "
                        + text.substring(Math.max(0, text.length() - 400)));
        assertTrue(!text.contains("false"),
                "time.now() retornou epoch invalido/antigo no UEFI. Log: "
                        + text.substring(Math.max(0, text.length() - 400)));
    }

    @Test
    void uefiSleepAdvancesWallClock(@TempDir Path tempDir) throws Exception {
        assumeTrue(hasTool("as", "--version") && hasTool("ld", "--version")
                && hasTool("objcopy", "--version"), "toolchain binutils ausente");
        Path bin = build(tempDir, TIME_SLEEP, true);
        String text = bootUnderOvmf(tempDir, bin, null);
        assertTrue(text.contains("true"),
                "time.sleep(1100) no UEFI nao avancou o RTC em >= 1 s (Stall nao dormiu). Log: "
                        + text.substring(Math.max(0, text.length() - 400)));
        assertTrue(!text.contains("false"),
                "time.sleep retornou cedo demais no UEFI. Log: "
                        + text.substring(Math.max(0, text.length() - 400)));
    }

    @Test
    void uefiRandomRunsBareUnderOvmf(@TempDir Path tempDir) throws Exception {
        assumeTrue(hasTool("as", "--version") && hasTool("ld", "--version")
                && hasTool("objcopy", "--version"), "toolchain binutils ausente");
        Path bin = build(tempDir, RANDOM, true);
        String text = bootUnderOvmf(tempDir, bin, null);
        assertTrue(text.contains("true"),
                "random.randomInt(10^9) no UEFI deu valores colidentes/sem entropia "
                        + "(ou o app ainda recusa/crasha). Log: "
                        + text.substring(Math.max(0, text.length() - 400)));
        assertTrue(!text.contains("false"),
                "random.* no UEFI colidiu/sem entropia. Log: "
                        + text.substring(Math.max(0, text.length() - 400)));
    }

    @Test
    void uefiNetRefusalStopsWithDiagnostic(@TempDir Path tempDir) throws Exception {
        assumeTrue(hasTool("as", "--version") && hasTool("ld", "--version")
                && hasTool("objcopy", "--version"), "toolchain binutils ausente");
        Path bin = build(tempDir, NET_REFUSE, true);
        String text = bootUnderOvmf(tempDir, bin, "kof_plat_net");
        String tail = text.substring(Math.max(0, text.length() - 600));
        assertTrue(text.contains("kof_plat_net"),
                "recusa NOMEADA da rede no UEFI nao apareceu. Log: " + tail);
        assertTrue(!text.contains("AFTER"),
                "a recusa UEFI nao PAROU o app (fall-through / crash). Log: " + tail);
    }

    @Test
    void uefiMonoSpanDuration(@TempDir Path tempDir) throws Exception {
        assumeTrue(hasTool("as", "--version") && hasTool("ld", "--version")
                && hasTool("objcopy", "--version"), "toolchain binutils ausente");
        Path bin = build(tempDir, MONO, true);
        String text = bootUnderOvmf(tempDir, bin, "durationMicros");
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("\"durationMicros\"\\s*:\\s*(\\d+)").matcher(text);
        String tail = text.substring(Math.max(0, text.length() - 400));
        assertTrue(m.find(), "spanEnd nao trouxe durationMicros no UEFI. Log: " + tail);
        long us = Long.parseLong(m.group(1));
        assertTrue(us >= 10000,
                "mono do UEFI nao mediu o sleep de 60 ms (durationMicros=" + us
                        + ", esperado >= 10000). Log: " + tail);
    }
}
