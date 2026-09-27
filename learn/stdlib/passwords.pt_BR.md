[English](passwords.md) | [Português](passwords.pt_BR.md)

# kof.security.passwords — hash de senha feito certo

> **Status: JVM ✅ (primitivas auditadas JCA/BC — crypto nunca é caseira,
> R11) · faces cross rastreadas no ledger de paridade (linha 14, `SECN00x`).**

| Função | Forma |
|--------|-------|
| `hash` | `hash(String plain) -> String` |
| `verify` | `verify(String plain, String hash) -> Bool` |
| `needsRehash` | `needsRehash(String hash) -> Bool` |

```kf
var h = passwords.hash(senha)              // família bcrypt, com salt, autodescritivo
if (passwords.verify(senha, h)) { log.info("login ok") }
if (passwords.needsRehash(h)) { h = passwords.hash(senha) }  // upgrade de parâmetros
```

- A string de hash carrega os próprios parâmetros (custo/salt) — caminhos de
  upgrade são dados, não código.
- `verify` é constant-time na comparação; `needsRehash` diz quando os hashes
  guardados precedem os parâmetros atuais.

**Veja também:** [36 — Segurança](../36-security.pt_BR.md) — a história
completa de segurança.
