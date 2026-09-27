package dev.kof.runtime;

import org.graalvm.polyglot.Value;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * kof.db no host GraalJS (DB001) — mesma semântica dos gerados por
 * {@code JvmConfigRuntime.source()} no alvo JVM (connect/close/execute/
 * query untyped/transaction com aninhamento), sobre JDBC do classpath do
 * runner (o mesmo contrato do caminho JVM: quem traz o driver é o
 * classpath — sqlite/h2 chegam via {@code -cp} dos testes e do app).
 *
 * O runtime JS ({@code kof-runtime-io.mjs}) delega {@code kofDb*} para os
 * métodos deste objeto publicados em {@code kof_platform}
 * ({@link KofJsRunner}); a classe fica no kof-runtime porque é o host que
 * executa, não o compilador. Métodos com {@link Value} aceitam o lado JS
 * direto (Value→Java nas fronteiras); os primitivos String/Object são para
 * teste sem GraalJS.
 */
public final class KofJsDbBridge {

    private KofJsDbBridge() {
    }

    private static final Map<String, java.sql.Connection> CONNECTIONS = new ConcurrentHashMap<>();
    private static final AtomicInteger SEQ = new AtomicInteger();
    private static volatile String DEFAULT_ID;
    private static final ThreadLocal<java.sql.Connection> TX = new ThreadLocal<>();

    public static String connect(String url) throws Exception {
        if (isMongo(url)) throw mongoGap(url);
        try {
            return register(java.sql.DriverManager.getConnection(normalizeBareScheme(url)));
        } catch (java.sql.SQLException e) {
            throw dbDriverGap(url, e);
        }
    }

    public static String connect2(String url, String user, String pass) throws Exception {
        if (isMongo(url)) throw mongoGap(url);
        try {
            return register(java.sql.DriverManager.getConnection(normalizeBareScheme(url), user, pass));
        } catch (java.sql.SQLException e) {
            throw dbDriverGap(url, e);
        }
    }

    private static boolean isMongo(String url) {
        return url != null && url.startsWith("mongodb://");
    }

    /**
     * D-DB-NORMALIZE (27/09, voted by the maintainer): same bare→`jdbc:`
     * normalization as the JVM path ({@code JvmConfigRuntime}) — the JS
     * delegate IS the host JDBC, so the same url must work. Duplicated here
     * (instead of shared) because this class lives in kof-runtime, the host
     * that executes — same precedent as the S2 DB001 mapping.
     * (`mysql://`→`jdbc:mariadb://`: the driver only takes `mariadb:`.)
     */
    private static String normalizeBareScheme(String url) {
        if (url == null) return null;
        String jdbcScheme;
        if (url.startsWith("mysql://")) jdbcScheme = "jdbc:mariadb://";
        else if (url.startsWith("mariadb://")) jdbcScheme = "jdbc:mariadb://";
        else if (url.startsWith("postgres://")) jdbcScheme = "jdbc:postgresql://";
        else if (url.startsWith("sqlite:")) return "jdbc:sqlite:" + url.substring("sqlite:".length());
        else return url;
        java.net.URI u;
        try {
            u = new java.net.URI(url);
        } catch (Exception e) {
            return url;
        }
        String host = u.getHost();
        if (host == null) return url;
        if (host.contains(":") && !host.startsWith("[")) host = "[" + host + "]";
        StringBuilder sb = new StringBuilder(jdbcScheme).append(host);
        if (u.getPort() != -1) sb.append(':').append(u.getPort());
        String path = u.getPath();
        if (path != null && !path.isEmpty()) sb.append(path);
        String query = u.getQuery();
        String user = null, pass = null;
        String userInfo = u.getUserInfo();
        if (userInfo != null) {
            int c = userInfo.indexOf(':');
            if (c < 0) user = userInfo;
            else {
                user = userInfo.substring(0, c);
                pass = userInfo.substring(c + 1);
            }
        }
        StringBuilder q = new StringBuilder(query == null ? "" : query);
        if (user != null && !user.isEmpty() && !hasQueryParam(query, "user")) {
            if (q.length() > 0) q.append('&');
            q.append("user=").append(user);
        }
        if (pass != null && !pass.isEmpty() && !hasQueryParam(query, "password")
                && !hasQueryParam(query, "pass")) {
            if (q.length() > 0) q.append('&');
            q.append("password=").append(pass);
        }
        if (q.length() > 0) sb.append('?').append(q);
        return sb.toString();
    }

