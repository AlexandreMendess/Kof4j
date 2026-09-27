[English](jwt.md) | [Português](jwt.pt_BR.md)

# kof.security.jwt — tokens assinados

> **Status: JVM ✅ · faces cross no ledger de paridade (linha 14).**

| Função | Forma |
|--------|-------|
| `create` | `create(String claims, String secret) -> String` · `create(claims, secret, Int ttlSeconds) -> String` |
| `verify` | `verify(String token, String secret) -> String` · `verify(token, secret, String iss, String aud) -> String` |
| `secret` | `secret() -> String` |

```kf
var token = jwt.create("{\"sub\":\"mel\",\"role\":\"admin\"}", key, 3600)
var claims = jwt.verify(token, key, "meu-issuer", "meu-aud")
```

- `verify` devolve o payload de claims ou lança — sem token inválido
  silencioso.
- O `verify` de 4 argumentos checa issuer e audience (confusão de alg
  excluída pela implementação).
- Para resource servers OAuth/OIDC veja [kof.auth](auth.pt_BR.md)
  (`resourceServer` com JWKS).

**Veja também:** [kof.auth](auth.pt_BR.md) — verificação por JWKS.
