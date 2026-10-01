package dev.kof.compiler.jvm;

import java.util.List;

/**
 * Runtime do kof.time (sleep/now/interval) — gerado no KofRuntime junto
 * com o JvmRuntime. Separado num arquivo próprio porque o constant pool
 * do javac limita cada string a 65535 bytes.
 */
public final class JvmTimeRuntime {

    private JvmTimeRuntime() {}

    static String source() {
        return """
                // ── kof.time — sleep, now e scheduler (interval) ─────────
                private static final java.util.concurrent.ConcurrentHashMap<String, Thread> KOF_TIME_JOBS =
                        new java.util.concurrent.ConcurrentHashMap<>();
                private static final java.util.concurrent.atomic.AtomicInteger KOF_TIME_SEQ =
                        new java.util.concurrent.atomic.AtomicInteger();

                public static void kof_time_sleep(int ms) {
                    try {
                        Thread.sleep(ms);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }

                public static long kof_time_now() {
                    return System.currentTimeMillis();
                }

                public static void kof_gc_collect_now() {
                    System.gc();
                }

                // ── kof.time (STDLIB S7-wedge) — calendário civil ─────────
                // isLeapYear: ano bissexto (Gregório: %4 && (!%100 || %400)).
                // daysInMonth: 1..12; mês inválido => 0 (paridade nos 4).
                public static boolean kof_time_isLeapYear(int year) {
                    if (year < 1) return false;
                    return year % 4 == 0 && (year % 100 != 0 || year % 400 == 0);
                }

                private static final int[] KOF_TIME_DIM = {31,28,31,30,31,30,31,31,30,31,30,31};

                public static int kof_time_daysInMonth(int year, int month) {
                    if (year < 1 || month < 1 || month > 12) return 0;
                    return (month == 2 && kof_time_isLeapYear(year)) ? 29 : KOF_TIME_DIM[month - 1];
                }

                // Serial civil -> dias desde 1970-01-01 (algoritmo Howard Hinnant,
                // dias-civil; verificado contra referência em 8 datas 1..9999).
                private static long kof_time_epochDay(int year, int month, int day) {
                    long y = year - (month <= 2 ? 1 : 0);
                    long era = Math.floorDiv(y, 400);
                    long yoe = y - era * 400;
                    long mp = month + (month > 2 ? -3 : 9);
                    long doy = Math.floorDiv(153 * mp + 2, 5) + day - 1;
                    long doe = yoe * 365 + Math.floorDiv(yoe, 4) - Math.floorDiv(yoe, 100) + doy;
                    return era * 146097 + doe - 719468;
                }

                private static boolean kof_time_validDate(int y, int m, int d) {
                    if (y < 1 || y > 9999 || m < 1 || m > 12) return false;
                    return d >= 1 && d <= kof_time_daysInMonth(y, m);
                }

                // ISO: 1=segunda .. 7=domingo. Data inválida => 0 (paridade 4).
                public static int kof_time_dayOfWeek(int year, int month, int day) {
                    if (!kof_time_validDate(year, month, day)) return 0;
                    long ed = kof_time_epochDay(year, month, day);
                    return (int) Math.floorMod(ed + 3, 7) + 1;
                }

                // isWeekend (S7-ext): dayOfWeek >= 6 (ISO 1=seg..7=dom).
                // Data inválida => dayOfWeek 0 => false (gating automático).
                public static boolean kof_time_isWeekend(int year, int month, int day) {
                    return kof_time_dayOfWeek(year, month, day) >= 6;
                }

                public static int kof_time_daysBetween(int y1, int m1, int d1,
                                                       int y2, int m2, int d2) {
                    if (!kof_time_validDate(y1, m1, d1) || !kof_time_validDate(y2, m2, d2)) return 0;
                    // 1..9999 => diff cabe em Int (máx ~3.65M dias)
                     return (int) (kof_time_epochDay(y2, m2, d2) - kof_time_epochDay(y1, m1, d1));
                 }

                 // kof_time_age(birthY,birthM,birthDay, refY,refM,refDay) -> Int | 0
                 // Anos COMPLETOS entre as duas datas. Aritmética inteira (sem
                 // java.time) => MESMA fórmula nos 5 alvos => paridade byte-a-byte.
                 // Data inválida => 0 (política do wedge).
                 public static int kof_time_age(int by, int bm, int bd,
                                                int ry, int rm, int rd) {
                     if (!kof_time_validDate(by, bm, bd) || !kof_time_validDate(ry, rm, rd)) return 0;
                     int years = ry - by;
                     if (rm < bm || (rm == bm && rd < bd)) years -= 1;
                     return years;
                 }

                // ── kof.time (STDLIB S7a) — data ISO (String) add/diff ─────
                // "YYYY-MM-DD" estrito; inválido => "" (add) / 0 (diff) —
                // mesma política "invalid => 0" do calendário wedge.
                // §182 (13/09): parse ESTRITO dígito a dígito — contrato
                // declarado "YYYY-MM-DD … dígitos" (Native é a referência).
                // Integer.parseInt aceitava sinal (+999/-9) = desvio do
                // contrato e divergência silenciosa cross-target (regra 5).
                private static int kof_time_digits(String s, int from, int len) {
                    int v = 0;
                    for (int i = from; i < from + len; i++) {
                        char c = s.charAt(i);
                        if (c < '0' || c > '9') return -1;
                        v = v * 10 + (c - '0');
                    }
                    return v;
                }

                private static java.time.LocalDate kof_time_parseIso(String iso) {
                    if (iso == null || iso.length() != 10) return null;
                    if (iso.charAt(4) != '-' || iso.charAt(7) != '-') return null;
                    int y = kof_time_digits(iso, 0, 4);
                    int m = kof_time_digits(iso, 5, 2);
                    int d = kof_time_digits(iso, 8, 2);
                    if (y < 0 || m < 0 || d < 0) return null;
                    if (!kof_time_validDate(y, m, d)) return null;
                    return java.time.LocalDate.of(y, m, d);
                }

                public static String kof_time_addDays(String iso, int days) {
                    java.time.LocalDate ld = kof_time_parseIso(iso);
                    if (ld == null) return "";
                    java.time.LocalDate r = ld.plusDays(days);
                    if (r.getYear() < 1 || r.getYear() > 9999) return "";
                    return String.format("%04d-%02d-%02d", r.getYear(), r.getMonthValue(), r.getDayOfMonth());
                }

                // S7a-ext — ISO date + N meses. Aritmética inteira pura (sem
                // java.time no resultado): t = ano*12 + (mes-1) + n; y1 = t/12;
                // m1 = t%12 + 1; d1 = min(d, daysInMonth(y1,m1)) (clamp de fim de
                // mês). Pré-guarda t em [12,119999] => divisão sempre positiva
                // (idêntico nos 5 alvos). Inválida/out-of-range => "" (mesma
                // política do addDays). Provado byte-idêntico ao java.time
                // (3M+ casos fuzz), e portanto ao Native.
                public static String kof_time_addMonths(String iso, int months) {
                    java.time.LocalDate ld = kof_time_parseIso(iso);
                    if (ld == null) return "";
                    long t = (long) ld.getYear() * 12 + (ld.getMonthValue() - 1) + (long) months;
                    if (t < 12 || t > 119999) return "";
                    int y1 = (int) (t / 12);
                    int m1 = (int) (t % 12) + 1;
                    int d1 = Math.min(ld.getDayOfMonth(), kof_time_daysInMonth(y1, m1));
                    return String.format("%04d-%02d-%02d", y1, m1, d1);
                }

                public static int kof_time_diffDays(String iso1, String iso2) {
                    java.time.LocalDate a = kof_time_parseIso(iso1);
                    java.time.LocalDate b = kof_time_parseIso(iso2);
                    if (a == null || b == null) return 0;
                    long diff = java.time.temporal.ChronoUnit.DAYS.between(a, b);
                    return (diff < Integer.MIN_VALUE || diff > Integer.MAX_VALUE)
                            ? 0 : (int) diff;
                }

                // ── kof.time (STDLIB S7e) — hoje/formato UTC (D-STDLIB) ────
                // D1: UTC-only em TODOS os alvos (deriva de now() em UTC).
                // D4: zero pattern-DSL; invalidade => "". D5: isToday =
                // igualdade com a data UTC de now(). Serial = dias-civil
                // (epochDay Hinnant acima — MESMA base do add/diffDays).
                public static String kof_time_todayIso() {
                    long epochDay = Math.floorDiv(System.currentTimeMillis(), 86400000L);
                    long[] ymd = kof_time_civilFromEpochDay(epochDay);
                    return String.format("%04d-%02d-%02d", ymd[0], ymd[1], ymd[2]);
                }

                public static String kof_time_formatDateIso(int year, int month, int day) {
                    if (!kof_time_validDate(year, month, day)) return "";
                    return String.format("%04d-%02d-%02d", year, month, day);
                }

                public static boolean kof_time_isToday(int year, int month, int day) {
                    return kof_time_isValidIso(year, month, day)
                            && kof_time_formatDateIso(year, month, day).equals(kof_time_todayIso());
                }

                private static boolean kof_time_isValidIso(int y, int m, int d) {
                    return kof_time_validDate(y, m, d);
                }

                // Inversa Hinnant (dias-civil -> [y,m,d]) — MESMO algoritmo do
                // .Lka_civil x86 (RuntimeTimeIso) e .Lu8_civil riscv (B33).
                private static long[] kof_time_civilFromEpochDay(long z) {
                    z += 719468;
                    long era = Math.floorDiv(z, 146097);
                    long doe = z - era * 146097;
                    long yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365;
                    long y = yoe + era * 400;
                    long doy = doe - (365 * yoe + yoe / 4 - yoe / 100);
                    long mp = (5 * doy + 2) / 153;
                    long d = doy - (153 * mp + 2) / 5 + 1;
                    long m = mp < 10 ? mp + 3 : mp - 9;
                    return new long[]{m <= 2 ? y + 1 : y, m, d};
                }

                // ── kof.time (STDLIB S7f) — hoursBetween (D3) ──────────────
                // floor simétrico: conta horas COMPLETAS entre os instantes
                // (data+hora), truncado em direção a zero (mesma convenção
                // daysBetween). Datas inválidas => 0; hora fora de 0..23
                // também invalida o instante (paridade do gating do wedge).
                public static int kof_time_hoursBetween(int y1, int m1, int d1, int h1,
                                                        int y2, int m2, int d2, int h2) {
                    if (!kof_time_validDate(y1, m1, d1) || !kof_time_validDate(y2, m2, d2)) return 0;
                    if (h1 < 0 || h1 > 23 || h2 < 0 || h2 > 23) return 0;
                    long hours1 = kof_time_epochDay(y1, m1, d1) * 24 + h1;
                    long hours2 = kof_time_epochDay(y2, m2, d2) * 24 + h2;
                    long diff = hours2 - hours1;
                    return (diff < Integer.MIN_VALUE || diff > Integer.MAX_VALUE)
                            ? 0 : (int) diff;
                }

                // ── kof.time (STDLIB S7g) — parseDateIso (D4) ──────────────
                // STR "YYYY-MM-DD" estrito -> serial daysFromEpoch; inválido
                // => 0 (mesma política do calendário wedge). Serial = MESMO
                // domínio de hoursBetween/daysBetween (recomposição fecha).
                public static int kof_time_parseDateIso(String iso) {
                    java.time.LocalDate ld = kof_time_parseIso(iso);
                    if (ld == null) return 0;
                    return (int) kof_time_epochDay(ld.getYear(), ld.getMonthValue(), ld.getDayOfMonth());
                }

                // ── kof.time (STDLIB S7h) — tzOffsetSeconds (D1) ───────────
                // Fuso do HOST como getter explícito (segundos leste+).
                // D1: todayIso/isToday NUNCA usam isto (UTC-only em todos
                // os alvos) — sem paridade acidental de fuso.
                public static int kof_time_tzOffsetSeconds() {
                    return java.time.ZoneId.systemDefault().getRules().getOffset(java.time.Instant.now()).getTotalSeconds();
                }

                public static String kof_time_interval(int ms, Object fn) {
                    if (ms <= 0) throw new IllegalArgumentException("interval must be positive: " + ms);
                    String id = "job-" + KOF_TIME_SEQ.incrementAndGet();
                    Thread t = new Thread(() -> {
                        try {
                            java.lang.reflect.Method invoke = fn.getClass().getMethod("invoke");
                            while (KOF_TIME_JOBS.containsKey(id)) {
                                Thread.sleep(ms);
                                if (!KOF_TIME_JOBS.containsKey(id)) break;
                                invoke.invoke(fn);
                            }
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        } catch (java.lang.reflect.InvocationTargetException e) {
                            if (e.getCause() instanceof RuntimeException re) throw re;
                            throw new RuntimeException(e.getCause());
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    }, "kof-time-" + id);
                    t.setDaemon(true);
                    KOF_TIME_JOBS.put(id, t);
                    t.start();
                    return id;
                }

                public static void kof_time_cancel(String id) {
                    KOF_TIME_JOBS.remove(id);
                }

                public static String kof_scheduler_every(int ms, Object fn) {
                    return kof_time_interval(ms, fn);
                }

                // ── kof.scheduler.at — duração idiomática (D-SCHED-DURATION)
                // Além do cron de 5 campos, `at` aceita expressões como
                // "30m", "90s", "1d&30m": termo = dígitos + unidade, unidade
                // ∈ { s, m, h, d, M, a }; composição com '&'. s/m/h/d são
                // fixos em ms; M/a avançam o calendário UTC com clamp no
                // último dia do mês alvo (2024-01-31 + 1M = 2024-02-29).
                // Malformada lança IllegalArgumentException (R6 — nunca
                // silencioso). Native mantém o gap honesto CRON001.
                /** { fixedMs, months, years }; null se a expressão NÃO é
                 *  duração (cai no caminho cron). */
                static long[] kof_duration_parse(String expr) {
                    if (expr == null) return null;
                    String e = expr.trim();
                    if (e.isEmpty()) return null;
                    String[] terms = e.split("&", -1);
                    if (terms.length == 0) return null;
                    long fixed = 0;
                    long months = 0;
                    long years = 0;
                    for (String raw : terms) {
                        String t = raw.trim();
                        if (t.isEmpty() || !Character.isDigit(t.charAt(0))) return null;
                        int i = 0;
                        while (i < t.length() && Character.isDigit(t.charAt(i))) i++;
                        if (i == t.length() || i > 18) return null;
                        long n;
                        try { n = Long.parseLong(t.substring(0, i)); }
                        catch (NumberFormatException nfe) { return null; }
                        if (n <= 0) return null;
                        // unidade: 'ms' (2 chars) antes da forma de 1 char
                        String unit;
                        if (t.charAt(i) == 'm' && i + 1 < t.length() && t.charAt(i + 1) == 's') {
                            unit = "ms";
                            if (t.length() - i != 2) return null;
                        } else {
                            if (t.length() - i != 1) return null;
                            unit = t.substring(i);
                        }
                        long add;
                        switch (unit) {
                            case "ms" -> add = n;
                            case "s" -> {
                                if (n > Long.MAX_VALUE / 1000L) return null;
                                add = n * 1000L;
                            }
                            case "m" -> {
                                if (n > Long.MAX_VALUE / 60000L) return null;
                                add = n * 60000L;
                            }
                            case "h" -> {
                                if (n > Long.MAX_VALUE / 3600000L) return null;
                                add = n * 3600000L;
                            }
                            case "d" -> {
                                if (n > Long.MAX_VALUE / 86400000L) return null;
                                add = n * 86400000L;
                            }
                            case "M" -> {
                                if (n > 999_999_999L) return null;
                                months += n;
                                add = 0;
                            }
                            case "a" -> {
                                if (n > 999_999_999L) return null;
                                years += n;
                                add = 0;
                            }
                            default -> { return null; }
                        }
                        fixed += add;
                        if (fixed < 0) return null;   // overflow da soma
                    }
                    return new long[]{fixed, months, years};
                }

                /** Próximo instante (epoch ms) para a duração a partir da
                 *  âncora: fixo = âncora + fixedMs; calendário = âncora
                 *  avançada (years, months) + fixedMs. UTC, clamp java.time. */
                static long kof_duration_next_from(long[] dur, long anchorMillis) {
                    long next = anchorMillis + dur[0];
                    if (dur[1] != 0 || dur[2] != 0) {
                        java.time.ZonedDateTime z = java.time.Instant.ofEpochMilli(anchorMillis)
                                .atZone(java.time.ZoneOffset.UTC);
                        if (dur[2] != 0) z = z.plusYears(dur[2]);
                        if (dur[1] != 0) z = z.plusMonths(dur[1]);
                        next = z.toInstant().toEpochMilli() + dur[0];
                    }
                    return next;
                }

                /** Delay (ms) do primeiro disparo a partir de nowMillis. */
                public static long kof_duration_next_delay_ms(String expr, long nowMillis) {
                    long[] dur = kof_duration_parse(expr);
                    if (dur == null) throw new IllegalArgumentException("duration: not a duration expression: " + expr);
                    return kof_duration_next_from(dur, nowMillis) - nowMillis;
                }

                // ── kof.scheduler.at — cron real (CRON001) ──────────────
                // 5 campos: minuto hora dia-do-mês mês dia-da-semana,
                // avaliados em UTC (convenção do stdlib: determinismo e
                // paridade entre alvos — sem DST). Suporta *, a, a-b,
                // a-b/s, */s e listas com vírgula. DOW 0/7 = domingo.
                // Quando dia-do-mês E dia-da-semana são restritos vale o OU
                // (regra cron clássica). Cron inválido lança
                // IllegalArgumentException (alto, nunca silencioso — R6).
                public static long kof_cron_next_delay_ms(String cron, long nowMillis) {
                    return kof_cron_next_delay_ms(kof_cron_parse(cron), nowMillis);
                }

                static long[] kof_cron_parse(String cron) {
                    if (cron == null) throw new IllegalArgumentException("cron: null expression");
                    String[] f = cron.trim().split("\\\\s+");
                    if (f.length != 5) {
                        throw new IllegalArgumentException("cron: expected 5 fields, got " + f.length + ": " + cron);
                    }
                    long[] out = new long[7];
                    out[0] = kof_cron_field(f[0], 0, 59, "minute");
                    out[1] = kof_cron_field(f[1], 0, 23, "hour");
                    out[2] = kof_cron_field(f[2], 1, 31, "day-of-month");
                    out[3] = kof_cron_field(f[3], 1, 12, "month");
                    out[4] = kof_cron_field(f[4], 0, 7, "day-of-week");
                    if ((out[4] & (1L << 7)) != 0) out[4] |= 1L;   // 7 == domingo == 0
                    out[4] &= ~(1L << 7);
                    out[5] = f[2].equals("*") ? 0 : 1;
                    out[6] = f[4].equals("*") ? 0 : 1;
                    return out;
                }

                static long kof_cron_field(String spec, int min, int max, String name) {
                    long mask = 0;
                    for (String part : spec.split(",")) {
                        if (part.isEmpty()) throw new IllegalArgumentException("cron: empty " + name + " field");
                        int step = 1;
                        String range = part;
                        int slash = part.indexOf('/');
                        if (slash >= 0) {
                            range = part.substring(0, slash);
                            step = kof_cron_int(part.substring(slash + 1), name);
                            if (step <= 0) throw new IllegalArgumentException("cron: bad step in " + name + ": " + part);
                        }
                        int lo;
                        int hi;
                        if (range.equals("*")) {
                            lo = min;
                            hi = max;
                        } else {
                            int dash = range.indexOf('-');
                            if (dash >= 0) {
                                lo = kof_cron_int(range.substring(0, dash), name);
                                hi = kof_cron_int(range.substring(dash + 1), name);
                            } else {
                                lo = kof_cron_int(range, name);
                                hi = slash >= 0 ? max : lo;
                            }
                        }
                        if (lo < min || hi > max || lo > hi) {
                            throw new IllegalArgumentException("cron: " + name + " out of range: " + part);
                        }
                        for (int v = lo; v <= hi; v += step) mask |= 1L << v;
                    }
                    return mask;
                }

                static int kof_cron_int(String s, String name) {
                    try {
                        return Integer.parseInt(s.trim());
                    } catch (NumberFormatException e) {
                        throw new IllegalArgumentException("cron: bad " + name + " value: " + s);
                    }
                }

                static boolean kof_cron_matches(long[] f, java.time.ZonedDateTime z) {
                    if ((f[0] >>> z.getMinute() & 1L) == 0) return false;
                    if ((f[1] >>> z.getHour() & 1L) == 0) return false;
                    if ((f[3] >>> z.getMonthValue() & 1L) == 0) return false;
                    boolean domOk = (f[2] >>> z.getDayOfMonth() & 1L) != 0;
                    boolean dowOk = (f[4] >>> (z.getDayOfWeek().getValue() % 7) & 1L) != 0;
                    if (f[5] == 1 && f[6] == 1) return domOk || dowOk;
                    if (f[5] == 1) return domOk;
                    if (f[6] == 1) return dowOk;
                    return true;
                }

                /** Próximo delay (ms) a partir de nowMillis; varre minuto a
                 *  minuto por até 4 anos (cobre 29/02). */
                static long kof_cron_next_delay_ms(long[] f, long nowMillis) {
                    long t = (nowMillis / 60000L) * 60000L + 60000L;
                    for (int i = 0; i < 366 * 24 * 60 * 4; i++) {
                        java.time.ZonedDateTime z = java.time.Instant.ofEpochMilli(t)
                                .atZone(java.time.ZoneOffset.UTC);
                        if (kof_cron_matches(f, z)) return t - nowMillis;
                        t += 60000L;
                    }
                    return 60000L;
                }

                public static String kof_scheduler_at(String cron, Object fn) {
                    long[] dur = kof_duration_parse(cron);
                    if (dur != null) return kof_scheduler_duration(dur, fn);
                    long[] fields = kof_cron_parse(cron);
                    String id = "job-" + KOF_TIME_SEQ.incrementAndGet();
                    Thread t = new Thread(() -> {
                        try {
                            java.lang.reflect.Method invoke = fn.getClass().getMethod("invoke");
                            while (KOF_TIME_JOBS.containsKey(id)) {
                                long delay = kof_cron_next_delay_ms(fields, System.currentTimeMillis());
                                long slept = 0;
                                while (slept < delay && KOF_TIME_JOBS.containsKey(id)) {
                                    long chunk = Math.min(1000L, delay - slept);
                                    Thread.sleep(chunk);
                                    slept += chunk;
                                }
                                if (!KOF_TIME_JOBS.containsKey(id)) break;
                                invoke.invoke(fn);
                            }
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        } catch (java.lang.reflect.InvocationTargetException e) {
                            if (e.getCause() instanceof RuntimeException re) throw re;
                            throw new RuntimeException(e.getCause());
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    }, "kof-cron-" + id);
                    t.setDaemon(true);
                    KOF_TIME_JOBS.put(id, t);
                    t.start();
                    return id;
                }

                /** Agendador por duração (D-SCHED-DURATION): 1º disparo após
                 *  o intervalo, depois repetido; a âncora dos termos M/a
                 *  avança do disparo anterior (nunca de `now` — sem drift).
                 *  Se o host atrasar além do alvo, a âncora salta em passos
                 *  inteiros até o futuro (sem rajada de disparos). */
                static String kof_scheduler_duration(long[] dur, Object fn) {
                    String id = "job-" + KOF_TIME_SEQ.incrementAndGet();
                    Thread t = new Thread(() -> {
                        try {
                            java.lang.reflect.Method invoke = fn.getClass().getMethod("invoke");
                            long anchor = System.currentTimeMillis();
                            while (KOF_TIME_JOBS.containsKey(id)) {
                                long target = kof_duration_next_from(dur, anchor);
                                long now = System.currentTimeMillis();
                                while (target <= now && KOF_TIME_JOBS.containsKey(id)) {
                                    anchor = target;
                                    target = kof_duration_next_from(dur, anchor);
                                }
                                if (!KOF_TIME_JOBS.containsKey(id)) break;
                                long delay = target - now;
                                long slept = 0;
                                while (slept < delay && KOF_TIME_JOBS.containsKey(id)) {
                                    long chunk = Math.min(1000L, delay - slept);
                                    Thread.sleep(chunk);
                                    slept += chunk;
                                }
                                if (!KOF_TIME_JOBS.containsKey(id)) break;
                                anchor = target;
                                invoke.invoke(fn);
                            }
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        } catch (java.lang.reflect.InvocationTargetException e) {
                            if (e.getCause() instanceof RuntimeException re) throw re;
                            throw new RuntimeException(e.getCause());
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    }, "kof-duration-" + id);
                    t.setDaemon(true);
                    KOF_TIME_JOBS.put(id, t);
                    t.start();
                    return id;
                }

                public static void kof_scheduler_cancel(String id) {
                    kof_time_cancel(id);
                }

""";
    }
}
