package dev.kof.compiler;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * §653 — fora do host GraalJS, `kof_platform` é o shim #104, cujo trap `get`
 * devolve uma função LANÇADORA para QUALQUER propriedade ausente. Isso tornava
 * `typeof kof_platform.pbkdf2Hex === "function"` VERDADEIRO no hostless, de modo
 * que `kofSecPasswordVerify` tomava o ramo de plataforma, o shim lançava e o
 * `catch { return 0; }` engolia tudo num `false` SILENCIOSO — o fallback
 * puro-JS (o mesmo PBKDF2 que o Script/JVM usam) nunca era alcançado. Verificar
 * um hash VÁLIDO no JS dava `false` (falso-negativo de segurança).
 *
 * A correção troca a detecção por PRESENÇA (`"pbkdf2Hex" in kof_platform`), que
 * não dispara o trap `get` do shim. Estes testes rodam o artefato JS SEM
 * `kof_platform` no global (como Node/browser puro) e exigem o resultado do
 * ORÁCULO — o mesmo que o JVM e o Script produzem.
 */
class KofJsPasswordVerifyHostlessTest {

    private final CompilerDriver driver = new CompilerDriver();

    // Hash conhecido de "hunter2": pbkdf2$sha256$1000$<salt b64>$<dk b64>.
    // 1000 iterações (não 600000) para o PBKDF2 puro-JS rodar rápido no Graal.
    private static final String KNOWN_HASH =
            "pbkdf2$sha256$1000$ABEiM0RVZneImaq7zN3u/w==$dWX01m72z6r8MAgI5bdcNsMgEkSUhpes+9NJriHBDWM=";

    @Test
    void verifyKnownHashOutsideHostMatchesOracleNotSilentFalse(@TempDir Path tmp) throws Exception {
        String out = runHostless(tmp, """
            main() {
                var h = "%s"
                println(passwords.verify("hunter2", h))
                println(passwords.verify("wrong", h))
            }
            """.formatted(KNOWN_HASH));
        assertEquals("true\nfalse", out.trim(),
                "verify hostless deve bater com o oráculo JVM/Script; 'false' p/ o hash válido = §653");
    }

    @Test
    void verifyMalformedHashOutsideHostIsFalseNotCrash(@TempDir Path tmp) throws Exception {
        String out = runHostless(tmp, """
            main() {
                println(passwords.verify("hunter2", "not-a-hash"))
            }
            """);
        assertEquals("false", out.trim(),
                "hash malformado = false honesto, nunca crash");
    }

    private String runHostless(Path tempDir, String source) throws Exception {
        Path file = tempDir.resolve("Main-" + System.nanoTime() + ".kf");
        Files.writeString(file, source);
        Path outDir = tempDir.resolve("out-" + System.nanoTime());
        CompilationResult result = driver.compile(file, outDir, Target.JS);
        assertTrue(result.success(), "JS compile failed: " + result.diagnostics().getDiagnostics());
        Path entry = findJsEntry(outDir);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (Context context = Context.newBuilder("js")
                .allowAllAccess(true)
                .option("engine.WarnInterpreterOnly", "false")
                .out(out)
                .err(new ByteArrayOutputStream())
                .build()) {
            // SEM exposePlatform: nenhum kof_platform no global, como Node/browser puro.
            Source src = Source.newBuilder("js", entry.toFile())
                    .mimeType("application/javascript+module")
                    .build();
            context.eval(src);
        }
        return out.toString(StandardCharsets.UTF_8);
    }

    private static Path findJsEntry(Path dir) throws java.io.IOException {
        try (var s = Files.walk(dir)) {
            return s.filter(p -> p.getFileName().toString().equals("Default.mjs"))
                    .findFirst().orElseThrow();
        }
    }
}
