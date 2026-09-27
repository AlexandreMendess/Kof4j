[English](security.md) | [Português](security.pt_BR.md)

# kof.security — primitivas de segurança web

> **Status: JVM ✅ · sessions/api-keys/csrf/headers/rate-limit · faces cross
> no ledger de paridade (linha 14, `SECN00x`).**

| Função | Forma |
|--------|-------|
| `constantTimeEquals` | `constantTimeEquals(String a, String b) -> Bool` |
| `randomHex` / `randomInt` | entropia do OS (`randomHex(Int n)`, `randomInt(Int max)`) |
| `redact` | `redact(String s) -> String` |
| `csrfToken` / `csrfValid` | `csrfToken() -> String` · `csrfValid(String token) -> Bool` |
| `corsAllowed` | `corsAllowed(String origin, String allowed) -> Bool` |
| `cspHeader` / `hstsHeader` / `contentTypeOptionsHeader` / `frameHeader` / `referrerHeader` | os headers de segurança, prontos |
| `rateLimit` | `rateLimit(String key, Int limit, Int windowSeconds) -> Bool` |
| `sessionCreate` / `sessionGet` / `sessionDestroy` | sessões server-side |
| `apiKeyGenerate` / `apiKeyValid` | chaves de API |
| `cookieSet` / `cookieGet` | `cookieSet(name, value[, Map opts]) -> String` · `cookieGet(header, name) -> String` |

```kf
app.post("/login", () -> {
    if (!security.rateLimit(ip, 5, 60)) { throw "devagar" }
    security.sessionCreate(userId)
    security.cookieSet("sid", sid, mapOf("HttpOnly", "true"))
})
```

- Os builders de header emitem o default seguro (`cspHeader()`,
  `hstsHeader()`...) — seguro por padrão é R11, não é opção.
- Comparações sobre segredos passam por `constantTimeEquals` — timing é
  dado.

**Veja também:** [36 — Segurança](../36-security.pt_BR.md) — a história
completa; [kof.auth](auth.pt_BR.md) — autenticação de requisição.
