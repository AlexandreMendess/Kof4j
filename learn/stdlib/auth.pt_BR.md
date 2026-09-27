[English](auth.md) | [Português](auth.pt_BR.md)

# kof.auth — quem está chamando?

> **Status: JVM ✅ · middleware composto + OAuth resource server (JWKS,
> RS/ES) · faces cross no ledger de paridade (linha 14, `SECN007`).**

| Função | Forma |
|--------|-------|
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
auth.resourceServer("https://idp/jwks", "meu-issuer", "minha-api")   // uma vez, no boot

app.get("/admin", () -> {
    if (!auth.authenticated() || !auth.hasRole("admin")) { throw "403" }
    return "ok " + auth.user()
})
```

- `resourceServer` verifica RS256/ES256 via JWKS — sem confusão de alg, sem
  segredo local para tokens de terceiros.
- Dentro de uma requisição, `authenticated`/`user`/`claims`/`hasRole` leem a
  identidade verificada — sem parse manual de header.

**Veja também:** [kof.jwt](jwt.pt_BR.md) — tokens first-party;
[36 — Segurança](../36-security.pt_BR.md).
