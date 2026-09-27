package dev.kof.compiler;

import java.util.ArrayList;
import java.util.List;


/**
 * Compile-time dispatch table for the Kof-native database module
 * ({@code kof.db}) — JDBC por interoperabilidade JVM, API idiomática Kof.
 *
 * <pre>{@code
 * var db = db.connect("jdbc:h2:mem:test")
 * db.execute(db, "create table users(id int, name varchar)")
 * db.execute(db, "insert into users values (?, ?)", 1, "Mel")
 * var rows = db.query<User>(db, "select * from users where id = ?", 1)
 * transaction {
 *     db.execute(db, "insert into users values (2, 'Kof')")
 * }
 * }</pre>
 *
 * <p>Internamente cada chamada mapeia para funções {@code kof_db_*} do
 * {@code dev.kof.runtime.KofRuntime} gerado. Aridade dinâmica (varargs de
 * bind) é resolvida por overloads de aridade fixa (0-4 parâmetros).
 * JS soporta connect/execute/query/transaction não-tipado (DB001 fechado 16/09)
 *  e tipado (DB002 fechado 18/09 — bind no guest via __kof_decode_<T>). */
public final class KofDb {

    private KofDb() {}

    static final Type DB = new Type.ClassType("kof.db", "Db", List.of());

    private static final Type STR = BuiltinTypes.STRING;
    private static final Type INT = Type.PrimitiveType.INT;
    private static final Type VOID = Type.PrimitiveType.VOID;
    private static final Type OBJ = Type.UnknownType.UNKNOWN;

    /** Máximo de parâmetros de bind suportados. */
    static final int MAX_BIND = 4;

    static boolean isDbNamespace(String name) {
        return "db".equals(name);
    }

    /** kof.db: JVM via JDBC; NATIVE (x86_64) e NATIVE_RISCV64/NATIVE_AARCH64
     *  via link direto de libsqlite3 (sem driver) — DB001 fechado 15/09: o
     *  consumidor `sqlite3_*` liga dinamicamente por link-by-use
     *  ({@code NativeCrossLink.needsSqlite} + {@code -lsqlite3}) e o runtime
     *  {code kof_db_*} do cross vive nas fatias RtB46/RtB47. Só o subconjunto
     *  SQLite (URLs `sqlite:*`); MySQL/oracle devolvem null em runtime. JS
     *  soporta kof.db não-tipado e tipado (DB001 fechado 16/09; DB002 fechado
     *  18/09 — bind tipado no guest via `__kof_decode_<T>`, wire untyped).
     *  ANDROID fecha 20/09 (D-DB-GAPS DB-2, §278): o alvo EMITE o mesmo
     *  bytecode do JVM (mesmo JvmBackend), logo as gates de namespace JVM
     *  valem para ele — "android é JVM". */
    static boolean supportedOn(Target target) {
        return target == Target.JVM || target == Target.ANDROID || target == Target.NATIVE
                || target == Target.NATIVE_RISCV64 || target == Target.NATIVE_AARCH64
                || target == Target.JS;
    }

    static String gapCode() {
        return "DB001";
    }

    record DbCall(String function, Type returnType, List<Type> parameterTypes) {}

    static boolean isQuery(String name) {
        return "query".equals(name);
    }

    static boolean isExecute(String name) {
        return "execute".equals(name);
    }

/** X10 fatia 2: nomes aceitos pelo dispatch real (catálogo p/ LSP).
     *  GUARDA: StdCatalogTest exige == case-literals + literais das famílias
     *  isQuery/isExecute da fonte abaixo (19/09: query/execute viviam NAS familias
     *  e ficaram de fora do catálogo — bug de transcription achado na fatia de
     *  assinaturas; o consumidor db.query(db.url,...) é documentado no corpus). */
    static List<String> functions() { return List.of("connect", "query", "execute", "close", "transaction"); }

    /** {@code db.<method>(...) } — resolve aridade e o runtime function. */
    static DbCall staticCall(String name, List<Type> argTypes, boolean typed) {
        int bind = argTypes.size() - 2;
        if (isExecute(name) && bind >= 0 && bind <= MAX_BIND) {
            List<Type> params = new ArrayList<>();
            params.add(STR);
            params.add(STR);
            for (int i = 0; i < bind; i++) params.add(OBJ);
            String fn = bind == 0 ? "kof_db_execute" : "kof_db_execute" + bind;
            return new DbCall(fn, INT, params);
        }
        if (isQuery(name) && bind >= 0 && bind <= MAX_BIND) {
            List<Type> params = new ArrayList<>();
            params.add(STR);
            params.add(STR);
            for (int i = 0; i < bind; i++) params.add(OBJ);
            String fn = bind == 0 ? "kof_db_query0" : "kof_db_query" + bind;
            return new DbCall(fn, new Type.ClassType("kof", "List", List.of(STR)), params);
        }
        return switch (name) {
            case "connect" -> argTypes.size() == 1
                    ? new DbCall("kof_db_connect", STR, List.of(STR))
                    : argTypes.size() == 3
                    ? new DbCall("kof_db_connect2", STR, List.of(STR, STR, STR))
                    : null;
            case "close" -> argTypes.size() == 1
                    ? new DbCall("kof_db_close", VOID, List.of(STR))
                    : null;
            case "transaction" -> argTypes.size() == 1
                    ? new DbCall("kof_db_transaction", VOID, List.of(OBJ))
                    : null;
            default -> null;
        };
    }
}