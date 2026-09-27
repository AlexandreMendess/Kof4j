package dev.kof.cli;

import dev.kof.compiler.CompilationResult;
import dev.kof.compiler.CompilerDriver;
import dev.kof.compiler.Target;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

/**
 * kof md — CLI do front Kofmd (spec docs/spec/kofmd.md §15). Verbo único,
 * dois subcomandos: `check` (parse + validação; exit !=0 em MDxxx) e `format`
 * (reescreve o arquivo na forma canônica, idempotente). `convert` é non-goal
 * declarado (§21) e recusado com honestidade.
 *
 * KOF-first (D-KOF-FIRST-IMPL): toda a política (parse, vocabulário, forma
 * canônica) vive na lib Kof pura `libs/kofmd`; esta classe só fornece o
 * mecanismo de terminal — resolve a lib, compila um main gerado mínimo,
 * executa na JVM (JVM-first: outros alvos = MD001 honesto) e imprime os
 * diagnósticos com file:line (§14).
 */
final class CmdMd {

    private CmdMd() {}

    static int run(String[] args) {
        return run(args, System.out, System.err);
    }

    static int run(String[] args, PrintStream out, PrintStream err) {
        if (args.length < 2) {
            err.println("usage: kof md check <file.md> | kof md format <file.md> | kof md help");
            return 1;
        }
        String verb = args[1];
        switch (verb) {
            case "help" -> {
                out.println("usage: kof md check <file.md>   # parse + validate; non-zero exit on MDxxx");
                out.println("       kof md format <file.md>  # rewrite in canonical form (idempotent)");
                out.println("       convert: non-goal for 0.5.0 (spec kofmd §21) — refused");
                return 0;
            }
            case "convert" -> {
                err.println("md convert: non-goal for 0.5.0 (spec kofmd §21) — honest refusal, not silence");
                return 1;
            }
            case "check", "format" -> { }
            default -> {
                err.println("md: unknown subcommand: " + verb + " (accepts: check format help)");
                return 1;
            }
        }
        if (args.length < 3) {
            err.println("usage: kof md " + verb + " <file.md>");
            return 1;
        }
        for (int i = 3; i < args.length; i++) {
            // R6: flag desconhecida nunca e ignorada em silencio.
            err.println("md: unknown flag: " + args[i] + " (accepts: --help)");
            return 1;
        }
        if ("--help".equals(args[2])) {
            return run(new String[]{"md", "help"}, out, err);
        }
        Path doc = Path.of(args[2]);
        if (!Files.isRegularFile(doc)) {
            err.println("not found: " + doc);
            return 1;
        }
        try {
            return switch (verb) {
                case "check" -> execute(doc, CmdMdCheck::generatedMain, CmdMdCheck::report, out, err);
                default -> execute(doc, CmdMdFormat::generatedMain, CmdMdFormat::report, out, err);
            };
        } catch (Exception e) {
            err.println("md: " + e.getMessage());
            return 1;
        }
    }

    private static int execute(Path doc,
                               java.util.function.Function<Path, String> mainGen,
                               Report report,
                               PrintStream out, PrintStream err) throws Exception {
        Path srcRoot = Files.createTempDirectory("kof-md-src-");
        Path outRoot = Files.createTempDirectory("kof-md-out-");
        try {
            URLClassLoader loader = KofmdLibrary.compile("Main.kf", mainGen.apply(doc), srcRoot, outRoot);
            if (loader == null) {
                err.println("md: MD001 — kofmd library not found (libs/kofmd, $kof.install.dir/lib/kof-libs"
                        + " or packaged resources)");
                return 1;
            }
            ByteArrayOutputStream captured = new ByteArrayOutputStream();
            PrintStream realOut = System.out;
            System.setOut(new PrintStream(captured, true, StandardCharsets.UTF_8));
            try (loader) {
                Class.forName("Default.Main", true, loader)
                        .getMethod("main", String[].class)
                        .invoke(null, (Object) new String[0]);
            } finally {
                System.setOut(realOut);
            }
            return report.report(doc, captured.toString(StandardCharsets.UTF_8), out, err);
        } finally {
            KofCliSupport.cleanup(srcRoot);
            KofCliSupport.cleanup(outRoot);
        }
    }

    static String literal(Path path) {
        return path.toAbsolutePath().toString().replace("\\", "\\\\").replace("\"", "\\\"");
    }

    interface Report {
        int report(Path doc, String output, PrintStream out, PrintStream err);
    }

}
