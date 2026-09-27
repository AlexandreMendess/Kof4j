package dev.kof.compiler.jvm;

/** JVM runtime for the {@code Secret} value type (D-SECRETS face 1, Stage 5 /
 *  3.6). {@code KofRuntime$Secret} wraps the raw text; {@code toString()} is the
 *  REDACTED form ({@code Secret(*** )} — never the value, never a prefix) and
 *  {@code equals} is constant-time, so a {@code println} or an interpolation
 *  cannot leak the value and {@code ==} does not short-circuit on content.
 *  {@code reveal()} is the only export. JS/Native never reach this file (honest
 *  gap SECN008 upstream); Native also would need a zeroable buffer (later face). */
public final class JvmSecretRuntime {
    private JvmSecretRuntime() {}

    static String source() {
        return """
                // ── kof.secrets — Secret value type (D-SECRETS face 1, JVM) ──
                public static final class Secret {
                    private final String value;
                    private Secret(String value) { this.value = value == null ? "" : value; }
                    @Override public String toString() { return "Secret(*** )"; }
                    // Identidade intencional (nunca o conteúdo): um mapa não indexa
                    // segredos por conteúdo — o plano P1 exige isto (D-SECRETS).
                    @Override public int hashCode() { return System.identityHashCode(this); }
                    @Override public boolean equals(Object o) {
                        if (this == o) return true;
                        if (!(o instanceof Secret s)) return false;
                        return kof_sec_constant_time_equals(this.value, s.value);
                    }
                }

                public static Secret kof_sec_secret_of(String value) {
                    return new Secret(value);
                }

                public static Secret kof_sec_secret(String name) {
                    return new Secret(kof_sec_secret_get(name));
                }

                // Um int por byte (0..255); visão Latin-1 — round-trip SEM perda
                // (UTF-8 substituiria sequências inválidas em silêncio, inaceitável
                // para material de chave). D-SECRETS P1.
                public static Secret kof_sec_secret_from_bytes(int[] bytes) {
                    if (bytes == null) return new Secret("");
                    byte[] raw = new byte[bytes.length];
                    for (int i = 0; i < bytes.length; i++) raw[i] = (byte) (bytes[i] & 0xff);
                    return new Secret(new String(raw, java.nio.charset.StandardCharsets.ISO_8859_1));
                }

                public static String kof_sec_secret_reveal(Secret s) {
                    return s == null ? null : s.value;
                }

                public static String kof_sec_secret_redacted(Secret s) {
                    return "***";
                }

                // ── kof.secrets — KeyHandle (D-SECRETS P3, JVM) ──────────────
                // Uma chave nomeada que NUNCA expoe bytes ao guest: so os
                // algoritmos de crypto a consomem. rotate() revoga o handle
                // antigo; usa-lo depois falha com SECN010 (honesto, nunca
                // silencioso).
                public static final class KeyHandle {
                    private final byte[] bytes;
                    private boolean revoked;
                    private KeyHandle(byte[] bytes) { this.bytes = bytes == null ? new byte[0] : bytes; }
                    @Override public String toString() { return "KeyHandle(*** )"; }
                    @Override public int hashCode() { return System.identityHashCode(this); }
                    @Override public boolean equals(Object o) { return this == o; }
                }

                private static byte[] kof_sec_key_material(KeyHandle k) {
                    if (k == null) throw new IllegalArgumentException("KeyHandle nulo");
                    if (k.revoked) throw new IllegalStateException("SECN010: KeyHandle revogado (rotate()); use o handle novo");
                    return k.bytes;
                }

                public static KeyHandle kof_sec_key_from_hex(String hex) {
                    return new KeyHandle(kof_sec_fromHex(hex));
                }

                public static KeyHandle kof_sec_key_from_pem(String path) {
                    try {
                        String text = java.nio.file.Files.readString(java.nio.file.Path.of(path));
                        int begin = text.indexOf("-----BEGIN");
                        int beginEnd = text.indexOf("-----", begin + 10);
                        int end = text.indexOf("-----END", beginEnd + 5);
                        if (begin < 0 || beginEnd < 0 || end < 0) {
                            throw new IllegalArgumentException("PEM sem bloco BEGIN/END: " + path);
                        }
                        String body = text.substring(beginEnd + 5, end).replaceAll("\\\\s", "");
                        return new KeyHandle(java.util.Base64.getDecoder().decode(body));
                    } catch (java.io.IOException e) {
                        throw new RuntimeException("PEM ilegivel: " + path, e);
                    }
                }

                public static KeyHandle kof_sec_key_from_keystore(String path, String alias, String password) {
                    try {
                        String type = path.endsWith(".p12") || path.endsWith(".pfx") ? "PKCS12" : "JKS";
                        java.security.KeyStore ks = java.security.KeyStore.getInstance(type);
                        char[] pw = password == null ? new char[0] : password.toCharArray();
                        try (java.io.InputStream in = java.nio.file.Files.newInputStream(java.nio.file.Path.of(path))) {
                            ks.load(in, pw);
                        }
                        java.security.Key key = ks.getKey(alias, pw);
                        if (key == null) throw new IllegalArgumentException("alias ausente no keystore: " + alias);
                        return new KeyHandle(key.getEncoded());
                    } catch (Exception e) {
                        throw new RuntimeException("keystore ilegivel: " + path, e);
                    }
                }

                public static KeyHandle kof_sec_key_rotate(KeyHandle k) {
                    kof_sec_key_material(k);
                    k.revoked = true;
                    byte[] fresh = new byte[32];
                    KOF_SEC_RANDOM.nextBytes(fresh);
                    return new KeyHandle(fresh);
                }

                public static String kof_sec_hmac_sha256_key(KeyHandle k, String data) {
                    try {
                        javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
                        mac.init(new javax.crypto.spec.SecretKeySpec(kof_sec_key_material(k), "HmacSHA256"));
                        return kof_sec_hex(mac.doFinal(data.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }

                public static String kof_sec_aesgcm_encrypt_key(String plaintext, KeyHandle k) {
                    return kof_sec_aesgcm_encrypt(plaintext, kof_sec_hex(kof_sec_key_material(k)));
                }

                public static String kof_sec_aesgcm_decrypt_key(String ciphertext, KeyHandle k) {
                    return kof_sec_aesgcm_decrypt(ciphertext, kof_sec_hex(kof_sec_key_material(k)));
                }

                public static String kof_sec_chacha20_encrypt_key(String plaintext, KeyHandle k) {
                    return kof_sec_chacha20_encrypt(plaintext, kof_sec_hex(kof_sec_key_material(k)));
                }

                public static String kof_sec_chacha20_decrypt_key(String ciphertext, KeyHandle k) {
                    return kof_sec_chacha20_decrypt(ciphertext, kof_sec_hex(kof_sec_key_material(k)));
                }

                public static String kof_sec_jwt_create_key(String claimsJson, KeyHandle k) {
                    return kof_sec_jwt_create_ttl_bytes(claimsJson, kof_sec_key_material(k), 3600);
                }

                public static String kof_sec_jwt_create_ttl_key(String claimsJson, KeyHandle k, int ttlSeconds) {
                    return kof_sec_jwt_create_ttl_bytes(claimsJson, kof_sec_key_material(k), ttlSeconds);
                }

                public static String kof_sec_jwt_verify_key(String token, KeyHandle k) {
                    return kof_sec_jwt_verify_iss_aud_bytes(token, kof_sec_key_material(k), null, null);
                }

                public static String kof_sec_jwt_verify_iss_aud_key(String token, KeyHandle k, String issuer, String audience) {
                    return kof_sec_jwt_verify_iss_aud_bytes(token, kof_sec_key_material(k), issuer, audience);
                }

                """;
    }
}
