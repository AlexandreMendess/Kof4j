package dev.kof.compiler.nat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * §371 (issue #550): leitura do FONTE de produção do runtime nativo, de onde
 * a ORDEM das fatias/peças é derivada (nunca transcrita).
 *
 * <p>Ordem de resolução: <b>classpath primeiro</b> — os dois fontes são
 * empacotados como recursos de build do jar do kof-compiler (pom
 * {@code <resources>}), então a derivação funciona do jar distribuído do CLI,
 * fora da árvore do projeto; <b>o caminho relativo ao CWD é apenas
 * fallback de DEV</b> (execução de IDE sem fase de resources do Maven).
 * Precedente do mesmo contrato: {@code CompilerPipeline} lendo
 * {@code /dev/kof/android-host.kf}.</p>
 *
 * <p>Antes disto os loaders só liam pelo CWD: do jar shipped o parse
 * lançava {@link IllegalStateException} → {@code prune DESABILITADO} →
 * runtime completo → {@code usesDb} → {@code -lsqlite3} inexistente no
 * sysroot cross → COMP001 até em {@code hello.kf}.</p>
 */
final class RuntimeSourceLoader {

    private RuntimeSourceLoader() {}

    /** Lê o fonte: recurso no classpath (path absoluto, ex.:
     *  {@code /dev/kof/compiler/NativeRuntime.java}) primeiro; depois cada
     *  caminho relativo ao CWD, na ordem dada (modo dev). Lança
     *  {@link IllegalStateException} nomeando as duas fontes tentadas (R6 —
     *  nunca silencioso). */
    static String read(Class<?> anchor, String classpathResource, String... devPaths) {
        try (InputStream in = anchor.getResourceAsStream(classpathResource)) {
            if (in != null) {
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            throw new IllegalStateException("classpath resource " + classpathResource
                    + " unreadable: " + e.getMessage(), e);
        }
        for (String devPath : devPaths) {
            try {
                return Files.readString(Path.of(devPath), StandardCharsets.UTF_8);
            } catch (IOException ignored) {
                // tenta o próximo candidato dev; esgotados → erro honesto
            }
        }
        throw new IllegalStateException(classpathResource
                + " not on the classpath (shaded jar must carry it as a build resource)"
                + " and no dev-tree copy found at: " + String.join(" or ", devPaths));
    }
}
