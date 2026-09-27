[English](uuid.md) | [Português](uuid.pt_BR.md)

# kof.uuid — RFC 4122 v4 + RFC 9562 v7

> **Status: estável — 4/4 targets** · forms measured from the real dispatchers
> (`StdCatalog`); the namespace chapter set is the 1:1 contract.

| Function | Form |
|----------|------|

| Função | Forma |
|--------|-------|
| `isUuid` | `isUuid(String s) -> Bool` |
| `v4` | `v4() -> String` |
| `v7` | `v7() -> String` |
```
```kf
var id = uuid.v4()             // RFC 4122 v4
var id7 = uuid.v7()            // RFC 9562 v7 — sortable
uuid.isUuid(id)                // true
```
```

> Paridade: 4/4 (SECN000/UUID001 closed).

**Veja também:** [39 — Standard Library universal](../39-stdlib.pt_BR.md) — a história completa e a tabela honesta de paridade.
