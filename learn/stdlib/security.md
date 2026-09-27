[English](security.md) | [Português](security.pt_BR.md)

# kof.security — web security primitives

> **Status: JVM ✅ · sessions/api-keys/csrf/headers/rate-limit · cross faces
> in the parity ledger (row 14, `SECN00x`).**

| Function | Form |
|----------|------|
| `constantTimeEquals` | `constantTimeEquals(String a, String b) -> Bool` |
| `randomHex` / `randomInt` | OS entropy (`randomHex(Int n)`, `randomInt(Int max)`) |
| `redact` | `redact(String s) -> String` |
| `csrfToken` / `csrfValid` | `csrfToken() -> String` · `csrfValid(String token) -> Bool` |
| `corsAllowed` | `corsAllowed(String origin, String allowed) -> Bool` |
| `cspHeader` / `hstsHeader` / `contentTypeOptionsHeader` / `frameHeader` / `referrerHeader` | the security headers, pre-built |
| `rateLimit` | `rateLimit(String key, Int limit, Int windowSeconds) -> Bool` |
| `sessionCreate` / `sessionGet` / `sessionDestroy` | server-side sessions |
| `apiKeyGenerate` / `apiKeyValid` | API keys |
| `cookieSet` / `cookieGet` | `cookieSet(name, value[, Map opts]) -> String` · `cookieGet(header, name) -> String` |

```kf
app.post("/login", () -> {
    if (!security.rateLimit(ip, 5, 60)) { throw "slow down" }
    security.sessionCreate(userId)
    security.cookieSet("sid", sid, mapOf("HttpOnly", "true"))
})
```

- The header builders emit the secure default (`cspHeader()`, `hstsHeader()`...)
  — secure by default is R11, not an option.
- Comparisons over secrets go through `constantTimeEquals` — timing is data.

**See also:** [36 — Security](../36-security.md) — the full story;
[kof.auth](auth.md) — request authentication.