    private static boolean hasQueryParam(String query, String name) {
        if (query == null) return false;
        for (String seg : query.split("&", -1)) {
            if (seg.equals(name) || seg.startsWith(name + "=")) return true;
        }
        return false;
    }

    /**
     * S3/db-parity: o alvo JS ainda nao tem a ponte de driver mongo no host
     * (o caminho ORM mongo vive no runtime do alvo JVM). Recusa NOMEADA (R6),
     * em vez da mensagem generica de "sem driver JDBC" — que seria enganosa,
     * porque {@code mongodb://} nao e URL JDBC.
     */
    private static Exception mongoGap(String url) {
        return new IllegalArgumentException(
                "DB001: mongodb:// is not supported on the JS target yet (host driver bridge pending): " + url);
    }

    /**
     * DB001 (S2/db-parity): paridade com o caminho JVM — URL JDBC sem driver
     * no classpath vira diagn\u00f3stico NOMEADO (R6) em vez do {@code SQLException}
     * cru; falhas reais de conex\u00e3o passam intactas.
     */
    private static Exception dbDriverGap(String url, java.sql.SQLException e) {
        String m = e.getMessage() == null ? "" : e.getMessage();
        if (m.contains("No suitable driver")) {
            return new IllegalArgumentException(
                    "DB001: no JDBC driver for this URL (add the driver to the classpath): " + url);
        }
        return e;
    }

    private static String register(java.sql.Connection c) {
        String id = "db" + SEQ.incrementAndGet();
        CONNECTIONS.put(id, c);
        DEFAULT_ID = id;
        return id;
    }

    public static int close(String id) {
        java.sql.Connection c = CONNECTIONS.remove(id == null || id.isEmpty() ? DEFAULT_ID : id);
        if (c == null) {
            return -1;
        }
        try {
            c.close();
            return 0;
        } catch (Exception e) {
            return -1;
        }
    }

