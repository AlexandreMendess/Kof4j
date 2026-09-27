package dev.kof.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * D-DB-ZERODRIVER track (a) — JDBC drivers auto-provisionados: `kof run` e
 * `kof build` resolvem o driver do scheme usado no programa (cache
 * {@code ~/.kof/deps}, mesmo layout do `kofdeps`) em vez de exigir download
 * manual (regra 11 — a plataforma absorve a cerimônia). Sem rede o erro é
 * ALTO e diz onde largar o jar na mão (R6, nunca silêncio).
 *
 * <p>Versões pinnadas = as provadas no classpath de teste do repo
 * (kof-compiler/pom.xml). `oracle://` não tem entrada (S4 declarado).
 * Dinâmicas (URL montada em variável) não são vistas pelo scan — declare em
 * `kofdeps` (aviso honesto, nunca tentativa adivinhada).</p>
 */
final class DbDrivers {

    private DbDrivers() {}

    /** Prefixo de URL → GAV pinnado (primeiro `startsWith` vence). */
    static final Map<String, String> PREFIX_GAV = new LinkedHashMap<>();
    static {
        PREFIX_GAV.put("jdbc:mariadb:", "org.mariadb.jdbc:mariadb-java-client:3.5.10");
        PREFIX_GAV.put("jdbc:mysql:", "org.mariadb.jdbc:mariadb-java-client:3.5.10");
        PREFIX_GAV.put("mysql://", "org.mariadb.jdbc:mariadb-java-client:3.5.10");
        PREFIX_GAV.put("mariadb://", "org.mariadb.jdbc:mariadb-java-client:3.5.10");
        PREFIX_GAV.put("jdbc:postgresql:", "org.postgresql:postgresql:42.7.13");
        PREFIX_GAV.put("postgres://", "org.postgresql:postgresql:42.7.13");
        PREFIX_GAV.put("jdbc:sqlite:", "org.xerial:sqlite-jdbc:3.53.4.0");
        PREFIX_GAV.put("sqlite:", "org.xerial:sqlite-jdbc:3.53.4.0");
        PREFIX_GAV.put("jdbc:h2:", "com.h2database:h2:2.5.250");
        PREFIX_GAV.put("mongodb://", "org.mongodb:mongodb-driver-sync:5.12.0");
    }

    /** URLs → GAVs (ordem de aparição, sem repetição; desconhecidas ignoradas). */
    static List<String> gavsForUrls(java.util.Collection<String> urls) {
        List<String> out = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (String u : urls) {
            if (u == null) continue;
            for (Map.Entry<String, String> e : PREFIX_GAV.entrySet()) {
                if (u.startsWith(e.getKey()) && seen.add(e.getValue())) {
                    out.add(e.getValue());
                    break;
                }
            }
        }
        return out;
    }

    private static final Pattern CONNECT_LIT =
            Pattern.compile("db\\.connect\\(\\s*\"([^\"\\\\]*)\"");

    /** Literais de URL em `db.connect("...")` (executa zero código). */
    static Set<String> scanUrls(Path kfFile) throws IOException {
        Set<String> urls = new LinkedHashSet<>();
        if (kfFile == null || !Files.isRegularFile(kfFile)) return urls;
        Matcher m = CONNECT_LIT.matcher(Files.readString(kfFile));
        while (m.find()) urls.add(m.group(1));
        return urls;
    }

    /** Artefatos `group:artifact` declarados no `kofdeps` do projeto (pin do
     *  usuário vence o auto — nunca duas versões no classpath). */
    static Set<String> declaredArtifacts(Path projectDir) throws IOException {
        Set<String> out = new LinkedHashSet<>();
        Path f = projectDir.resolve(Deps.DEPS_FILE);
        if (!Files.exists(f)) return out;
        for (String l : Files.readAllLines(f)) {
            String s = l == null ? "" : l.trim();
            if (s.isEmpty() || DepsRegistry.isRegistrySpec(s)) continue;
            String[] ga = s.split(":");
            if (ga.length == 3) out.add(ga[0] + ":" + ga[1]);
        }
        return out;
    }

    /** Garante o jar no cache (baixa se preciso) + transitivos do mongo
     *  (trio em lockstep, download direto — sem `mvn`). Falha ALTA com o
     *  caminho de drop manual. */
    static List<Path> ensureDriver(String gav) throws IOException {
        String[] ga = gav.split(":");
        List<String> jars = new ArrayList<>();
        if (ga[1].equals("mongodb-driver-sync")) {
            for (String art : List.of("mongodb-driver-sync", "mongodb-driver-core", "bson")) {
                jars.add(ga[0] + ":" + art + ":" + ga[2]);
            }
        } else {
            jars.add(gav);
        }
        List<Path> out = new ArrayList<>();
        for (String j : jars) {
            String[] p = j.split(":");
            try {
                Deps.download(p[0], p[1], p[2]);
            } catch (IOException e) {
                Path want = Deps.jarPath(p[0], p[1], p[2]);
                throw new IOException("kof db: cannot provision " + j + " (" + e.getMessage()
                        + "); offline? download the jar and drop it at " + want
                        + " (same layout) or declare it in kofdeps");
            }
            out.add(Deps.jarPath(p[0], p[1], p[2]));
        }
        return out;
    }

    /** Classpath (separador do OS) dos drivers dos schemes usados nos .kf. */
    static String provision(Path projectDir, List<Path> kfFiles) throws IOException {
        Set<String> urls = new LinkedHashSet<>();
        if (kfFiles != null) {
            for (Path f : kfFiles) urls.addAll(scanUrls(f));
        }
        List<String> gavs = gavsForUrls(urls);
        if (gavs.isEmpty()) return "";
        Set<String> declared = declaredArtifacts(projectDir);
        String sep = System.getProperty("os.name", "").toLowerCase().contains("win") ? ";" : ":";
        StringBuilder cp = new StringBuilder();
        for (String gav : gavs) {
            String[] ga = gav.split(":");
            if (declared.contains(ga[0] + ":" + ga[1])) continue;
            for (Path jar : ensureDriver(gav)) {
                if (cp.length() > 0) cp.append(sep);
                cp.append(jar);
            }
        }
        return cp.toString();
    }

    static String provision(Path projectDir, Path kfFile) throws IOException {
        return provision(projectDir, kfFile == null ? List.of() : List.of(kfFile));
    }
}
