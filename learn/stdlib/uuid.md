[English](uuid.md) | [Português](uuid.pt_BR.md)

# kof.uuid — RFC 4122 v4 + RFC 9562 v7

> **Status: stable — 4/4 targets** · forms measured from the real dispatchers
> (`StdCatalog`); the namespace chapter set is the 1:1 contract.

| Function | Form |
|----------|------|
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

> Parity: 4/4 (SECN000/UUID001 closed).

**See also:** [39 — Universal Standard Library](../39-stdlib.md) — the full story and the honest parity table.