    public static int execute(String id, String sql, Object... args) throws Exception {
        try (java.sql.PreparedStatement ps = conn(id).prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) {
                ps.setObject(i + 1, args[i]);
            }
            return ps.executeUpdate();
        }
    }

    /** Value[] do guest JS (execute/query variadics). */
    public static int executeV(String id, String sql, Value... args) throws Exception {
        Object[] plain = new Object[args.length];
        for (int i = 0; i < args.length; i++) {
            plain[i] = fromGuest(args[i]);
        }
        return execute(id, sql, plain);
    }

    /** Rows como JSON strings (contrato {@code List<String>} do frontend). */
    public static List<String> query(String id, String sql, String className, Object... args)
            throws Exception {
        List<String> rows = new ArrayList<>();
        try (java.sql.PreparedStatement ps = conn(id).prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) {
                ps.setObject(i + 1, args[i]);
            }
            try (java.sql.ResultSet rs = ps.executeQuery()) {
                java.sql.ResultSetMetaData md = rs.getMetaData();
                int cols = md.getColumnCount();
                while (rs.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (int i = 1; i <= cols; i++) {
                        row.put(md.getColumnLabel(i).toLowerCase(), value(rs.getObject(i)));
                    }
                    if (className != null && !className.isEmpty()) {
                        // O programa JS nao emite .class no classpath do host
                        // (Target.JS nao produz bytecode JVM) — o caminho
                        // tipado e gate DB002 no compile; em runtime jamais
                        // silenciar: erro claro, nunca linha vazia.
                        throw new UnsupportedOperationException(
                                "db.query<T> is not supported on the JS target (DB002)");
                    }
                    rows.add(rowToJson(row));
                }
            }
        }
        return rows;
    }

    public static List<String> queryV(String id, String sql, String className, Value... args)
            throws Exception {
        Object[] plain = new Object[args.length];
        for (int i = 0; i < args.length; i++) {
            plain[i] = fromGuest(args[i]);
        }
        return query(id, sql, className, plain);
    }

    /** Mesma regra de aninhamento do JVM (bug 77): bloco interno NAO
     *  comita nem rollbacka — quem decide e o bloco externo. */
    public static void transaction(Runnable task) throws Exception {
        java.sql.Connection c = conn(DEFAULT_ID);
        boolean nested = c.equals(TX.get());
        boolean prevAuto = c.getAutoCommit();
        c.setAutoCommit(false);
        if (!nested) {
            TX.set(c);
        }
        try {
            task.run();
            if (!nested) {
                c.commit();
            }
        } catch (Exception | Error e) {
            if (!nested) {
                try {
                    c.rollback();
                } catch (Exception ignored) {
                }
            }
            throw e;
        } finally {
            if (!nested) {
                TX.remove();
            }
            c.setAutoCommit(prevAuto);
        }
    }

    /** Bridge do Value: o guest passa Int/Long/Double/Bool/String; o shape
     *  do argumento e o mesmo do `setObject` no JVM. */
    static Object fromGuest(Value v) {
        if (v == null || v.isNull()) {
            return null;
        }
        if (v.isString()) {
            return v.asString();
        }
        if (v.isBoolean()) {
            return v.asBoolean();
        }
        if (v.fitsInLong()) {
            return v.asLong();
        }
        if (v.fitsInDouble()) {
            return v.asDouble();
        }
        return v.as(Object.class);
    }

    /** Bridge do Value p/ objeto: o guest passa o record via `toJSON()` (membros
     *  = nomes de campo crus); o ORM lê os valores pela ordem do schema. */
    static Map<String, Object> valueMap(Value obj) {
        Map<String, Object> m = new LinkedHashMap<>();
        if (obj == null || obj.isNull()) {
            return m;
        }
        for (String key : obj.getMemberKeys()) {
            m.put(key, fromGuest(obj.getMember(key)));
        }
        return m;
    }

    /** Normaliza tipos JDBC p/ JSON natural (CLOB→String, BLOB→base64) —
     *  paridade com o `kof_db_value` do JVM. */
    private static Object value(Object v) throws Exception {
        if (v instanceof java.sql.Clob clob) {
            return clob.getSubString(1, (int) clob.length());
        }
        if (v instanceof java.sql.Blob blob) {
            return java.util.Base64.getEncoder().encodeToString(blob.getBytes(1, (int) blob.length()));
        }
        return v;
    }

    static java.sql.Connection conn(String id) throws Exception {
        String key = (id == null || id.isEmpty()) ? DEFAULT_ID : id;
        java.sql.Connection c = key == null ? null : CONNECTIONS.get(key);
        if (c == null) {
            throw new IllegalStateException("no db connection: " + id);
        }
        return c;
    }

    /** Serializacao JSON do linha — mesmo shape do `kof_db_row_to_json` JVM
     *  (chaves na ordem de leitura, strings escapadas, null literal). */
    static String rowToJson(Map<String, Object> row) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> e : row.entrySet()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append(encodeString(e.getKey())).append(':');
            Object v = e.getValue();
            if (v == null) {
                sb.append("null");
            } else if (v instanceof String s) {
                sb.append(encodeString(s));
            } else if (v instanceof Number n) {
                sb.append(n);
            } else if (v instanceof Boolean b) {
                sb.append(b);
            } else {
                sb.append(encodeString(String.valueOf(v)));
            }
        }
        return sb.append('}').toString();
    }

    static String encodeString(String value) {
        if (value == null) {
            return "null";
        }
        StringBuilder sb = new StringBuilder(value.length() + 2);
        sb.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append("\\u");
                        String hex = Integer.toHexString(c);
                        sb.append("0".repeat(4 - hex.length()));
                        sb.append(hex);
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.append('"').toString();
    }
}
