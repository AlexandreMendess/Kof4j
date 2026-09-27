[English](jwt.md) | [Português](jwt.pt_BR.md)

# kof.security.jwt — signed tokens

> **Status: JVM ✅ · cross faces in the parity ledger (row 14).**

| Function | Form |
|----------|------|
| `create` | `create(String claims, String secret) -> String` · `create(claims, secret, Int ttlSeconds) -> String` |
| `verify` | `verify(String token, String secret) -> String` · `verify(token, secret, String iss, String aud) -> String` |
| `secret` | `secret() -> String` |

```kf
var token = jwt.create("{\"sub\":\"mel\",\"role\":\"admin\"}", key, 3600)
var claims = jwt.verify(token, key, "my-issuer", "my-aud")
```

- `verify` returns the claims payload or throws — no silent invalid token.
- The 4-arg `verify` checks issuer and audience (alg-confusion excluded by
  the implementation).
- For OAuth/OIDC resource servers see [kof.auth](auth.md) (`resourceServer`
  with JWKS).

**See also:** [kof.auth](auth.md) — JWKS-based verification.
