[English](orm.md) | [Português](orm.pt_BR.md)

# kof.orm — records como linhas, sem cerimônia SQL

> **Status: paridade total de plataforma (D-DB-GAPS FECHADO 24/09) — as 13
> faces rodam byte-idênticas em JVM, Native x86-64, riscv64/aarch64 (SQLite)
> e no wire MySQL; JS desde 18/09 (`KofJsOrmBridge`). Prova: `KofOrmE2ETest`.**

| Função | Forma |
|--------|-------|
| `create` | `create(String entity) -> Bool` |
| `save` | `save(String entity, Object row) -> Object` |
| `saveAll` | `saveAll(String entity, List rows) -> Bool` |
| `find` | `find(String entity, Object id) -> Object` |
| `all` | `all(String entity) -> List` |
| `where` | `where(String entity, String cond, Object value) -> List` · `where(entity, col, op, value) -> List` |
| `page` | `page(String entity, Object offset, Object limit) -> List` |
| `count` | `count(String entity) -> Long` · `count(entity, String where, Object value) -> Long` |
| `delete` | `delete(String entity, Object id) -> Bool` |
| `deleteAll` | `deleteAll(String entity) -> Bool` |
| `migrate` | `migrate(String url, String user, String pass) -> Bool` |

```kf
record User(Int id, String name)

var u = orm.save("users", User(1, "Mel"))
var back = orm.find("users", 1) as User
println(back.name())                 // Mel
var adultos = orm.where("users", "id", ">", 0)
println(orm.count("users"))          // 1
```

- O nome da entity é a tabela; linhas são records — sem DTO, sem mapper, sem
  annotations.
- `where` tem as duas formas medidas: `where(entity, cond, value)` e
  `where(entity, col, op, value)`.
- `migrate` cria o schema a partir das entities declaradas.

**Veja também:** [kof.db](db.pt_BR.md) — SQL cru quando precisar;
[D-DB-GAPS](https://github.com/aminadojava/Kof4j/blob/beta-0.5.0/docs/development/DECISIONS.md) — fila de paridade.
