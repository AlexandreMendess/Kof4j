package dev.kof.c;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * KofCcompiler — native-only C subset compiler.
 * Input: .c file with subset grammar.
 * Output: ELF64 executable via GAS + LD, no alvo escolhido, or a reusable
 * object ({@link #compileObject}) for the cross FFI fixtures.
 * No JVM target.
 */
public final class KofCCompiler {

    public record CompileResult(boolean success, String diagnostics, Path binary) {}

    /** Compatibilidade: sem alvo explícito, emite para o host (x86_64). */
    public static CompileResult compile(Path cFile, Path outDir) throws IOException {
        return compile(cFile, outDir, KofCTarget.X86_64);
    }

    public static CompileResult compile(Path cFile, Path outDir, KofCTarget target) throws IOException {
        return compile(cFile, outDir, target, List.of());
    }

    /**
     * Links an executable, optionally pulling extra objects (a cross fixture
     * built with {@link #compileObject}) into the {@code ld} invocation.
     */
    public static CompileResult compile(Path cFile, Path outDir, KofCTarget target,
                                        List<Path> extraObjects) throws IOException {
        FrontEnd fe = parse(cFile);
        if (!fe.ok()) return new CompileResult(false, fe.diagnostics(), null);

        boolean hasMain = fe.prog().funcs().stream().anyMatch(f -> f.name().equals("main"));
        if (!hasMain) {
            return new CompileResult(false, "missing main() function", null);
        }

        String asm = emitter(fe.prog(), target, true).emit();

        Files.createDirectories(outDir);
        Path sFile = outDir.resolve("kofc.s");
        Files.writeString(sFile, asm);

        Path oFile = outDir.resolve("kofc.o");
        Path bin = outDir.resolve("kofc_bin");

        CompileResult as = assemble(sFile, oFile, target);
        if (!as.success()) return new CompileResult(false, as.diagnostics() + "\n" + asm, null);

        // ld — freestanding (`-e _start`), com o linker do alvo.
        List<String> ldCmd = new ArrayList<>(List.of(
                target.linker(), "-o", bin.toString(), "-e", "_start", oFile.toString()));
        for (Path extra : extraObjects) ldCmd.add(extra.toString());
        ProcessBuilder pbLd = new ProcessBuilder(ldCmd);
        pbLd.redirectErrorStream(true);
        Process pLd = pbLd.start();
        String ldOut = new String(pLd.getInputStream().readAllBytes());
        try { pLd.waitFor(5, TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        if (pLd.exitValue() != 0) {
            if (target != KofCTarget.X86_64) {
                return new CompileResult(false, "ld failed: " + ldOut + "\n" + asm, null);
            }
            // fallback to gcc (só host x86_64 — cross não tem cc)
            List<String> gccCmd = new ArrayList<>(List.of("gcc", "-nostdlib", "-o", bin.toString(), oFile.toString()));
            for (Path extra : extraObjects) gccCmd.add(extra.toString());
            ProcessBuilder pbGcc = new ProcessBuilder(gccCmd);
            pbGcc.redirectErrorStream(true);
            Process pGcc = pbGcc.start();
            String gccOut = new String(pGcc.getInputStream().readAllBytes());
            try { pGcc.waitFor(5, TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            if (pGcc.exitValue() != 0) {
                return new CompileResult(false, "ld failed: " + ldOut + "\ngcc failed: " + gccOut + "\n" + asm, null);
            }
        }
        bin.toFile().setExecutable(true);
        return new CompileResult(true, "", bin);
    }

    /**
     * Emits a reusable object ({@code .o}) for {@code target} — no {@code _start},
     * no {@code main} requirement. The FFI cross tests link it into the program
     * so a C fixture with by-value struct parameters can be consumed.
     */
    public static CompileResult compileObject(Path cFile, Path oFile, KofCTarget target) throws IOException {
        FrontEnd fe = parse(cFile);
        if (!fe.ok()) return new CompileResult(false, fe.diagnostics(), null);

        String asm = emitter(fe.prog(), target, false).emit();

        Path parent = oFile.toAbsolutePath().getParent();
        if (parent != null) Files.createDirectories(parent);
        Path sFile = oFile.resolveSibling(oFile.getFileName() + ".s");
        Files.writeString(sFile, asm);

        CompileResult as = assemble(sFile, oFile, target);
        if (!as.success()) return new CompileResult(false, as.diagnostics() + "\n" + asm, null);
        return new CompileResult(true, "", oFile);
    }

    private static KofCEmitter emitter(KofCAst.Program prog, KofCTarget target, boolean executable) {
        return switch (target) {
            case X86_64 -> new KofCEmitterX86(prog, executable);
            case RISCV64 -> new KofCEmitterRiscv(prog, executable);
            case AARCH64 -> new KofCEmitterAarch(prog, executable);
        };
    }

    private static CompileResult assemble(Path sFile, Path oFile, KofCTarget target) throws IOException {
        List<String> asCmd = new ArrayList<>(target.assembler());
        asCmd.add("-o");
        asCmd.add(oFile.toString());
        asCmd.add(sFile.toString());
        ProcessBuilder pbAs = new ProcessBuilder(asCmd);
        pbAs.redirectErrorStream(true);
        Process pAs = pbAs.start();
        String asOut = new String(pAs.getInputStream().readAllBytes());
        try { pAs.waitFor(5, TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        if (pAs.exitValue() != 0) return new CompileResult(false, "as failed: " + asOut, null);
        return new CompileResult(true, "", oFile);
    }

    private record FrontEnd(KofCAst.Program prog, List<String> errors) {
        boolean ok() { return errors.isEmpty(); }
        String diagnostics() { return String.join("\n", errors); }
    }

    private static FrontEnd parse(Path cFile) throws IOException {
        String src = Files.readString(cFile);
        var parser = new KofCParser(new KofCLexer(src).lex());
        KofCAst.Program prog = parser.parseProgram();
        // never emit a binary from a junk AST (R6/Q7)
        return new FrontEnd(prog, parser.errors());
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.err.println("Usage: KofCCompiler <file.c> [-o outDir] [--target x86_64|riscv64|aarch64] [-c]");
            System.exit(1);
        }
        Path cFile = Path.of(args[0]);
        Path outDir = Files.createTempDirectory("kofc-out");
        KofCTarget target = KofCTarget.X86_64;
        boolean objectOnly = false;
        for (int i = 1; i < args.length; i++) {
            if (args[i].equals("-o") && i + 1 < args.length) {
                outDir = Path.of(args[++i]);
            } else if (args[i].equals("--target") && i + 1 < args.length) {
                target = KofCTarget.parse(args[++i]);
            } else if (args[i].equals("-c")) {
                objectOnly = true;
            }
        }
        CompileResult res;
        if (objectOnly) {
            Path o = outDir.resolve(cFile.getFileName().toString().replaceFirst("\\.c$", "") + ".o");
            res = compileObject(cFile, o, target);
        } else {
            res = compile(cFile, outDir, target);
        }
        if (!res.success()) {
            System.err.println(res.diagnostics());
            System.exit(1);
        }
        System.out.println("built " + res.binary());
    }
}
