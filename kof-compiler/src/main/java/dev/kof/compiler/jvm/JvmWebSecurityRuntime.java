package dev.kof.compiler.jvm;

/**
 * Fragmento do source do KofRuntime gerado (REFACTOR-500 Fase 8).
 * app.security() (D-SEC C18) — config do middleware composto.
 * Concatenacao preserva byte-a-byte.
 */
public final class JvmWebSecurityRuntime {

    private JvmWebSecurityRuntime() {}

    static String source() {
        return """
                /**
                 * C18 (D-SEC): {@code app.security([opts])} — middleware
                 * composto de ordem fixa ratificada no DECISIONS §D-SEC:
                 * rate-limit → cors → headers → session → csrf → auth → RBAC.
                 * Os opts (Map) alimentam campos no WebApp; a APLICAÇÃO
                 * acontece no dispatch (JvmRuntimeWebDispatch), que já tem a
                 * WebRequest em mão — nada de handler-reflect.
                 *
                 * Opts documentados (união das duas lanes que fizeram C18):
                 * headers (Bool), cors (String CSV/`*`), corsOrigin (String),
                 * rateLimit (String "n/janelaSeg" ou Number de requests),
                 * csrf (Bool), sessionHeader (String), publicPaths (String CSV),
                 * auth (Bool), roles (String CSV ou List),
                 * responses (Map: unauthorized/forbidden/tooManyRequests/notFound
                 * -> corpo de rejeicao cru; ausente = corpo embutido).
                 *
                 * §5 (Spring model): `permitAll` é alias de `publicPaths`
                 * (allow-list de matchers; todo o resto exige autenticação).
                 * CSRF é ON por padrão (métodos seguros emitem o cookie;
                 * métodos de mutação exigem o double-submit) — pode desligar
                 * com csrf:false.
                 */
                public static void kof_web_security(String appId) {
                    kof_web_security_opts(appId, null);
                }

                public static void kof_web_security_opts(String appId, java.util.Map<?, ?> opts) {
                    WebApp app = kof_web_app(appId);
                    app.securityConfigured = true;
                    // §5 (Spring model): CSRF ON por padrão quando app.security()
                    // é configurado (csrf:false desliga explicitamente).
                    app.securityCsrf = true;
                    if (opts == null) return;
                    Object headers = opts.get("headers");
                    if (headers != null) app.securityHeaders = kof_web_sec_bool(headers);
                    Object cors = opts.get("cors");
                    if (cors == null) cors = opts.get("corsOrigin");
                    if (cors != null) app.securityCors = String.valueOf(cors);
                    Object rate = opts.get("rateLimit");
                    if (rate instanceof Number n) {
                        app.securityRateLimit = n.intValue();
                    } else if (rate != null) {
                        String spec = String.valueOf(rate);
                        int slash = spec.indexOf('/');
                        if (slash <= 0) {
                            throw new IllegalArgumentException(
                                    "rateLimit must be \\"limit/windowSeconds\\", got: " + spec);
                        }
                        app.securityRateLimit = Integer.parseInt(spec.substring(0, slash).trim());
                        app.securityRateWindow = Integer.parseInt(spec.substring(slash + 1).trim());
                    }
                    Object csrf = opts.get("csrf");
                    if (csrf != null) app.securityCsrf = kof_web_sec_bool(csrf);
                    Object sessionHeader = opts.get("sessionHeader");
                    if (sessionHeader instanceof String s && !s.isBlank()) {
                        app.securityAuthHeader = s.toLowerCase();
                    }
                    Object publicPaths = opts.get("publicPaths");
                    if (publicPaths == null) publicPaths = opts.get("permitAll");
                    if (publicPaths instanceof String csv && !csv.isBlank()) {
                        for (String p : csv.split(",")) {
                            if (!p.isBlank()) app.securityPublicPaths.add(p.trim());
                        }
                    }
                    Object auth = opts.get("auth");
                    if (auth != null) app.securityRequireAuth = kof_web_sec_bool(auth);
                    Object roles = opts.get("roles");
                    if (roles instanceof java.util.List<?> list) {
                        for (Object r : list) {
                            if (r != null) app.securityRoles.add(String.valueOf(r));
                        }
                    } else if (roles != null) {
                        for (String r : String.valueOf(roles).split(",")) {
                            if (!r.trim().isEmpty()) app.securityRoles.add(r.trim());
                        }
                    }
                    // D-HTTP-POLICIES (F0): corpos de rejeicao declarativos.
                    // Chaves validas: unauthorized/forbidden/tooManyRequests/
                    // notFound; valor = corpo cru. Ausente = corpo embutido.
                    Object responses = opts.get("responses");
                    if (responses instanceof java.util.Map<?, ?> map) {
                        for (java.util.Map.Entry<?, ?> e : map.entrySet()) {
                            if (e.getKey() == null || e.getValue() == null) continue;
                            String key = String.valueOf(e.getKey());
                            if (kof_web_sec_response_key(key)) {
                                app.securityResponses.put(key, String.valueOf(e.getValue()));
                            }
                        }
                    }
                }

                private static boolean kof_web_sec_response_key(String key) {
                    return "unauthorized".equals(key) || "forbidden".equals(key)
                            || "tooManyRequests".equals(key) || "notFound".equals(key);
                }

                private static boolean kof_web_sec_bool(Object value) {
                    if (value instanceof Boolean b) return b;
                    String s = String.valueOf(value);
                    return !("false".equalsIgnoreCase(s) || "0".equals(s));
                }

                /**
                 * D-SEC C18: security by default — `listen` em produção
                 * (KOF_ENV=production) exige `app.security()` explícito; sem
                 * ele, avisa (nunca falha silenciosamente).
                 */
                private static void kof_web_warn_security_default(WebApp app) {
                    if (!app.securityConfigured
                            && "production".equalsIgnoreCase(System.getenv("KOF_ENV"))) {
                        System.err.println("kof.web: production mode without app.security() — "
                                + "no security middleware configured (D-SEC C18)");
                    }
                }

""";
    }
}
