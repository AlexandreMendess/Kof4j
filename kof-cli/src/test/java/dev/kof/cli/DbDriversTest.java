package dev.kof.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * D-DB-ZERODRIVER track (a) — drivers JDBC auto-provisionados pelo `kof run`
 * / `kof build` (cache ~/.kof/deps): o programador nunca baixa jar na mão.
 * Sem rede o erro é ALTO e diz onde largar o jar (R6).
 */
class DbDriversTest {

    @Test
    void schemeMapCoversAllParitySchemes() {
        assertEquals("org.mariadb.jdbc:mariadb-java-client:3.5.10",
                DbDrivers.gavsForUrls(List.of("mysql://h/db")).get(0));
        assertEquals("org.mariadb.jdbc:mariadb-java-client:3.5.10",
                DbDrivers.gavsForUrls(List.of("jdbc:mysql://h/db")).get(0));
        assertEquals("org.mariadb.jdbc:mariadb-java-client:3.5.10",
                DbDrivers.gavsForUrls(List.of("mariadb://h/db", "jdbc:mariadb://h/db")).get(0));
        assertEquals("org.postgresql:postgresql:42.7.13",
                DbDrivers.gavsForUrls(List.of("postgres://h/db")).get(0));
        assertEquals("org.xerial:sqlite-jdbc:3.53.4.0",
                DbDrivers.gavsForUrls(List.of("sqlite:/tmp/x.db")).get(0));
        assertEquals("com.h2database:h2:2.5.250",
                DbDrivers.gavsForUrls(List.of("jdbc:h2:mem:x")).get(0));
        assertEquals("org.mongodb:mongodb-driver-sync:5.12.0",
                DbDrivers.gavsForUrls(List.of("mongodb://h/db")).get(0));
        assertTrue(DbDrivers.gavsForUrls(List.of("oracle://h/x", "jdbc:oracle:thin:@h/x", "foo://h")).isEmpty(),
                "oracle/desconhecido nao tem driver provisionavel");
        assertEquals(1, DbDrivers.gavsForUrls(List.of("mysql://a/x", "mariadb://b/y")).size(),
                "mesmo artefato uma vez so");
    }

    @Test
    void scanFindsConnectLiteralsIgnoresDynamic(@TempDir Path dir) throws IOException {
        Path kf = dir.resolve("M.kf");
        Files.writeString(kf, """
            main() {
                var db = db.connect("mysql://root:pw@127.0.0.1:13306/test")
                var u = "mysql://" + host
                var db2 = db.connect(u)
                println("sqlite:/tmp/x.db")
            }
            """);
        Set<String> urls = DbDrivers.scanUrls(kf);
        assertEquals(Set.of("mysql://root:pw@127.0.0.1:13306/test"), urls,
                "so literal em db.connect; dinamica e string solta nao provisionam");
    }

    private static void fakeJar(Path jar, String entry) throws IOException {
        Files.createDirectories(jar.getParent());
        try (var zip = new java.util.zip.ZipOutputStream(Files.newOutputStream(jar))) {
            zip.putNextEntry(new java.util.zip.ZipEntry(entry));
            zip.write(new byte[]{1, 2, 3, 4});
            zip.closeEntry();
        }
    }

    @Test
    void provisionUsesPreseededCacheWithoutNetwork(@TempDir Path dir) throws IOException {
        String oldHome = System.getProperty("kof.deps.home");
        Path cache = dir.resolve("cache");
        System.setProperty("kof.deps.home", cache.toString());
        try {
            fakeJar(cache.resolve("org/mariadb/jdbc/mariadb-java-client/3.5.10/mariadb-java-client-3.5.10.jar"),
                    "org/mariadb/jdbc/Driver.class");
            fakeJar(cache.resolve("org/xerial/sqlite-jdbc/3.53.4.0/sqlite-jdbc-3.53.4.0.jar"),
                    "org/sqlite/JDBC.class");
            Path kf = dir.resolve("M.kf");
            Files.writeString(kf, """
                main() {
                    var a = db.connect("mysql://root:pw@h/db")
                    var b = db.connect("sqlite:/tmp/x.db")
                }
                """);
            String cp = DbDrivers.provision(dir, kf);
            assertTrue(cp.contains("mariadb-java-client-3.5.10.jar"), "cp com mariadb: " + cp);
            assertTrue(cp.contains("sqlite-jdbc-3.53.4.0.jar"), "cp com sqlite: " + cp);
        } finally {
            if (oldHome == null) System.clearProperty("kof.deps.home");
            else System.setProperty("kof.deps.home", oldHome);
        }
    }

