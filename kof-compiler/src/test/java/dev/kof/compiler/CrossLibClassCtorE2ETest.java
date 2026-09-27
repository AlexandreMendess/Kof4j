package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * §530 — classe de BIBLIOTEOFICIAL com pacote tinha o construtor chamado no
 * cross pelo nome CRU (`GridStyle_init_1`) enquanto a definicao saia
 * package-mangled (`mini_style_GridStyle_init_1`): link quebrava em
 * riscv64/aarch64 para QUALQUER classe vinda de raiz de biblioteca
 * (`kof-libs/`), o caminho que o wiring PR #557 (`libs/pdf`) abriu e o
 * `PdfLibraryE2ETest` (so-JVM) nunca mediu. IRMAO do #628: perder o pacote no
 * simbolo. Fix de mecanismo em `NativeRiscvCrossOps.resolveCalleeNameRiscv`
 * (call-site de CONSTRUCTOR agora usa `NativeSymbolMangling.internalOwner`, a
 * forma canônica do ramo FUNCTION) — sem `if pdf`, sem caso especial.
 */
class CrossLibClassCtorE2ETest {

    @TempDir
    Path tmp;

    private static final String LIB_MAIN =
            "import mini.hello\nimport mini.style.GridStyle\n\nmain() {\n    hello()\n    var g = GridStyle(2)\n    println(g.height())\n}\n";

    private static final String ORACLE = "from lib\n2\n";

    private Path installRoot() throws IOException {
        Path installRoot = tmp.resolve("kof-install");
        Path libDir = installRoot.resolve("lib").resolve("kof-libs").resolve("mini");
        Files.createDirectories(libDir);
        Files.writeString(libDir.resolve("hello.kf"),
                "package mini\nhello() {\n    println(\"from lib\")\n}\n");
        Path styleDir = libDir.resolve("style");
        Files.createDirectories(styleDir);
        Files.writeString(styleDir.resolve("GridStyle.kf"),
                "package mini.style\nclass GridStyle {\n    Int h\n    public constructor(Int h) {\n        this.h = h\n    }\n    Int height() {\n        return h\n    }\n}\n");
        return installRoot;
    }

    private CompilationResult compileWithLibs(Path source, Path out, Target t) throws IOException {
        String prev = System.getProperty("kof.install.dir");
        System.setProperty("kof.install.dir", installRoot().toString());
        try {
            return new CompilerDriver().compile(source, out, t);
        } finally {
            if (prev == null) System.clearProperty("kof.install.dir");
            else System.setProperty("kof.install.dir", prev);
        }
    }

    @Test
    void jvmOracleRunsLibraryClassCtor() throws Exception {
        Path source = tmp.resolve("Main.kf");
        Files.writeString(source, LIB_MAIN);
        Path out = Files.createTempDirectory(tmp, "s530-jvm-");
        CompilationResult r = compileWithLibs(source, out, Target.JVM);
        assertTrue(r.success(), "JVM deve compilar a lib oficial: " + r.diagnostics().getDiagnostics());
        Process p = new ProcessBuilder("java", "-cp", out.toString(), "Default.Main")
                .redirectErrorStream(true).start();
        String outp = drain(p);
        assertEquals(0, p.exitValue(), "saida JVM: '" + outp + "'");
        assertEquals(ORACLE, outp, "oraculo JVM");
    }

    @Test
    void x86LibraryClassCtorLinksAndRuns() throws Exception {
        Path source = tmp.resolve("Main.kf");
        Files.writeString(source, LIB_MAIN);
        Path out = Files.createTempDirectory(tmp, "s530-x86-");
        CompilationResult r = compileWithLibs(source, out, Target.NATIVE);
        assertTrue(r.success(), "x86-64 link da lib (controle positivo pre-fix): "
                + r.diagnostics().getDiagnostics());
        Process p = new ProcessBuilder(out.resolve("Default/Main").toString()).redirectErrorStream(true).start();
        String outp = drain(p);
        assertEquals(0, p.exitValue(), "saida x86: '" + outp + "'");
        assertEquals(ORACLE, outp, "x86 ≡ JVM");
    }

    @Test
    void riscv64LibraryClassCtorLinksAndRuns() throws Exception {
        assumeTrue(has("riscv64-linux-gnu-as", "riscv64-linux-gnu-ld", "qemu-riscv64"), "sem toolchain riscv");
        Path source = tmp.resolve("Main.kf");
        Files.writeString(source, LIB_MAIN);
        Path out = Files.createTempDirectory(tmp, "s530-riscv-");
        CompilationResult r = compileWithLibs(source, out, Target.NATIVE_RISCV64);
        assertTrue(r.success(), "§530 RED pre-fix: riscv64-ld undefined reference to `GridStyle_init_1` — "
                + r.diagnostics().getDiagnostics());
        Process p = NativeRiscv64E2ETest.qemu("riscv64", out.resolve("Default/Main"))
                .redirectErrorStream(true).start();
        String outp = drain(p);
        assertEquals(0, p.exitValue(), "saida riscv: '" + outp + "'");
        assertEquals(ORACLE, outp, "riscv64 ≡ JVM");
    }

    @Test
    void aarch64LibraryClassCtorLinksAndRuns() throws Exception {
        assumeTrue(has("aarch64-linux-gnu-as", "aarch64-linux-gnu-ld", "qemu-aarch64"), "sem toolchain aarch");
        Path source = tmp.resolve("Main.kf");
        Files.writeString(source, LIB_MAIN);
        Path out = Files.createTempDirectory(tmp, "s530-aarch-");
        CompilationResult r = compileWithLibs(source, out, Target.NATIVE_AARCH64);
        assertTrue(r.success(), "§530 RED pre-fix: aarch64-ld undefined reference ao init do ctor da lib — "
                + r.diagnostics().getDiagnostics());
        Process p = NativeAarch64E2ETest.qemu("aarch64", out.resolve("Default/Main"))
                .redirectErrorStream(true).start();
        String outp = drain(p);
        assertEquals(0, p.exitValue(), "saida aarch: '" + outp + "'");
        assertEquals(ORACLE, outp, "aarch64 ≡ JVM");
    }

    private static String drain(Process p) throws IOException, InterruptedException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        p.getInputStream().transferTo(bos);
        assertTrue(p.waitFor(60, TimeUnit.SECONDS), "processo nao terminou");
        return bos.toString(StandardCharsets.UTF_8);
    }

    private static boolean has(String... bins) throws Exception {
        for (String b : bins) {
            Process p = new ProcessBuilder("which", b).start();
            if (p.waitFor() != 0) return false;
        }
        return true;
    }
}
