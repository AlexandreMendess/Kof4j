package dev.kof.compiler.js;

/**
 * kof-runtime.mjs — relógio/timers JS (kof.time): sleep cooperativo async
 * (§132/#83-JS), timers de interval e a bomba `__kofSleepStep` que o host
 * {@code KofJsRunner} dirige no GraalJS (sem event-loop nativo). Extraído de
 * {@code JsRuntimeUiWeb} para manter cada classe dentro do orçamento de linhas
 * (gate REFACTOR-500). Concatenado na MESMA mensagem de módulo, na mesma ordem
 * anterior — o JS emitido é byte-a-byte idêntico.
 */
final class JsRuntimeTime {
    private JsRuntimeTime() {
    }

    static String timeRuntime() {
        return """
            // §132/#83-JS: sleep yields to the cooperative scheduler instead of
            // busy-waiting the single JS thread, so concurrent spawned/async tasks
            // advance WHILE a task sleeps. node/browser: the real event loop
            // (setTimeout) resolves it. GraalJS (no event loop — same absence that
            // forces kofTimeInterval onto the cooperative queue): a host pump
            // (KofJsRunner) resolves due sleepers via __kofSleepStep. time.now()/
            // Date.now() stay the REAL clock (no cross-cutting clock-model change).
            const __kofSleepers = [];
            export function kofTimeSleep(ms) {
                const wait = (typeof ms === "number" && ms > 0) ? ms : 0;
                if (typeof setTimeout === "function") {
                    return new Promise(res => setTimeout(res, wait));
                }
                return new Promise(res => __kofSleepers.push({ at: Date.now() + wait, res: res }));
            }
            // Host pump hook (GraalJS only): fire interval jobs (kofTimePump), resolve
            // due sleepers, and return the epoch-ms deadline of the earliest pending
            // sleeper, or -1 when none. The host drains microtasks after this via an
            // eval, sleeps toward the returned deadline, and stops when it is -1 and
            // no spawned tasks remain — i.e. a minimal host-side event loop.
            globalThis.__kofSleepStep = function () {
                if (typeof kofTimePump === "function") { kofTimePump(); }
                const now = Date.now();
                const due = __kofSleepers.filter(s => s.at <= now);
                for (let i = __kofSleepers.length - 1; i >= 0; i--) {
                    if (__kofSleepers[i].at <= now) __kofSleepers.splice(i, 1);
                }
                for (const s of due) { s.res(); }
                let next = -1;
                for (const s of __kofSleepers) { if (next < 0 || s.at < next) next = s.at; }
                return next;
            };

            // ── Cooperative timers (TIME001 fechado): GraalJS não tem
            // event loop nativo nem setInterval, então os jobs vivem numa
            // fila bombeada por kofTimeSleep (que já bloqueia). Em browser/
            // Node, onde setInterval existe, os timers disparam assíncronos.
            const kofTimeJobs = new Map();
            const kofTimeSeq = { value: 0 };
            function kofTimeRunJob(fn) {
                if (typeof fn.invoke === 'function') fn.invoke();
                else if (typeof fn === 'function') fn();
            }
            export function kofTimeInterval(ms, fn) {
                if (typeof setInterval === 'function') {
                    return "n" + String(setInterval(() => kofTimeRunJob(fn), ms));
                }
                const id = "c" + (++kofTimeSeq.value);
                kofTimeJobs.set(id, { ms: ms, run: () => kofTimeRunJob(fn), next: Date.now() + ms });
                return id;
            }
            function kofTimePump() {
                const now = Date.now();
                for (const [id, job] of kofTimeJobs) {
                    if (now >= job.next) {
                        if (job.dur) {
                            // Duração (D-SCHED-DURATION): âncora avança do
                            // disparo anterior — sem drift; atraso do host
                            // salta em passos inteiros (sem rajada).
                            let anchor = job.next;
                            let next = kofDurationNextFrom(job.dur, anchor);
                            while (next <= now) {
                                anchor = next;
                                next = kofDurationNextFrom(job.dur, anchor);
                            }
                            job.anchor = anchor;
                            job.next = next;
                        } else {
                            job.next = now + (job.cron ? kofCronNextDelayMs(job.cron, now) : job.ms);
                        }
                        job.run();
                    }
                }
            }

            export function kofTimeCancel(id) {
                const key = String(id);
                if (key.charAt(0) === "n") {
                    if (typeof clearInterval === 'function') clearInterval(Number(key.substring(1)));
                    return;
                }
                kofTimeJobs.delete(key);
            }

            // §426 (improved 25/09): time.collect() (manual GC). Previously
            // imported but never exported -> clean compile + module-load
            // failure; the fix is a REAL host request, not a no-op stub (R6).
            // The KofJsRunner host exposes kof_platform.gcCollect()
            // (System.gc(), the exact JVM semantics used by JVM/SCRIPT). A
            // hostless runtime (browser) has no GC control -> honest error,
            // never a silent success (R7).
            export function kofGcCollectNow() {
                const host = globalThis.kof_platform;
                if (host) {
                    host.gcCollect();
                    return;
                }
                throw new Error("kof.time: collect() is not available in this host (no GC control)");
            }
            """;
    }
}