    @Test
    void provisionDownloadsFromFileRepo(@TempDir Path dir) throws IOException {
        String oldHome = System.getProperty("kof.deps.home");
        String oldBase = System.getProperty("kof.maven.central");
        Path cache = dir.resolve("cache");
        Path repo = dir.resolve("repo");
        fakeJar(repo.resolve("com/h2database/h2/2.5.250/h2-2.5.250.jar"), "org/h2/Driver.class");
        System.setProperty("kof.deps.home", cache.toString());
        System.setProperty("kof.maven.central", repo.toUri().toString());
        try {
            Path kf = dir.resolve("M.kf");
            Files.writeString(kf, "main() { var db = db.connect(\"jdbc:h2:mem:x\") }\n");
            String cp = DbDrivers.provision(dir, kf);
            assertTrue(cp.contains("h2-2.5.250.jar"), "cp com h2 baixado: " + cp);
            assertTrue(Files.exists(cache.resolve("com/h2database/h2/2.5.250/h2-2.5.250.jar")),
                    "jar deve pousar no cache");
            // Segunda chamada = hit de cache, sem tocar no repo (apaga o repo).
            try (var w = Files.walk(repo)) {
                w.sorted((a, b) -> b.getNameCount() - a.getNameCount())
                        .forEach(x -> { try { Files.deleteIfExists(x); } catch (IOException ignored) { } });
            }
            String cp2 = DbDrivers.provision(dir, kf);
            assertEquals(cp, cp2, "segunda resolucao vem do cache");
        } finally {
            if (oldHome == null) System.clearProperty("kof.deps.home");
            else System.setProperty("kof.deps.home", oldHome);
            if (oldBase == null) System.clearProperty("kof.maven.central");
            else System.setProperty("kof.maven.central", oldBase);
        }
    }

    @Test
    void provisionOfflineIsLoud(@TempDir Path dir) throws IOException {
        String oldHome = System.getProperty("kof.deps.home");
        String oldBase = System.getProperty("kof.maven.central");
        System.setProperty("kof.deps.home", dir.resolve("cache").toString());
        System.setProperty("kof.maven.central", "file:///nonexistent-kof-repo-xyz/");
        try {
            Path kf = dir.resolve("M.kf");
            Files.writeString(kf, "main() { var db = db.connect(\"mysql://h/db\") }\n");
            IOException e = assertThrows(IOException.class, () -> DbDrivers.provision(dir, kf));
            assertTrue(e.getMessage().contains("org.mariadb.jdbc:mariadb-java-client:3.5.10"),
                    "erro nomeia o GAV: " + e.getMessage());
            assertTrue(e.getMessage().contains("mariadb-java-client-3.5.10.jar"),
                    "erro diz onde largar o jar: " + e.getMessage());
        } finally {
            if (oldHome == null) System.clearProperty("kof.deps.home");
            else System.setProperty("kof.deps.home", oldHome);
            if (oldBase == null) System.clearProperty("kof.maven.central");
            else System.setProperty("kof.maven.central", oldBase);
        }
    }

    @Test
    void userPinWinsOverAuto(@TempDir Path dir) throws IOException {
        String oldHome = System.getProperty("kof.deps.home");
        Path cache = dir.resolve("cache");
        System.setProperty("kof.deps.home", cache.toString());
        try {
            fakeJar(cache.resolve("org/mariadb/jdbc/mariadb-java-client/9.9.9/mariadb-java-client-9.9.9.jar"),
                    "org/mariadb/jdbc/Driver.class");
            fakeJar(cache.resolve("org/mariadb/jdbc/mariadb-java-client/3.5.10/mariadb-java-client-3.5.10.jar"),
                    "org/mariadb/jdbc/Driver.class");
            Files.writeString(dir.resolve("kofdeps"), "org.mariadb.jdbc:mariadb-java-client:9.9.9\n");
            Path kf = dir.resolve("M.kf");
            Files.writeString(kf, "main() { var db = db.connect(\"mysql://h/db\") }\n");
            String cp = DbDrivers.provision(dir, kf);
            assertFalse(cp.contains("3.5.10"),
                    "pin do usuario cobre o artefato: auto nao duplica versao, cp='" + cp + "'");
            assertTrue(cp.isEmpty(), "nada auto a provisionar quando o usuario pina: cp='" + cp + "'");
        } finally {
            if (oldHome == null) System.clearProperty("kof.deps.home");
            else System.setProperty("kof.deps.home", oldHome);
        }
    }

    @Test
    void mongoTrioDirectWithoutMvn(@TempDir Path dir) throws IOException {
        String oldHome = System.getProperty("kof.deps.home");
        String oldBase = System.getProperty("kof.maven.central");
        Path cache = dir.resolve("cache");
        Path repo = dir.resolve("repo");
        for (String art : List.of("mongodb-driver-sync", "mongodb-driver-core", "bson")) {
            fakeJar(repo.resolve("org/mongodb/" + art + "/5.12.0/" + art + "-5.12.0.jar"),
                    "com/mongodb/Fake.class");
        }
        System.setProperty("kof.deps.home", cache.toString());
        System.setProperty("kof.maven.central", repo.toUri().toString());
        try {
            Path kf = dir.resolve("M.kf");
            Files.writeString(kf, "main() { var db = db.connect(\"mongodb://h/db\") }\n");
            String cp = DbDrivers.provision(dir, kf);
            assertTrue(cp.contains("mongodb-driver-sync-5.12.0.jar"), "sync: " + cp);
            assertTrue(cp.contains("mongodb-driver-core-5.12.0.jar"), "core: " + cp);
            assertTrue(cp.contains("bson-5.12.0.jar"), "bson: " + cp);
        } finally {
            if (oldHome == null) System.clearProperty("kof.deps.home");
            else System.setProperty("kof.deps.home", oldHome);
            if (oldBase == null) System.clearProperty("kof.maven.central");
            else System.setProperty("kof.maven.central", oldBase);
        }
    }

