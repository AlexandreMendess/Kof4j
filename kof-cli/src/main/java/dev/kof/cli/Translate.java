package dev.kof.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * `kof translate` — translate a subset of Java source into idiomatic Kof
 * (docs/development/TRANSLATOR.md, Fase F).
 *
 * Understands structure (classes, fields, methods, control flow) rather than
 * doing textual substitution. Static methods become top-level functions;
 * {@code System.out.println} becomes {@code println}; {@code a.equals(b)}
 * becomes {@code a == b}; {@code new X(...)} drops {@code new}.
 */
public final class Translate {

    private Translate() {
    }

    public static int run(String[] args) {
        if (args.length > 0 && "translate".equals(args[0])) {
            args = java.util.Arrays.copyOfRange(args, 1, args.length);
        }
        if (args.length == 0) {
            System.err.println("usage: kof translate <file.java> [--output <file.kf>]");
            return 1;
        }
        Path file = Path.of(args[0]);
        String outArg = optionValue(args, "--output");
        Path outFile = outArg != null ? Path.of(outArg) : null;
        if (!Files.isRegularFile(file)) {
            System.err.println("file not found: " + file);
            return 1;
        }
        try {
            String kof = translateJava(Files.readString(file));
            if (outFile != null) {
                Files.writeString(outFile, kof);
                System.out.println("translated to " + outFile);
            } else {
                System.out.print(kof);
            }
            return 0;
        } catch (TranslateException e) {
            System.err.println("kof translate: " + e.getMessage());
            return 1;
        } catch (IOException e) {
            System.err.println("kof translate: " + e.getMessage());
            return 1;
        }
    }

    // ── public API for tests ──────────────────────────────────────────────

    static String translateJava(String source) {
        List<Tok> toks = TranslateLexer.lex(source);
        Parser p = new Parser(toks);
        return new TranslateEmitter(p).translate();
    }


    private static String optionValue(String[] args, String opt) {
        for (int i = 0; i < args.length - 1; i++) {
            if (opt.equals(args[i])) return args[i + 1];
        }
        return null;
    }
}
