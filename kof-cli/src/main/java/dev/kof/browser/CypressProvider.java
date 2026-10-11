package dev.kof.browser;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * §6.3 Cypress provider (the testing-platform plan, `D-TESTING-PLATFORM`) —
 * the SECOND browser backend behind the SPI, over a REAL cypress binary. The
 * binary is an npm tool: the repo NEVER bundles it (the same per-project
 * opt-in policy as the Playwright browsers); the probe sweeps the Kof testing
 * cache (`~/.cache/kof-testing/cypress`), the PATH and the project-local
 * `node_modules`.
 *
 * <p>§6.3's rule governs the shape: Cypress's execution model is NOT
 * Playwright's, so this provider does not pretend otherwise — each call runs
 * a GENERATED ad-hoc spec through `cypress run --headless` (real browser
 * execution: a swept Chromium, or Electron as the honest fallback) and
 * returns the real artifact (rendered `outerHTML`, real PNG). Cypress-only
 * semantics (its spec runner, fixtures, network stubs) stay Cypress's domain;
 * the interactive faces are `capability unsupported` for this backend — a
 * per-call spec generation is the documented boundary, never a silent
 * pretend-equivalence.
 */
public final class CypressProvider implements BrowserProvider {

    @Override
    public String name() {
        return "cypress";
    }

    /** Varredura honesta do binário: cache do Kof → PATH → node_modules do
     *  projeto. null = não há Cypress (o gate pula com o motivo nomeado, Q7). */
    static Path findCypress() {
        Path cache = Path.of(System.getProperty("user.home"),
                ".cache", "kof-testing", "cypress", "node_modules", ".bin", "cypress");
        if (Files.isExecutable(cache)) return cache;
        String pathEnv = System.getenv("PATH");
        if (pathEnv != null) {
            for (String dir : pathEnv.split(java.util.regex.Pattern.quote(File.pathSeparator))) {
                Path hit = Path.of(dir, "cypress");
                if (Files.isExecutable(hit)) return hit;
            }
        }
        Path local = Path.of("node_modules", ".bin", "cypress");
        return Files.isExecutable(local) ? local : null;
    }

    @Override
    public String probe() {
        Path bin = findCypress();
        if (bin == null) return null;
        try {
            String out = exec(new ProcessBuilder(bin.toString(), "--version"), 60);
            // `cypress --version` imprime as linhas package+binary — a última
            // não-vazia é a versão do binário efetivo.
            return java.util.Arrays.stream(out.split("\n"))
                    .map(String::trim).filter(s -> !s.isEmpty()).reduce((a, b) -> b)
                    .map(last -> "cypress " + last).orElse(null);
        } catch (Exception e) {
            return null;
        }
    }

    /** Medido (protótipo §6.3): o `cy.screenshot` trava em Electron headless
     *  sem GPU nesta classe de host; o Chromium varrido executa limpo. O
     *  Electron segue como fallback honesto (o dumpDom foi provado nele). */
    static String resolveBrowser() {
        Path chrome = PlaywrightLibProvider.findPlaywrightChromium();
        if (chrome != null) return chrome.toString();
        for (String name : new String[]{"google-chrome", "chromium", "chromium-browser"}) {
            Path hit = findInPath(name);
            if (hit != null) return hit.toString();
        }
        return "electron";
    }

    private static Path findInPath(String name) {
        String pathEnv = System.getenv("PATH");
        if (pathEnv == null) return null;
        for (String dir : pathEnv.split(java.util.regex.Pattern.quote(File.pathSeparator))) {
            Path p = Path.of(dir, name);
            if (Files.isExecutable(p)) return p;
        }
        return null;
    }

    @Override
    public String dumpDom(String url) throws Exception {
        Path proj = Files.createTempDirectory("kof-cypress");
        try {
            writeConfig(proj, true);
            Path spec = writeSpec(proj, "kof-dump.cy.js", """
                    it('kof dumpDom', () => {
                      cy.visit(%s, { timeout: 15000 })
                      cy.document().then((d) => { cy.task('dumpDom', d.documentElement.outerHTML) })
                    })
                    """.formatted(jsQuote(url)));
            String out = run(proj, spec);
            Path dump = proj.resolve("dump.html");
            if (!Files.isRegularFile(dump)) {
                throw new IllegalStateException("cypress run produced no DOM dump:\n" + tail(out));
            }
            return Files.readString(dump);
        } finally {
            deleteRecursively(proj);
        }
    }

