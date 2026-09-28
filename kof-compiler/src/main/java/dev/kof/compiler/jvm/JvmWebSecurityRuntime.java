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
                public static final class Policy {
                    boolean headers = true;
                    int rateLimit = 0;
                    int rateWindow = 60;
                    String cors = null;
                    boolean csrf = false;
                    String authHeader = null;
                    final java.util.List<String> publicPaths = new java.util.ArrayList<>();
                    boolean requireAuth = false;
                    final java.util.List<String> roles = new java.util.ArrayList<>();
                    final java.util.Map<String, String> responses = new java.util.HashMap<>();

                    /** F1 (D-HTTP-POLICIES): parser unico dos opts — mesmas
                     *  chaves/defaults de hoje. {@code csrfDefault} = true so no
                     *  escopo global (app.security() liga CSRF por padrao). */
                    static Policy parse(java.util.Map<?, ?> opts, boolean csrfDefault) {
                        Policy p = new Policy();
                        p.csrf = csrfDefault;
                        if (opts == null) return p;
                        Object headers = opts.get("headers");
                        if (headers != null) p.headers = kof_web_sec_bool(headers);
                        Object cors = opts.get("cors");
                        if (cors == null) cors = opts.get("corsOrigin");
                        if (cors != null) p.cors = String.valueOf(cors);
                        Object rate = opts.get("rateLimit");
                        if (rate instanceof Number n) {
                            p.rateLimit = n.intValue();
                        } else if (rate != null) {
                            String spec = String.valueOf(rate);
                            int slash = spec.indexOf('/');
                            if (slash <= 0) {
                                throw new IllegalArgumentException(
                                        "rateLimit must be \\"limit/windowSeconds\\", got: " + spec);
                            }
                            p.rateLimit = Integer.parseInt(spec.substring(0, slash).trim());
                            p.rateWindow = Integer.parseInt(spec.substring(slash + 1).trim());
                        }
                        Object csrf = opts.get("csrf");
                        if (csrf != null) p.csrf = kof_web_sec_bool(csrf);
                        Object sessionHeader = opts.get("sessionHeader");
                        if (sessionHeader instanceof String s && !s.isBlank()) {
                            p.authHeader = s.toLowerCase();
                        }
                        Object publicPaths = opts.get("publicPaths");
                        if (publicPaths == null) publicPaths = opts.get("permitAll");
                        if (publicPaths instanceof String csv && !csv.isBlank()) {
                            for (String x : csv.split(",")) {
                                if (!x.isBlank()) p.publicPaths.add(x.trim());
                            }
                        }
                        Object auth = opts.get("auth");
                        if (auth != null) p.requireAuth = kof_web_sec_bool(auth);
                        Object roles = opts.get("roles");
                        if (roles instanceof java.util.List<?> list) {
                            for (Object r : list) {
                                if (r != null) p.roles.add(String.valueOf(r));
                            }
                        } else if (roles != null) {
                            for (String r : String.valueOf(roles).split(",")) {
                                if (!r.trim().isEmpty()) p.roles.add(r.trim());
                            }
                        }
                        Object responses = opts.get("responses");
                        if (responses instanceof java.util.Map<?, ?> map) {
                            for (java.util.Map.Entry<?, ?> e : map.entrySet()) {
                                if (e.getKey() == null || e.getValue() == null) continue;
                                String key = String.valueOf(e.getKey());
                                if (kof_web_sec_response_key(key)) {
                                    p.responses.put(key, String.valueOf(e.getValue()));
                                }
                            }
                        }
                        return p;
                    }
                }

                public static void kof_web_security(String appId) {
                    kof_web_security_opts(appId, null);
                }

                public static void kof_web_security_opts(String appId, java.util.Map<?, ?> opts) {
                    WebApp app = kof_web_app(appId);
                    app.securityConfigured = true;
                    // F1: o escopo global vira um Policy (defaults de hoje:
                    // headers ON; CSRF ON quando app.security() e declarado).
                    app.globalPolicy = Policy.parse(opts, true);
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
