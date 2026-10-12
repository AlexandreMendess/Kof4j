package dev.kof.compiler.jvm;

/**
 * Runtime do entity-history (D-ENTITY-HISTORY): a gravação (2b) e a
 * consulta (2c-parte-2) — fragmento de {@link JvmOrmRuntime} extraído pelo
 * REFACTOR-500 (o constant pool/limite de 65535 bytes por text block da
 * classe única). Os métodos são statics da MESMA classe gerada
 * ({@code KofRuntime}) — as chamadas cruzadas para {@code kof_orm_do_save}/
 * {@code kof_orm_delete} (que ficaram no fragmento principal) resolvem
 * normalmente.
 */
public final class JvmOrmHistoryRuntime {

    private JvmOrmHistoryRuntime() {}

    static String source() {
        return """
                // entity-history fatia 2b (D-ENTITY-HISTORY): wrapper que
                // grava a revisão APÓS o DML — a operação (INSERT/UPDATE) é
                // conhecida DENTRO do doSave (generated key → INSERT).
                public static Object kof_orm_save_recorded(String id, Object obj, String table, String schema) throws Exception {
                    String[] op = new String[1];
                    Object out = kof_orm_do_save(id, obj, table, schema, op);
                    kof_orm_history_record(id, out, table, schema, op[0]);
                    return out;
                }

                // entity-history fatia 2c-parte-2 (D-ENTITY-HISTORY §9.2):
                // `Entity.history(db, id[, offset, limit])` — as revisões em
                // ordem ascendente, instâncias de `Revision` pelo resolver de
                // className (mesmo mecanismo do find/all). HIST005 no runtime
                // para offset/limit negativos não-constantes (§9.3).
                public static java.util.ArrayList<Object> kof_orm_history(String id, Object key,
                        String table, String schema, String className) throws Exception {
                    String dialect = kof_orm_dialect(id);
                    return kof_db_query1(id, "SELECT revision, at, operation FROM "
                            + kof_orm_q(table + "_history", dialect)
                            + " WHERE entity_pk = ? ORDER BY revision", key, className);
                }

                public static java.util.ArrayList<Object> kof_orm_history_page(String id, Object key,
                        int offset, int limit, String table, String schema, String className) throws Exception {
                    if (offset < 0 || limit < 0) {
                        throw new RuntimeException("HIST005: " + table
                                + ".history offset/limit must be >= 0");
                    }
                    String dialect = kof_orm_dialect(id);
                    return kof_db_query3(id, "SELECT revision, at, operation FROM "
                            + kof_orm_q(table + "_history", dialect)
                            + " WHERE entity_pk = ? ORDER BY revision LIMIT ? OFFSET ?", key, limit, offset, className);
                }

                // entity-history fatia 2d (D-ENTITY-HISTORY §9.2): `E.at` —
                // o estado materializado NA revisão (snapshot completo →
                // kof_json_parse + kof_json_bind, a maquinaria do §565).
                // Revisão DELETE → null (a entity não existia, §9.3).
                public static Object kof_orm_history_at(String id, Object key, int revision,
                        String table, String schema, String className) throws Exception {
                    if (revision <= 0) {
                        throw new RuntimeException("HIST005: " + table
                                + ".at revision must be >= 1");
                    }
                    String dialect = kof_orm_dialect(id);
                    try (java.sql.PreparedStatement ps = kof_db_conn(id).prepareStatement(
                            "SELECT payload FROM " + kof_orm_q(table + "_history", dialect)
                                    + " WHERE entity_pk = ? AND revision = ?")) {
                        ps.setObject(1, key);
                        ps.setInt(2, revision);
                        try (java.sql.ResultSet rs = ps.executeQuery()) {
                            if (!rs.next()) return null; // revisão ausente (§9.3)
                            String payload = rs.getString(1);
                            if (payload.contains("\\"deleted\\"")) return null; // §9.3: após DELETE
                            return kof_json_bind(Class.forName(className), kof_json_parse(payload));
                        }
                    }
                }

                // §9.2: `E.atTime` — a última revisão EM-OU-ANTES do instante
                // ISO (Instant.parse; malformado = HIST005 em runtime, §9.3).
                public static Object kof_orm_history_at_time(String id, Object key, String iso,
                        String table, String schema, String className) throws Exception {
                    long epoch;
                    try {
                        epoch = java.time.Instant.parse(iso).toEpochMilli();
                    } catch (java.time.format.DateTimeParseException e) {
                        throw new RuntimeException("HIST005: " + table
                                + ".atTime iso instant is malformed: " + iso);
                    }
                    String dialect = kof_orm_dialect(id);
                    try (java.sql.PreparedStatement ps = kof_db_conn(id).prepareStatement(
                            "SELECT payload FROM " + kof_orm_q(table + "_history", dialect)
                                    + " WHERE entity_pk = ? AND at <= ? ORDER BY revision DESC LIMIT 1")) {
                        ps.setObject(1, key);
                        ps.setLong(2, epoch);
                        try (java.sql.ResultSet rs = ps.executeQuery()) {
                            if (!rs.next()) return null;
                            String payload = rs.getString(1);
                            if (payload.contains("\\"deleted\\"")) return null; // §9.3
                            return kof_json_bind(Class.forName(className), kof_json_parse(payload));
                        }
                    }
                }

                // §9.2: `E.changes` — diffs de campos entre payloads
                // consecutivos na faixa [from, to] (fechada); strings
                // portáveis; campo removido = previous-only.
                public static java.util.ArrayList<Object> kof_orm_history_changes(String id, Object key,
                        int from, int to, String table, String schema, String fcClassName) throws Exception {
                    if (from < 1 || to < from) {
                        throw new RuntimeException("HIST005: " + table
                                + ".changes range must be 1 <= from <= to");
                    }
                    String dialect = kof_orm_dialect(id);
                    java.util.ArrayList<Object> out = new java.util.ArrayList<>();
                    Class<?> fcType = Class.forName(fcClassName);
                    java.lang.reflect.Constructor<?> fcCtor = null;
                    for (java.lang.reflect.Constructor<?> c : fcType.getDeclaredConstructors()) {
                        if (c.getParameterCount() == 4) { fcCtor = c; break; }
                    }
                    String prevPayload = null;
                    int prevRev = -1;
                    try (java.sql.PreparedStatement ps = kof_db_conn(id).prepareStatement(
                            "SELECT revision, payload FROM " + kof_orm_q(table + "_history", dialect)
                                    + " WHERE entity_pk = ? AND revision >= ? AND revision <= ? "
                                    + "ORDER BY revision")) {
                        ps.setObject(1, key);
                        ps.setInt(2, Math.max(1, from - 1)); // o estado ANTERIOR à faixa
                        ps.setInt(3, to);
                        try (java.sql.ResultSet rs = ps.executeQuery()) {
                            while (rs.next()) {
                                int rev = rs.getInt(1);
                                String payload = rs.getString(2);
                                if (payload.contains("\\"deleted\\"")) break; // após DELETE não há estado
                                if (prevPayload != null && rev >= from) {
                                    java.util.Map<Object, Object> prev =
                                            (java.util.Map<Object, Object>) kof_json_parse(prevPayload);
                                    java.util.Map<Object, Object> cur =
                                            (java.util.Map<Object, Object>) kof_json_parse(payload);
                                    for (java.util.Map.Entry<Object, Object> e : cur.entrySet()) {
                                        Object before = prev.get(e.getKey());
                                        if (!java.util.Objects.equals(before, e.getValue())) {
                                            out.add(fcCtor.newInstance((long) rev, String.valueOf(e.getKey()),
                                                    before == null ? null : String.valueOf(before),
                                                    e.getValue() == null ? null : String.valueOf(e.getValue())));
                                        }
                                    }
                                }
                                prevPayload = payload;
                                prevRev = rev;
                            }
                        }
                    }
                    return out;
                }

                public static boolean kof_orm_delete_recorded(String id, Object key, String table, String schema) throws Exception {
                    boolean ok = kof_orm_delete(id, key, table, schema);
                    if (ok) kof_orm_history_record_delete(id, String.valueOf(key), table, schema);
                    return ok;
                }

                // §10 (D-ENTITY-HISTORY): a tabela lógica audit_revision —
                // criada pelo passo EXPLÍCITO orm.createHistory<T>(db) (nunca
                // DDL implícito). Nomes físicos são do backend (§10).
                public static boolean kof_orm_create_history(String id, String table, String schema) throws Exception {
                    String dialect = kof_orm_dialect(id);
                    return kof_db_execute(id, "CREATE TABLE IF NOT EXISTS " + kof_orm_q(table + "_history", dialect)
                            + " (revision BIGINT NOT NULL, entity_pk VARCHAR(255) NOT NULL, "
                            + "operation VARCHAR(16) NOT NULL, at BIGINT NOT NULL, payload CLOB)") >= 0;
                }

                // §10: revisão monotônica POR-ENTITY (entity_pk) —
                // COALESCE(MAX)+1 na MESMA connection do DML; payload = snapshot
                // completo dos campos (JSON de texto via reflection).
                public static void kof_orm_history_record(String id, Object obj, String table, String schema,
                        String operation) throws Exception {
                    String dialect = kof_orm_dialect(id);
                    java.util.List<OrmField> fields = kof_orm_schema(schema);
                    int pkIndex = kof_orm_pkIndex(fields);
                    java.lang.reflect.RecordComponent[] comps = obj.getClass().getRecordComponents();
                    Object[] vals = new Object[comps.length];
                    StringBuilder json = new StringBuilder("{");
                    String q = String.valueOf((char) 34);
                    for (int i = 0; i < comps.length; i++) {
                        vals[i] = comps[i].getAccessor().invoke(obj);
                        if (i > 0) json.append(",");
                        json.append(q).append(comps[i].getName()).append(q).append(":")
                                .append(kof_orm_json(vals[i]));
                    }
                    json.append("}");
                    Object pk = vals[pkIndex];
                    long rev;
                    try (java.sql.PreparedStatement rs_ = kof_db_conn(id).prepareStatement(
                            "SELECT COALESCE(MAX(revision), 0) + 1 FROM "
                                    + kof_orm_q(table + "_history", dialect) + " WHERE entity_pk = ?")) {
                        rs_.setString(1, String.valueOf(pk));
                        try (java.sql.ResultSet r_ = rs_.executeQuery()) {
                            rev = r_.next() ? r_.getLong(1) : 1L;
                        }
                    }
                    try (java.sql.PreparedStatement ps = kof_db_conn(id).prepareStatement(
                            "INSERT INTO " + kof_orm_q(table + "_history", dialect)
                                    + " (revision, entity_pk, operation, at, payload) VALUES (?, ?, ?, ?, ?)")) {
                        ps.setLong(1, rev);
                        ps.setString(2, String.valueOf(pk));
                        ps.setString(3, operation);
                        ps.setLong(4, System.currentTimeMillis());
                        ps.setString(5, json.toString());
                        ps.executeUpdate();
                    }
                }

                // DELETE: o snapshot é a própria chave (a linha já não existe)
                public static void kof_orm_history_record_delete(String id, String pk, String table, String schema)
                        throws Exception {
                    String dialect = kof_orm_dialect(id);
                    long rev;
                    try (java.sql.PreparedStatement rs_ = kof_db_conn(id).prepareStatement(
                            "SELECT COALESCE(MAX(revision), 0) + 1 FROM "
                                    + kof_orm_q(table + "_history", dialect) + " WHERE entity_pk = ?")) {
                        rs_.setString(1, pk);
                        try (java.sql.ResultSet r_ = rs_.executeQuery()) {
                            rev = r_.next() ? r_.getLong(1) : 1L;
                        }
                    }
                    try (java.sql.PreparedStatement ps = kof_db_conn(id).prepareStatement(
                            "INSERT INTO " + kof_orm_q(table + "_history", dialect)
                                    + " (revision, entity_pk, operation, at, payload) VALUES (?, ?, ?, ?, ?)")) {
                        ps.setLong(1, rev);
                        ps.setString(2, pk);
                        ps.setString(3, "DELETE");
                        ps.setLong(4, System.currentTimeMillis());
                        ps.setString(5, "{" + (char) 34 + "deleted" + (char) 34 + ":true}");
                        ps.executeUpdate();
                    }
                }

                private static String kof_orm_json(Object v) {
                    // aspas/barra via char-constante: ZERO sequência de escape
                    // dentro do text block (o gerado compila sempre).
                    String q = String.valueOf((char) 34);
                    String b = String.valueOf((char) 92);
                    if (v == null) return "null";
                    if (v instanceof Number || v instanceof Boolean) return String.valueOf(v);
                    String s = String.valueOf(v);
                    s = s.replace(b, b + b).replace(q, b + q);
                    return q + s + q;
                }

                private static Object kof_orm_do_save(String id, Object obj, String table, String schema, String[] opOut) throws Exception {

                    String dialect = kof_orm_dialect(id);                    java.util.List<OrmField> fields = kof_orm_schema(schema);
                    int pkIndex = kof_orm_pkIndex(fields);
                    java.lang.reflect.RecordComponent[] comps = obj.getClass().getRecordComponents();
                    Object[] values = new Object[comps.length];
                    for (int i = 0; i < comps.length; i++) {
                        values[i] = comps[i].getAccessor().invoke(obj);
                    }
                    Object pk = values[pkIndex];
                    if (kof_mongo_id(id)) {
                        // upsert por _id (o id é fornecido pela aplicação)
                        Object coll = kof_mongo_coll(id, table);
                        Object filter = kof_mongo_eq("_id", pk);
                        Object doc = kof_mongo_values(comps, values);
                        Class<?> optsType = Class.forName("com.mongodb.client.model.ReplaceOptions");
                        Object opts = optsType.getConstructor().newInstance();
optsType.getMethod("upsert", boolean.class).invoke(opts, true);
                         kof_mongo_method(coll.getClass(), "replaceOne", 3, filter.getClass(), doc.getClass(), optsType).invoke(coll, filter, doc, opts);
                         return obj;
                    }
                    boolean pkIsNumeric = pk instanceof Number
                            || "int".equals(fields.get(pkIndex).type)
                            || "long".equals(fields.get(pkIndex).type);
                    long pkLong = pk instanceof Number n ? n.longValue()
                            : (pk == null ? 0L : 0L);
                    if (pk == null || (pkIsNumeric && pkLong == 0L)) {
                        // INSERT sem a PK — o banco gera o id
                        StringBuilder cols = new StringBuilder();
                        StringBuilder marks = new StringBuilder();
                        java.util.List<Object> binds = new java.util.ArrayList<>();
                        for (int i = 0; i < fields.size(); i++) {
                            if (i == pkIndex) continue;
                            if (cols.length() > 0) { cols.append(", "); marks.append(", "); }
                            cols.append(kof_orm_q(fields.get(i).name, dialect));
                            marks.append('?');
                            binds.add(values[i]);
                        }
                        try (java.sql.PreparedStatement ps = kof_db_conn(id).prepareStatement(
                                "INSERT INTO " + kof_orm_q(table, dialect) + " (" + cols + ") VALUES (" + marks + ")",
                                java.sql.Statement.RETURN_GENERATED_KEYS)) {
                            for (int i = 0; i < binds.size(); i++) ps.setObject(i + 1, binds.get(i));
                            ps.executeUpdate();
                            try (java.sql.ResultSet keys = ps.getGeneratedKeys()) {
                                if (keys.next()) {
                                    values[pkIndex] = keys.getObject(1);
                                }
                            }
                        }
                        if (opOut != null) opOut[0] = "INSERT";
                        return kof_orm_newInstance(obj.getClass(), values);
                    }
                    // UPDATE
                    StringBuilder sets = new StringBuilder();
                    java.util.List<Object> binds = new java.util.ArrayList<>();
                    for (int i = 0; i < fields.size(); i++) {
                        if (i == pkIndex) continue;
                        if (sets.length() > 0) sets.append(", ");
                        sets.append(kof_orm_q(fields.get(i).name, dialect)).append(" = ?");
                        binds.add(values[i]);
                    }
                    binds.add(pk);
                    try (java.sql.PreparedStatement ps = kof_db_conn(id).prepareStatement(
                            "UPDATE " + kof_orm_q(table, dialect) + " SET " + sets + " WHERE "
                                    + kof_orm_q(fields.get(pkIndex).name, dialect) + " = ?")) {
                        for (int i = 0; i < binds.size(); i++) ps.setObject(i + 1, binds.get(i));
                        if (ps.executeUpdate() > 0) {
                            if (opOut != null) opOut[0] = "UPDATE";
                            return obj;
                        }
                    }
                    // PK não-numérica ou id inexistente: INSERT (upsert-like)
                    StringBuilder cols = new StringBuilder();
                    StringBuilder marks = new StringBuilder();
                    java.util.List<Object> insertBinds = new java.util.ArrayList<>();
                    for (int i = 0; i < fields.size(); i++) {
                        if (cols.length() > 0) { cols.append(", "); marks.append(", "); }
                        cols.append(kof_orm_q(fields.get(i).name, dialect));
                        marks.append('?');
                        insertBinds.add(values[i]);
                    }
                    try (java.sql.PreparedStatement ps = kof_db_conn(id).prepareStatement(
                            "INSERT INTO " + kof_orm_q(table, dialect) + " (" + cols + ") VALUES (" + marks + ")")) {
                        for (int i = 0; i < insertBinds.size(); i++) ps.setObject(i + 1, insertBinds.get(i));
                        ps.executeUpdate();
                    }
                    if (opOut != null) opOut[0] = "INSERT";
                    return obj;
                }
            """;
    }
}