    @Override
    public void screenshot(String url, String path) throws Exception {
        Path proj = Files.createTempDirectory("kof-cypress");
        try {
            writeConfig(proj, false);
            Path spec = writeSpec(proj, "kof-shot.cy.js", """
                    it('kof screenshot', () => {
                      cy.visit(%s, { timeout: 15000 })
                      cy.screenshot('kof-shot', { overwrite: true, capture: 'viewport', timeout: 60000 })
                    })
                    """.formatted(jsQuote(url)));
            String out = run(proj, spec);
            Path shots = proj.resolve("cypress").resolve("screenshots");
            if (!Files.isDirectory(shots)) {
                throw new IllegalStateException(
                        "cypress run produced no screenshots dir:\n" + tail(out));
            }
            try (Stream<Path> walk = Files.walk(shots)) {
                Path png = walk.filter(p -> p.toString().endsWith(".png")).findFirst().orElse(null);
                if (png == null) {
                    throw new IllegalStateException(
                            "cypress run produced no screenshot:\n" + tail(out));
                }
                Files.move(png, Path.of(path), StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            deleteRecursively(proj);
        }
    }

    /** O scaffold mínimo por chamada: config sem `require('cypress')` (o
     *  projeto efêmero não tem node_modules — o objeto puro basta) + spec. */
    private static void writeConfig(Path proj, boolean withDumpTask) throws Exception {
        String task = withDumpTask ? """
                    setupNodeEvents(on) {
                      on('task', {
                        dumpDom(html) {
                          require('fs').writeFileSync(%s, String(html))
                          return null
                        },
                      })
                    },
                """.formatted(jsQuote(proj.resolve("dump.html").toString())) : "";
        String config = """
                module.exports = {
                  e2e: {
                    supportFile: false,
                    screenshotOnRunFailure: false,
                %s  },
                }
                """.formatted(task);
        Files.writeString(proj.resolve("cypress.config.js"), config);
    }

    private static Path writeSpec(Path proj, String name, String body) throws Exception {
        Path dir = proj.resolve("cypress").resolve("e2e");
        Files.createDirectories(dir);
        Path spec = dir.resolve(name);
        Files.writeString(spec, body);
        return spec;
    }

    private String run(Path proj, Path spec) throws Exception {
        Path bin = findCypress();
        if (bin == null) {
            throw new IllegalStateException("no cypress binary found (Kof cache, PATH, node_modules swept)");
        }
        ProcessBuilder pb = new ProcessBuilder(bin.toString(), "run", "--headless",
                "--browser", resolveBrowser(), "--project", proj.toString(),
                "--spec", spec.toString());
        String out = exec(pb, 300);
        if (out.contains("All specs passed") || Files.isRegularFile(proj.resolve("dump.html"))) {
            return out;
        }
        throw new IllegalStateException("cypress run failed:\n" + tail(out));
    }

    private static String exec(ProcessBuilder pb, int timeoutSeconds) throws Exception {
        pb.redirectErrorStream(true);
        Process p = pb.start();
        boolean done = p.waitFor(timeoutSeconds, TimeUnit.SECONDS);
        if (!done) {
            p.destroyForcibly();
            throw new IllegalStateException(pb.command().get(0) + " did not finish in " + timeoutSeconds + "s");
        }
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (p.exitValue() != 0 && out.isBlank()) {
            throw new IllegalStateException(pb.command().get(0) + " exit " + p.exitValue());
        }
        return out;
    }

    /** O literal JS COMPLETO — com as aspas: 'path'. Os templates NÃO
     *  colocam aspas ao redor de %s (a responsabilidade é daqui). */
    private static String jsQuote(String s) {
        return "'" + s.replace("\\", "\\\\").replace("'", "\\'") + "'";
    }

    private static String tail(String s) {
        if (s == null) return "<nulo>";
        if (s.length() <= 1800) return s;
        return s.substring(0, 600) + "\n…[meio cortado]…\n" + s.substring(s.length() - 1200);
    }

    private static void deleteRecursively(Path dir) {
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (Exception ignored) {
                    // best-effort cleanup of an ephemeral scaffold (Q7: it is
                    // a temp dir; a leftover never affects a result)
                }
            });
        } catch (Exception ignored) {
            // same: the scaffold is ephemeral
        }
    }
}
