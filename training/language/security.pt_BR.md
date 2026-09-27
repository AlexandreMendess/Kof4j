[English](security.md) | [Português](security.pt_BR.md)

# Security (kof.security)

`kof.security` é a camada de segurança da Standard Library: senhas, crypto,
JWT, segredos e autenticação web — secure by default, com gaps de target
reportados em compile-time (SECN00x).

## Intenção

```kof
passwords.hash(password)                  // secure by default
passwords.verify(password, storedHash)    // constant-time
jwt.create(claimsJson, secret)            // HS256 + iat/exp
jwt.verify(token, secret, iss, aud)       // sig + exp + iss + aud
secrets.get("API_KEY")                    // env, nunca logado
secrets.redact(value)                     // para logs
secrets.of("sk-...") / secrets.secret("API_KEY")   // Secret: imprime Secret(*** )
secrets.fromBytes(u8)                     // Secret a partir de bytes
secret.reveal() / secret.redacted()       // único export cru / "***"
secret == otherSecret                     // igualdade de conteúdo constant-time
secrets.keyFromHex(hex) / keyFromPem(path) / keyFromKeystore(path, alias, pwd)  // KeyHandle
crypto.hmacSha256(keyHandle, data)        // sobrecarga com chave, chave crua nunca exposta
keyHandle.rotate()                        // revoga o handle antigo (SECN010 no reuso)
security.constantTimeEquals(a, b)         // comparação segura
crypto.sha256(data) / crypto.hmacSha256(key, data)
crypto.encryptAesGcm(text, keyHex) / decryptAesGcm(ct, keyHex)
```

## Anti-padrões

- `sha256(password)` para armazenar senha — use `passwords.hash`.
- `==` para comparar tokens/hashes — use `security.constantTimeEquals`.
- Imprimir segredos em logs — use `secrets.redact`.
- Confiar no `alg` do token — o Kof fixa HS256.

## Web

```kof
auth.secret("s3cret")
app.use {
    if (!auth.authenticated()) { return "{\"error\":\"unauthorized\"}" }
    if (!auth.hasRole("admin")) { return "{\"error\":\"forbidden\"}" }
    return null
}
```

## Suporte por target (0.5.0-beta)

| Função | JVM | Native | JS |
|--------|-----|--------|----|
| passwords (PBKDF2 600k) | ✅ | ✅ (asm SHA-256 + hmac) | ✅ (via platform) |
| sha256 / hmacSha256 | ✅ | ✅ | ✅ |
| sha512 | ✅ | ✅ (asm FIPS 180-4) | ✅ |
| aesGcm | ✅ | ✅ (asm; SECN002 fechado 30/08) | ❌ SECN002 |
| jwt HS256 | ✅ | ✅ (asm, iat/exp/iss/aud) | ✅ |
| secrets | ✅ | ✅ (/proc/self/environ) | ✅ |
| constantTimeEquals | ✅ | ✅ | ✅ |
| auth web (rateLimit/sessions/apiKeys) | ✅ | ✅ | ✅ |

Referência: docs/stdlib/security.md (0.5.0-beta), learn/36-security.md.