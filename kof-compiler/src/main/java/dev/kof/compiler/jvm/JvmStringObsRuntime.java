package dev.kof.compiler.jvm;

/**
 * Fragmento do source do KofRuntime gerado (REFACTOR-500 Fase 8).
 * kof.observability (G5: counters/gauges/histograms/spans/metrics) - parte 4/5 de JvmStringRuntime. Concatenacao preserva byte-a-byte.
 */
public final class JvmStringObsRuntime {

    private JvmStringObsRuntime() {}

    static String source() {
        return """
                // ── kof.observability (G5) ────────────────────────────

                private static final java.util.concurrent.ConcurrentHashMap<String, java.util.concurrent.atomic.AtomicInteger> KOF_OBS_COUNTERS = new java.util.concurrent.ConcurrentHashMap<>();
                private static final java.util.concurrent.ConcurrentHashMap<String, Integer> KOF_OBS_GAUGES = new java.util.concurrent.ConcurrentHashMap<>();
                // histograma: name → [sum, count] (mutuamente sincronizado)
                private static final java.util.concurrent.ConcurrentHashMap<String, long[]> KOF_OBS_HISTOGRAMS = new java.util.concurrent.ConcurrentHashMap<>();

                public static String kof_observability_health() {
                    return "UP";
                }

                public static boolean kof_observability_readiness() {
                    return true;
                }

                public static boolean kof_observability_liveness() {
                    return true;
                }

                public static int kof_observability_counter(String name) {
                    if (name == null) name = "";
                    return KOF_OBS_COUNTERS.computeIfAbsent(name, k -> new java.util.concurrent.atomic.AtomicInteger(0)).incrementAndGet();
                }

                public static int kof_observability_increment(String name, int delta) {
                    if (name == null) name = "";
                    return KOF_OBS_COUNTERS.computeIfAbsent(name, k -> new java.util.concurrent.atomic.AtomicInteger(0)).addAndGet(delta);
                }

                public static void kof_observability_gauge(String name, int value) {
                    if (name == null) name = "";
                    KOF_OBS_GAUGES.put(name, value);
                }

                public static void kof_observability_histogram(String name, int value) {
                    if (name == null) name = "";
                    long[] entry = KOF_OBS_HISTOGRAMS.computeIfAbsent(name, k -> new long[2]);
                    synchronized (entry) {
                        entry[0] += value;      // sum
                        entry[1] += 1;          // count
                    }
                }

                // ── spans W3C com timing (tracing leve) ─────────────────
                // spanStart(name) → handle (id); spanEnd(handle) → JSON
                // {"traceId":..,"spanId":..,"parentSpanId":..,"name":..,
                //  "startMicros":..,"endMicros":..,"durationMicros":..}.
                // Propaga o traceId atual num ThreadLocal (requests web e
                // spawn herdam o trace).
                private static final java.lang.ThreadLocal<String> KOF_OBS_ACTIVE_TRACE = new java.lang.ThreadLocal<>();

                /** Span ativo: nome + relógio monotônico (duração) + epoch (OTLP). */
                private static final class KofObsSpan {
                    final String name;
                    final long startNanos;
                    final long startMicros;
                    KofObsSpan(String name, long startNanos, long startMicros) {
                        this.name = name;
                        this.startNanos = startNanos;
                        this.startMicros = startMicros;
                    }
                }

                /** Span concluído, pronto para export OTLP. */
                private static final class KofObsCompleted {
                    final String traceId;
                    final String spanId;
                    final String name;
                    final long startMicros;
                    final long endMicros;
                    KofObsCompleted(String traceId, String spanId, String name,
                            long startMicros, long endMicros) {
                        this.traceId = traceId;
                        this.spanId = spanId;
                        this.name = name;
                        this.startMicros = startMicros;
                        this.endMicros = endMicros;
                    }
                }

                private static final java.util.concurrent.ConcurrentHashMap<String, KofObsSpan> KOF_OBS_SPANS = new java.util.concurrent.ConcurrentHashMap<>();
                // Ring limitado dos últimos spans concluídos (export OTLP).
                private static final int KOF_OBS_EXPORT_MAX = 256;
                private static final java.util.concurrent.ConcurrentLinkedDeque<KofObsCompleted> KOF_OBS_COMPLETED = new java.util.concurrent.ConcurrentLinkedDeque<>();

                public static String kof_observability_span_start(String name) {
                    String id = kof_observability_trace_id() + kof_observability_span_id();
                    KOF_OBS_SPANS.put(id, new KofObsSpan(name == null ? "" : name,
                            System.nanoTime(), System.currentTimeMillis() * 1000L));
                    return id;
                }

                public static String kof_observability_span_end(String handle) {
                    KofObsSpan span = KOF_OBS_SPANS.remove(handle);
                    if (span == null) return "{}";
                    long endNanos = System.nanoTime();
                    long endMicros = System.currentTimeMillis() * 1000L;
                    long durUs = (endNanos - span.startNanos) / 1000;
                    String trace = KOF_OBS_ACTIVE_TRACE.get();
                    if (trace == null) trace = kof_observability_trace_id();
                    String spanId = handle.substring(32);
                    KOF_OBS_COMPLETED.addLast(new KofObsCompleted(trace, spanId, span.name,
                            span.startMicros, endMicros));
                    while (KOF_OBS_COMPLETED.size() > KOF_OBS_EXPORT_MAX) KOF_OBS_COMPLETED.pollFirst();
                    return "{\\"traceId\\":\\"" + trace + "\\",\\"spanId\\":\\"" + spanId
                            + "\\",\\"parentSpanId\\":\\"\\",\\"name\\":\\""
                            + kofObsJsonEscape(span.name) + "\\",\\"startMicros\\":"
                            + span.startMicros + ",\\"endMicros\\":" + endMicros
                            + ",\\"durationMicros\\":" + durUs + "}";
                }

                /** Escapa \\ e " para embutir o nome num literal JSON. */
                private static String kofObsJsonEscape(String s) {
                    if (s == null) return "";
                    StringBuilder out = new StringBuilder(s.length());
                    for (int i = 0; i < s.length(); i++) {
                        char c = s.charAt(i);
                        if (c == '\\\\' || c == '"') out.append('\\\\');
                        out.append(c);
                    }
                    return out.toString();
                }

                /** Exporta os spans concluídos no formato OTLP/JSON
                 *  ({@code resourceSpans}) — o payload que um coletor
                 *  OpenTelemetry consome em {@code /v1/traces}. Não destrutivo:
                 *  o ring permanece até ser sobrescrito (máx. 256 spans). */
                public static String kof_observability_export_spans() {
                    StringBuilder spans = new StringBuilder();
                    for (KofObsCompleted s : KOF_OBS_COMPLETED) {
                        if (spans.length() > 0) spans.append(',');
                        spans.append("{\\"traceId\\":\\"").append(s.traceId)
                                .append("\\",\\"spanId\\":\\"").append(s.spanId)
                                .append("\\",\\"parentSpanId\\":\\"\\",\\"name\\":\\"")
                                .append(kofObsJsonEscape(s.name))
                                .append("\\",\\"kind\\":1,\\"startTimeUnixNano\\":\\"")
                                .append(s.startMicros * 1000L)
                                .append("\\",\\"endTimeUnixNano\\":\\"")
                                .append(s.endMicros * 1000L).append("\\"}");
                    }
                    return "{\\"resourceSpans\\":[{\\"resource\\":{\\"attributes\\":[{\\"key\\":\\"service.name\\",\\"value\\":{\\"stringValue\\":\\"kof\\"}}]},"
                            + "\\"scopeSpans\\":[{\\"scope\\":{\\"name\\":\\"kof.observability\\"},\\"spans\\":["
                            + spans + "]}]}]}";
                }

                public static void kof_observability_set_trace(String traceId) {
                    if (traceId != null) KOF_OBS_ACTIVE_TRACE.set(traceId);
                }

                /** Exporta counters, gauges e histograms em formato Prometheus
                 *  (text exposition format). Histogramas sem buckets: expostos
                 *  como name_count (counter) + name_sum (gauge). */
                public static String kof_observability_metrics() {
                    StringBuilder sb = new StringBuilder();
                    sb.append(sortedCounterLines(KOF_OBS_COUNTERS, "counter"));
                    sb.append(sortedIntLines(KOF_OBS_GAUGES, "gauge"));
                    // histogramas: sum/count por name
                    java.util.List<String> hnames = new java.util.ArrayList<>(KOF_OBS_HISTOGRAMS.keySet());
                    java.util.Collections.sort(hnames);
                    for (String base0 : hnames) {
                        String base = promName(base0, "");
                        long[] e = KOF_OBS_HISTOGRAMS.get(base0);
                        long sum, count;
                        synchronized (e) { sum = e[0]; count = e[1]; }
                        sb.append("# TYPE ").append(base).append("_count counter\\n");
                        sb.append(base).append("_count ").append(count).append('\\n');
                        sb.append("# TYPE ").append(base).append("_sum gauge\\n");
                        sb.append(base).append("_sum ").append(sum).append('\\n');
                    }
                    return sb.toString();
                }

                private static String sortedCounterLines(
                        java.util.Map<String, java.util.concurrent.atomic.AtomicInteger> m, String type) {
                    StringBuilder sb = new StringBuilder();
                    java.util.List<String> names = new java.util.ArrayList<>(m.keySet());
                    java.util.Collections.sort(names);
                    for (String k : names) {
                        String n = promName(k, "");
                        sb.append("# TYPE ").append(n).append(' ').append(type).append('\\n');
                        sb.append(n).append(' ').append(m.get(k).get()).append('\\n');
                    }
                    return sb.toString();
                }

                private static String sortedIntLines(java.util.Map<String, Integer> m, String type) {
                    StringBuilder sb = new StringBuilder();
                    java.util.List<String> names = new java.util.ArrayList<>(m.keySet());
                    java.util.Collections.sort(names);
                    for (String k : names) {
                        String n = promName(k, "");
                        sb.append("# TYPE ").append(n).append(' ').append(type).append('\\n');
                        sb.append(n).append(' ').append(m.get(k)).append('\\n');
                    }
                    return sb.toString();
                }

                /** Nome Prometheus: sanitiza para [a-zA-Z0-9_:] e acrescenta sufixo. */
                private static String promName(String name, String suffix) {
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < name.length(); i++) {
                        char c = name.charAt(i);
                        if (Character.isLetterOrDigit(c) || c == '_') sb.append(c);
                        else sb.append('_');
                    }
                    if (sb.length() == 0) sb.append("k");
                    sb.append(suffix);
                    return sb.toString();
                }

                public static String kof_observability_request_id() {
                    return java.util.UUID.randomUUID().toString();
                }

                // entity-history Fase 4a (D-ENTITY-HISTORY Q14): request-bound
                // — dentro de um request web o id é ESTÁVEL (o dispatch já
                // seta/limpa KOF_LOG_REQUEST_ID por request); fora de request
                // (jobs/CLI) segue o UUID fresco por chamada.
                public static String kof_observability_correlation_id() {
                    String rid = KOF_LOG_REQUEST_ID.get();
                    if (rid != null) return rid;
                    return kof_observability_request_id();
                }

                public static String kof_observability_trace_id() {
                    return kof_sec_random_hex(16);
                }

                public static String kof_observability_span_id() {
                    return kof_sec_random_hex(8);
                }
""";
    }
}
