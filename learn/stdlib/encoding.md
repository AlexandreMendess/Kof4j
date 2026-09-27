[English](encoding.md) | [Português](encoding.pt_BR.md)

# kof.encoding — bytes that every external API requires

> **Status: stable — 4/4 targets** · forms measured from the real dispatchers
> (`StdCatalog`); the namespace chapter set is the 1:1 contract.

| Function | Form |
|----------|------|
| `hexEncode` | `hexEncode(String s) -> String` |
| `hexDecode` | `hexDecode(String s) -> String` |
| `base64Encode` | `base64Encode(String s) -> String` |
| `base64Decode` | `base64Decode(String s) -> String` |
| `urlEncode` | `urlEncode(String s) -> String` |
| `urlDecode` | `urlDecode(String s) -> String` |
| `base64UrlEncode` | `base64UrlEncode(String s) -> String` |
| `base64UrlDecode` | `base64UrlDecode(String s) -> String` |
```
```kf
encoding.hexEncode("Kof")      // 4b6f66
encoding.base64Encode("hi")    // aGk=
encoding.urlEncode("a b&c")    // a%20b%26c
```
```

> Parity: 4/4 (ENC002 closed).

**See also:** [39 — Universal Standard Library](../39-stdlib.md) — the full story and the honest parity table.
