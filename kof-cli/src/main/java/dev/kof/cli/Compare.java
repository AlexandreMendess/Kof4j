package dev.kof.cli;

import dev.kof.compiler.CompilationResult;
import dev.kof.compiler.CompilerDriver;
import dev.kof.compiler.Target;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * `kof compare` — differential testing of a migration (docs/development/
 * LEGACY_MIGRATION.md §8, Fase G).
 *
 * Runs a legacy program (a {@code .class} with a {@code main}, or a
 * {@code .jar}) and a Kof program with the same stdin/args, then compares the
 * observable behavior (stdout, stderr, exit code). Divergence is reported as
 * structured data, never a silent pass/fail.
 */
public final class Compare {

    private Compare() {
    }

    private static final String USAGE =
            "usage: kof compare <legacy.class|legacy.jar> <file.kf> [--stdin <text>] [--arg <v>...] [--json]";

    record RunResult(int exitCode, String stdout, String stderr, Map<String, String> files) {
        boolean sameStdout(RunResult o) { return stdout.equals(o.stdout); }
        boolean sameStderr(RunResult o) { return stderr.equals(o.stderr); }
        boolean sameExit(RunResult o) { return exitCode == o.exitCode; }
        boolean sameFiles(RunResult o) { return files.equals(o.files); }
    }

    enum Channel { EQUIVALENT, DIVERGENT }

