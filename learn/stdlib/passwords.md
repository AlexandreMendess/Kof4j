[English](passwords.md) | [Português](passwords.pt_BR.md)

# kof.security.passwords — password hashing done right

> **Status: JVM ✅ (JCA/BC audited primitives — crypto is never homemade,
> R11) · cross faces tracked in the parity ledger (row 14, `SECN00x`).**

| Function | Form |
|----------|------|
| `hash` | `hash(String plain) -> String` |
| `verify` | `verify(String plain, String hash) -> Bool` |
| `needsRehash` | `needsRehash(String hash) -> Bool` |

```kf
var h = passwords.hash(senha)              // bcrypt-family, salted, self-describing
if (passwords.verify(senha, h)) { log.info("login ok") }
if (passwords.needsRehash(h)) { h = passwords.hash(senha) }  // parameter upgrades
```

- The hash string carries its own parameters (cost/salt) — upgrade paths are
  data, not code.
- `verify` is constant-time on the comparison; `needsRehash` tells you when
  stored hashes predate the current parameters.

**See also:** [36 — Security](../36-security.md) — the whole security story.
