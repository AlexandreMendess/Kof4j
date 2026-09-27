[English](db.md) | [Português](db.pt_BR.md)

# kof.db — SQL through the platform

> **Status: stable (JVM — JDBC; Native — SQLite direct `.so` + MySQL wire) ·
> honest per-driver diagnostics when a driver is not on the classpath.**

| Function | Form |
|----------|------|
| `connect` | `connect(String url) -> String` · `connect(String url, String user, String pass) -> String` |
| `query` | `query(String url, String sql) -> List<String>` · `query(url, sql, binds[1..4]) -> List<String>` |
| `execute` | `execute(String url, String sql) -> Int` · `execute(url, sql, binds[1..4]) -> Int` |
| `close` | `close(String url) -> void` |
| `transaction` | `transaction(callback) -> void` |

```kf
main() {
    var db = db.connect("jdbc:h2:mem:test1;DB_CLOSE_DELAY=-1")
    db.execute(db, "create table users(id int, name varchar(50))")
    db.execute(db, "insert into users values (?, ?)", 1, "Mel")
    var rows = db.query(db, "select * from users order by id")
    println(rows.get(0))   // {"id":1,"name":"Mel"} — rows come back JSON-shaped
    db.close(db)
}
```

- Any JDBC driver on the classpath works on the JVM (`jdbc:h2:`, `jdbc:sqlite:`,
  `jdbc:mariadb:`, ...); a missing driver is a named diagnostic, never a
  silent fallback.
- `?` binds are positional, 1–4 per statement (compile-time arity).
- `transaction { ... }` wraps the callback in commit/rollback.
- Native links SQLite directly and speaks the MySQL wire protocol — same
  `db.connect` call, honest gap codes when a backend is absent.

**See also:** [kof.orm](orm.md) — records mapped without SQL;
[D-DB-GAPS](https://github.com/aminadojava/Kof4j/blob/beta-0.5.0/docs/development/DECISIONS.md) — the native DB queue.
