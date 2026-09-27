[English](db.md) | [Português](db.pt_BR.md)

# kof.db — SQL pela plataforma

> **Status: estável (JVM — JDBC; Native — SQLite via `.so` direto + wire MySQL) ·
> diagnósticos nomeados por driver quando o driver não está no classpath.**

| Função | Forma |
|--------|-------|
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
    println(rows.get(0))   // {"id":1,"name":"Mel"} — linhas voltam em forma JSON
    db.close(db)
}
```

- Qualquer driver JDBC no classpath funciona no JVM (`jdbc:h2:`, `jdbc:sqlite:`,
  `jdbc:mariadb:`, ...); driver ausente é diagnóstico nomeado, nunca fallback
  silencioso.
- Binds `?` são posicionais, 1–4 por statement (aridade em compile-time).
- `transaction { ... }` embrulha o callback em commit/rollback.
- O Native vincula SQLite direto e fala o wire protocol do MySQL — o mesmo
  `db.connect`, códigos de gap honestos quando um backend está ausente.

**Veja também:** [kof.orm](orm.pt_BR.md) — records mapeados sem SQL;
[D-DB-GAPS](https://github.com/aminadojava/Kof4j/blob/beta-0.5.0/docs/development/DECISIONS.md) — a fila nativa de DB.
