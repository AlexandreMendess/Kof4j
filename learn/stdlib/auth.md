[English](auth.md) | [Português](auth.pt_BR.md)

# kof.auth — who is calling?

> **Status: JVM ✅ · composite middleware + OAuth resource server (JWKS,
> RS/ES) · cross faces in the parity ledger (row 14, `SECN007`).**

| Function | Form |
|----------|------|
| `secret` | `secret(String s) -> Bool` |
| `token` | `token() -> String` |
| `authenticated` | `authenticated() -> Bool` |
| `claims` | `claims() -> String` |
| `user` | `user() -> String` |
| `hasRole` | `hasRole(String role) -> Bool` |
| `hasPermission` | `hasPermission(String perm) -> Bool` |
| `resourceServer` | `resourceServer(String jwksUrl, String issuer, String audience) -> Bool` |
| `resourceServerVerify` | `resourceServerVerify(String token) -> String` |

```kf
auth.resourceServer("https://idp/jwks", "meu-issuer", "minha-api")   // once, at boot

app.get("/admin", () -> {
    if (!auth.authenticated() || !auth.hasRole("admin")) { throw "403" }
    return "ok " + auth.user()
})
```

- `resourceServer` verifies RS256/ES256 via JWKS — no alg confusion, no
  local secret for third-party tokens.
- Inside a request, `authenticated`/`user`/`claims`/`hasRole` read the
  verified identity — no manual header parsing.

**See also:** [kof.jwt](jwt.md) — first-party tokens;
[36 — Security](../36-security.md).