    record Verdict(Channel stdout, Channel stderr, Channel exit, Channel files, Channel exceptions) {
        boolean equivalent() {
            return stdout == Channel.EQUIVALENT && exit == Channel.EQUIVALENT
                    && files == Channel.EQUIVALENT && exceptions == Channel.EQUIVALENT;
        }

        Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("stdout", stdout == Channel.EQUIVALENT ? "equivalent" : "divergent");
            m.put("stderr", stderr == Channel.EQUIVALENT ? "equivalent" : "divergent");
            m.put("exit", exit == Channel.EQUIVALENT ? "equivalent" : "divergent");
            m.put("files", files == Channel.EQUIVALENT ? "equivalent" : "divergent");
            m.put("exceptions", exceptions == Channel.EQUIVALENT ? "equivalent" : "divergent");
            m.put("overall", equivalent() ? "equivalent" : "divergent");
            return m;
        }
    }

    public static int run(String[] args) {
        if (args.length > 0 && "compare".equals(args[0])) {
            args = java.util.Arrays.copyOfRange(args, 1, args.length);
        }
        if (args.length == 0 || "--help".equals(args[0]) || "-h".equals(args[0])) {
            System.out.println(USAGE);
            System.out.println("  runs both programs with identical inputs and compares stdout/stderr/exit code");
            return 0;
        }
        if (args.length < 2) {
            System.err.println(USAGE);
            return 1;
        }
        Path legacy = Path.of(args[0]);
        Path kofFile = Path.of(args[1]);
        boolean json = false;
        String stdin = null;
        List<String> programArgs = new ArrayList<>();
        for (int i = 2; i < args.length; i++) {
            switch (args[i]) {
                case "--json" -> json = true;
                // CodeQL java/index-out-of-bounds: flag sem valor lia
                // args[++i] sem checar o fim (AIOOBE em vez de usage).
                case "--stdin" -> {
                    if (i + 1 >= args.length) {
                        System.err.println("missing value for --stdin");
                        System.err.println(USAGE);
                        return 1;
                    }
                    stdin = args[++i];
                }
                case "--arg" -> {
                    if (i + 1 >= args.length) {
                        System.err.println("missing value for --arg");
                        System.err.println(USAGE);
                        return 1;
                    }
                    programArgs.add(args[++i]);
                }
                default -> {
                    // #431: unknown option must be fatal (was: printed, then ran & could exit 0).
                    System.err.println("unknown option: " + args[i]);
                    System.err.println(USAGE);
                    return 1;
                }
            }
        }
        try {
            RunResult legacyRes = runLegacy(legacy, programArgs, stdin);
            RunResult kofRes = runKof(kofFile, programArgs, stdin);
            Verdict verdict = compare(legacyRes, kofRes);

            if (json) {
                Map<String, Object> out = new LinkedHashMap<>();
                out.put("legacy", runToMap(legacyRes));
                out.put("kof", runToMap(kofRes));
                out.put("verdict", verdict.toMap());
                System.out.println(Json.stringify(out));
            } else {
                System.out.println("legacy exit=" + legacyRes.exitCode + " stdout=" + abbrev(legacyRes.stdout));
                System.out.println("kof    exit=" + kofRes.exitCode + " stdout=" + abbrev(kofRes.stdout));
                System.out.println("verdict: " + (verdict.equivalent() ? "EQUIVALENT" : "DIVERGENT")
                        + "  (stdout=" + verdict.stdout + ", stderr=" + verdict.stderr
                        + ", exit=" + verdict.exit + ", files=" + verdict.files + ")");
            }
            return verdict.equivalent() ? 0 : 1;
        } catch (Exception e) {
            System.err.println("kof compare: " + e.getMessage());
            return 2;
        }
    }

    static Verdict compare(RunResult legacy, RunResult kof) {
        String legacyEx = exceptionType(legacy.stderr());
        String kofEx = exceptionType(kof.stderr());
        Channel exceptions;
        if (legacyEx == null && kofEx == null) {
            exceptions = Channel.EQUIVALENT;
        } else if (legacyEx == null || kofEx == null) {
            // one side threw, the other exited cleanly: the failure MODE diverged
            exceptions = Channel.DIVERGENT;
        } else {
            // §8 typed-exception face: the TYPE is the observable — the raw
            // stack text carries environment paths/line numbers (noise)
            exceptions = legacyEx.equals(kofEx) ? Channel.EQUIVALENT : Channel.DIVERGENT;
        }
        return new Verdict(
                legacy.sameStdout(kof) ? Channel.EQUIVALENT : Channel.DIVERGENT,
                legacy.sameStderr(kof) ? Channel.EQUIVALENT : Channel.DIVERGENT,
                legacy.sameExit(kof) ? Channel.EQUIVALENT : Channel.DIVERGENT,
                legacy.sameFiles(kof) ? Channel.EQUIVALENT : Channel.DIVERGENT,
                exceptions);
    }

    /**
     * Fase G (§8): the first exception/error TYPE in a stderr stream — the
     * JVM trace's first line carries it fully qualified
     * ("Exception in thread \"main\" java.lang.ArithmeticException: ...").
     * Null when the run threw nothing. JVM-target format (the compare runs
     * the JVM target on both sides); JS/native trace formats are a future face.
     */
    static String exceptionType(String stderr) {
        if (stderr == null || stderr.isBlank()) {
            return null;
        }
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("[a-z][a-z0-9_]*(?:\\.[A-Za-z0-9_]+)+")
                .matcher(stderr.lines().filter(l -> l.contains("Exception") || l.contains("Error"))
                        .findFirst().orElse(""));
        return m.find() ? m.group() : null;
    }

    static RunResult runLegacy(Path legacy, List<String> args, String stdin) throws IOException {
        return runLegacy(legacy, args, stdin, null);
    }

    /**
     * Fase G (§8): overloads com classpath extra — o driver JDBC provisionado
     * (`DbDrivers`) para as faces de mutação de DB; os DOIS lados recebem o
     * MESMO classpath para o vetor ser idêntico.
     */
    static RunResult runLegacy(Path legacy, List<String> args, String stdin, String extraCp) throws IOException {
        String file = legacy.getFileName().toString();
        Path sandbox = Files.createTempDirectory("kof-compare-legacy-");
        try {
            if (file.endsWith(".jar")) {
                return exec(Paths.java("-jar", legacy.toString()), args, stdin, sandbox);
            }
            if (file.endsWith(".class")) {
                String className = file.substring(0, file.length() - ".class".length());
                String cp = extraCp == null ? legacy.getParent().toString()
                        : legacy.getParent().toString() + File.pathSeparator + extraCp;
                List<String> cmd = Paths.java("-cp", cp, className);
                return exec(cmd, args, stdin, sandbox);
            }
            throw new IOException("legacy must be a .class or .jar file");
        } finally {
            cleanup(sandbox);
        }
    }

    static RunResult runKof(Path kofFile, List<String> args, String stdin) throws IOException {
        return runKof(kofFile, "Default.Main", args, stdin);
    }

    /**
     * Fase H (§8.1): a migração gera classes com NOMES PRÓPRIOS (`class Hello`
     * com um main estático) — a entrada compilada é `Default.Hello`, não o
     * `Default.Main` do fluxo top-level. A sobrecarga nomeia a entrada.
     */
    static RunResult runKof(Path kofFile, String entryClass, List<String> args, String stdin) throws IOException {
        return runKof(kofFile, entryClass, args, stdin, null);
    }

    static RunResult runKof(Path kofFile, String entryClass, List<String> args, String stdin,
                            String extraCp) throws IOException {
        Path outDir = Files.createTempDirectory("kof-compare-");
        Path sandbox = Files.createTempDirectory("kof-compare-kof-");
        CompilerDriver driver = new CompilerDriver();
        CompilationResult result = driver.compile(kofFile, outDir, Target.JVM);
        if (!result.success()) {
            throw new IOException("Kof compile failed: " + result.diagnostics().getDiagnostics());
        }
        try {
            String cp = extraCp == null ? outDir.toString()
                    : outDir.toString() + File.pathSeparator + extraCp;
            return exec(Paths.java("-cp", cp, entryClass), args, stdin, sandbox);
        } finally {
            cleanup(outDir);
            cleanup(sandbox);
        }
    }

    private static RunResult exec(List<String> cmd, List<String> args, String stdin, Path sandbox) throws IOException {
        List<String> full = new ArrayList<>(cmd);
        full.addAll(args);
        ProcessBuilder pb = new ProcessBuilder(full);
        pb.directory(sandbox.toFile());
        pb.redirectErrorStream(false);
        Process p = pb.start();
        if (stdin != null) {
            try (OutputStream os = p.getOutputStream()) {
                os.write(stdin.getBytes(StandardCharsets.UTF_8));
            }
        }
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
        String err = new String(p.getErrorStream().readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
        try {
            p.waitFor();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        Map<String, String> files = captureFiles(sandbox);
        return new RunResult(p.exitValue(), out.trim(), err.trim(), files);
    }

    /** Snapshot dos arquivos criados no sandbox: caminho relativo → hash SHA-256. */
    private static Map<String, String> captureFiles(Path dir) throws IOException {
        Map<String, String> files = new java.util.TreeMap<>();
        try (var s = Files.walk(dir)) {
            for (Path p : s.filter(Files::isRegularFile)
                    // MEASURED 14/10: an H2 FILE database run twice with the
                    // identical program produces DIFFERENT `.mv.db` bytes (the
                    // store header carries creation) — the store is
                    // driver-internal, not program output; the DB observable
                    // is the query digest in stdout. Without the exclusion
                    // equivalent DB programs compare files-divergent.
                    .filter(p -> !p.getFileName().toString().endsWith(".mv.db")
                            && !p.getFileName().toString().endsWith(".trace.db")
                            && !p.getFileName().toString().endsWith(".lock.db"))
                    .toList()) {
                String rel = dir.relativize(p).toString().replace('\\', '/');
                files.put(rel, sha256(Files.readAllBytes(p)));
            }
        }
        return files;
    }

    private static String sha256(byte[] data) {
        try {
            byte[] h = java.security.MessageDigest.getInstance("SHA-256").digest(data);
            StringBuilder sb = new StringBuilder();
            for (byte b : h) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            return Integer.toHexString(java.util.Arrays.hashCode(data));
        }
    }

    private static Map<String, Object> runToMap(RunResult r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("exit", r.exitCode);
        m.put("stdout", r.stdout);
        m.put("stderr", r.stderr);
        m.put("files", r.files);
        return m;
    }

    private static String abbrev(String s) {
        String oneLine = s.replace('\n', ' ');
        return oneLine.length() > 60 ? oneLine.substring(0, 60) + "…" : oneLine;
    }

    static void cleanup(Path dir) {
        try (var s = Files.walk(dir)) {
            s.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                try { Files.deleteIfExists(p); } catch (IOException ignored) {}
            });
        } catch (IOException ignored) {
        }
    }

    private static final class Paths {
        /** java + saída UTF-8: exec() lê stdout/stderr como UTF-8 (KofStdio). */
        static List<String> java(String... args) {
            List<String> cmd = new ArrayList<>();
            cmd.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
            cmd.addAll(KofStdio.capturedJvmFlags());
            cmd.addAll(List.of(args));
            return cmd;
        }
    }
}