    private static String javaBin() {
        return Path.of(System.getProperty("java.home"), "bin", "java").toString();
    }

    private static boolean tcpUp(String host, int port) {
        try (java.net.Socket s = new java.net.Socket()) {
            s.connect(new java.net.InetSocketAddress(host, port), 2000);
            return s.isConnected();
        } catch (Exception e) {
            return false;
        }
    }

    // E2E flagship (prova zero-cerimônia): `kof run` SEM --deps e SEM kofdeps,
    // cache zerado (força download real do Central) — precisa de rede + MariaDB.
    @Test
    void kofRunProvisionsMariadbDriver(@TempDir Path dir) throws Exception {
        int port;
        try {
            port = Integer.parseInt(System.getenv().getOrDefault("KOF_MYSQL_PORT", "13306"));
        } catch (NumberFormatException e) {
            port = 13306;
        }
        assumeTrue(tcpUp("127.0.0.1", port), "MariaDB fora em 127.0.0.1:" + port);
        assumeTrue(tcpUp("repo1.maven.org", 443), "sem rede p/ o Central");
        Path kf = dir.resolve("Db.kf");
        Files.writeString(kf, """
            main() {
                var db = db.connect("mysql://root:kofpass@127.0.0.1:%d/test")
                db.execute(db, "create table if not exists ndp(id int, name varchar(50))")
                db.execute(db, "delete from ndp")
                db.execute(db, "insert into ndp values (?, ?)", 7, "Zero")
                var rows = db.query(db, "select id, name from ndp where id = ?", 7)
                for (var r in rows) { println(r) }
                db.close(db)
            }
            """.formatted(port));
        java.util.List<String> cmd = new java.util.ArrayList<>();
        cmd.add(javaBin());
        cmd.add("-Dkof.deps.home=" + dir.resolve("cache"));
        cmd.add("-cp");
        cmd.add(System.getProperty("java.class.path"));
        cmd.add("dev.kof.cli.Main");
        cmd.add("run");
        cmd.add(kf.toString());
        cmd.add("--target");
        cmd.add("jvm");
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(dir.toFile());
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
        assertTrue(p.waitFor(300, TimeUnit.SECONDS), "kof run timeout\n" + out);
        assertEquals(0, p.exitValue(), "kof run rc\n" + out);
        assertTrue(out.contains("{\"id\":7,\"name\":\"Zero\"}"), "roundtrip sem driver manual: " + out);
        assertTrue(Files.exists(dir.resolve("cache/org/mariadb/jdbc/mariadb-java-client/3.5.10/mariadb-java-client-3.5.10.jar")),
                "driver deve pousar no cache");
    }

    // `kof build --fat` embebe o driver provisionado (cache pré-seedado, sem
    // rede/DB — pina o wiring do embed, não a execução).
    @Test
    void buildFatEmbedsProvisionedDriver(@TempDir Path dir) throws Exception {
        String oldHome = System.getProperty("kof.deps.home");
        Path cache = dir.resolve("cache");
        System.setProperty("kof.deps.home", cache.toString());
        try {
            fakeJar(cache.resolve("org/mariadb/jdbc/mariadb-java-client/3.5.10/mariadb-java-client-3.5.10.jar"),
                    "org/mariadb/jdbc/Driver.class");
            Path src = dir.resolve("src");
            Files.createDirectories(src);
            Files.writeString(src.resolve("Main.kf"), """
                main() {
                    var db = db.connect("mysql://root:pw@127.0.0.1:13306/test")
                    println("built")
                }
                """);
            java.util.List<String> cmd = new java.util.ArrayList<>();
            cmd.add(javaBin());
            cmd.add("-Dkof.deps.home=" + cache);
            cmd.add("-cp");
            cmd.add(System.getProperty("java.class.path"));
            cmd.add("dev.kof.cli.Main");
            cmd.add("build");
            cmd.add(src.toString());
            cmd.add("--target");
            cmd.add("jvm");
            cmd.add("--output");
            cmd.add("dist");
            cmd.add("--fat");
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.directory(dir.toFile());
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(p.waitFor(300, TimeUnit.SECONDS), "build timeout\n" + out);
            assertEquals(0, p.exitValue(), "build --fat rc\n" + out);
            Path jar = dir.resolve("dist/kof-app.jar");
            assertTrue(Files.exists(jar), "fat jar deve existir: " + out);
            try (var zip = new java.util.zip.ZipFile(jar.toFile())) {
                assertTrue(zip.getEntry("org/mariadb/jdbc/Driver.class") != null,
                        "fat deve embebar o driver provisionado");
            }
        } finally {
            if (oldHome == null) System.clearProperty("kof.deps.home");
            else System.setProperty("kof.deps.home", oldHome);
        }
    }
}
