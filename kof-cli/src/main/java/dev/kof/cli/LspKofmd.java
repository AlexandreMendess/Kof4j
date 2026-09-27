package dev.kof.cli;

import dev.kof.compiler.CompilationResult;
import dev.kof.compiler.CompilerDriver;
import dev.kof.compiler.Target;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Fatia 3.7 (D-KOFMD, spec docs/spec/kofmd.md §16): o servidor LSP existente
 * ganha o hook `.md` SEM segundo servidor. Toda a politica (parse,
 * vocabulario fechado, forma do hover) vive na lib Kof pura, empacotada como
 * recurso `dev/kof/interop-kofmd.kf` e compilada UMA vez por processo de
 * servidor (JVM-first; outros alvos ficam no MD001 honesto do plano). Esta
 * classe so move bytes: reflexao minima sobre `kofmd.KofmdTool` e o mapa
 * `MD002:<linha>:<sujeito>` -> LSP Diagnostic com range (§14). Motor
 * indisponivel = UM diagnostico-info nomeado, nunca silencio (R6).
 */
final class LspKofmd {

    private LspKofmd() {}

    private static final class Engine {
        final Object tool;
        final Method parse;
        final Method validateVocabulary;
        final Method hoverFor;

        Engine(Class<?> type, ClassLoader loader) throws Exception {
            tool = type.getDeclaredConstructor().newInstance();
            parse = type.getMethod("parse", String.class);
            Class<?> doc = Class.forName("kofmd.KofmdDoc", true, loader);
            validateVocabulary = type.getMethod("validateVocabulary", doc);
            hoverFor = findHover(type);
        }

        private static Method findHover(Class<?> type) throws NoSuchMethodException {
            try {
                return type.getMethod("hoverFor", String.class, int.class, String.class);
            } catch (NoSuchMethodException boxed) {
                return type.getMethod("hoverFor", String.class, Integer.class, String.class);
            }
        }
    }

    private static volatile Engine engine;
    private static volatile boolean bootAttempted;

    static boolean isMd(String uri) {
        return uri != null && uri.toLowerCase(java.util.Locale.ROOT).endsWith(".md");
    }

    static synchronized void resetForTests() {
        engine = null;
        bootAttempted = false;
    }

    static List<Object> diagnostics(String text) {
        List<Object> diagnostics = new ArrayList<>();
        Engine e = engine();
        if (e == null) {
            diagnostics.add(diagnostic(0, "MD001", "kofmd engine unavailable (packaging)"));
            return diagnostics;
        }
        try {
            Object doc = e.parse.invoke(e.tool, text);
            @SuppressWarnings("unchecked")
            List<String> raw = (List<String>) e.validateVocabulary.invoke(e.tool, doc);
            for (String diag : raw) {
                String code = CmdMdCheck.beforeColon(diag);
                String rest = CmdMdCheck.afterFirstColon(diag);
                String lineText = CmdMdCheck.beforeColon(rest);
                String subject = CmdMdCheck.afterFirstColon(rest);
                int line = 1;
                try {
                    line = Integer.parseInt(lineText);
                } catch (NumberFormatException ignored) {
                }
                diagnostics.add(diagnostic(line - 1, code, CmdMdCheck.human(code, subject)));
            }
        } catch (ReflectiveOperationException | ClassCastException ex) {
            diagnostics.clear();
            diagnostics.add(diagnostic(0, "MD001", "kofmd engine failed: " + ex.getMessage()));
        }
        return diagnostics;
    }

    static String hoverAt(String text, int lineOneBased, String word) {
        Engine e = engine();
        if (e == null || word.isEmpty()) {
            return "";
        }
        try {
            Object result = e.hoverFor.invoke(e.tool, text, lineOneBased, word);
            return result == null ? "" : result.toString();
        } catch (ReflectiveOperationException ex) {
            return "";
        }
    }

    private static Map<Object, Object> diagnostic(int line, String code, String message) {
        Map<Object, Object> diag = new LinkedHashMap<>();
        Map<Object, Object> range = new LinkedHashMap<>();
        range.put("start", Map.of("line", (long) line, "character", 0L));
        range.put("end", Map.of("line", (long) line, "character", 80L));
        diag.put("range", range);
        diag.put("severity", code.equals("MD001") ? 3 : 1);
        diag.put("source", "kofmd");
        diag.put("message", code + ": " + message);
        return diag;
    }

    private static Engine engine() {
        if (bootAttempted) {
            return engine;
        }
        synchronized (LspKofmd.class) {
            if (bootAttempted) {
                return engine;
            }
            try {
                engine = boot();
            } catch (Exception e) {
                engine = null;
            }
            bootAttempted = true;
            return engine;
        }
    }

    private static Engine boot() throws Exception {
        Path srcRoot = Files.createTempDirectory("kof-lsp-kofmd-src-");
        // classesRoot vive enquanto o servidor viver: URLClassLoader carrega
        // classes LAZY do disco — apagar o out depois do compile e o bug que
        // so aparece no primeiro uso pos-boot (medido no desenvolvimento).
        Path classesRoot = Files.createTempDirectory("kof-lsp-kofmd-classes-");
        try {
            java.net.URLClassLoader loader = KofmdLibrary.compile(
                    "Engine.kf", "import kofmd.Kofmd\n\nmain() { val keep = true }\n",
                    srcRoot, classesRoot);
            if (loader == null) {
                deleteTree(classesRoot);
                return null;
            }
            Class<?> type = Class.forName("kofmd.KofmdTool", true, loader);
            return new Engine(type, loader);
        } catch (Exception e) {
            deleteTree(classesRoot);
            throw e;
        } finally {
            deleteTree(srcRoot);
        }
    }

    private static void deleteTree(Path root) {
        try (var s = Files.walk(root)) {
            s.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                }
            });
        } catch (IOException ignored) {
        }
    }
}
