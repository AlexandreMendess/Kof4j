package dev.kof.compiler.js;

/** kof-runtime.mjs — kof.config (P2, docs/stdlib/stdlib-config.md §8.2). */
public final class JsRuntimeUiConfig {
    private JsRuntimeUiConfig() {
    }

    static  String CONFIG_RUNTIME = """
            export function kofConfigGet(key) {
                return kofConfigLookup(key);
            }

            export function kofConfigEnv(key) {
                return kofConfigLookup(key);
            }

            export function kofConfigHas(key) {
                return kofConfigLookup(key) != null ? 1 : 0;
            }

            export function kofConfigRequired(key) {
                const v = kofConfigLookup(key);
                if (v == null) {
                    throw new Error("Kof config: missing required key '" + key + "'");
                }
                return v;
            }

            export function kofConfigStr(key, def) {
                const v = kofConfigLookup(key);
                return v != null ? v : def;
            }

            export function kofConfigInt(key, def) {
                const v = kofConfigLookup(key);
                if (v == null) return def | 0;
                const n = parseInt(v, 10);
                return isNaN(n) ? def | 0 : n | 0;
            }

            export function kofConfigLong(key, def) {
                const v = kofConfigLookup(key);
                if (v == null) return def;
                const n = parseInt(v, 10);
                return isNaN(n) ? def : n;
            }

            export function kofConfigBool(key, def) {
                const v = kofConfigLookup(key);
                if (v == null) return def ? 1 : 0;
                const s = String(v).toLowerCase();
                if (s === 'true' || s === '1' || s === 'yes') return 1;
                if (s === 'false' || s === '0' || s === 'no') return 0;
                return def ? 1 : 0;
            }

            // P2 (docs/stdlib/stdlib-config.md §8.2): interpolação ${key} —
            // resolve referências entre chaves; ciclo/missing → literal.
            function kofConfigInterpolate(value) {
                if (!value || !value.includes('${')) return value;
                const seen = new Set();
                let current = value;
                for (let depth = 0; depth < 16; depth++) {
                    const start = current.indexOf('${');
                    if (start < 0) break;
                    const end = current.indexOf('}', start + 2);
                    if (end < 0) break;
                    const ref = current.slice(start + 2, end);
                    const resolved = kofConfigLookup(ref);
                    if (resolved == null || seen.has(ref)) return value;
                    seen.add(ref);
                    current = current.slice(0, start) + resolved + current.slice(end + 1);
                }
                return current;
            }

            function kofConfigLookup(key) {
                try {
                    if (typeof process !== 'undefined' && process.env) {
                        if (key in process.env) return process.env[key];
                        const kofKey = 'KOF_' + key.replace(/[^a-zA-Z0-9]/g, '_').toUpperCase();
                        if (kofKey in process.env) return process.env[kofKey];
                        const flat = key.replace(/\\./g, '_').toUpperCase();
                        if (flat in process.env) return process.env[flat];
                    }
                } catch (e) {}
                try {
                    if (typeof globalThis !== 'undefined' && globalThis.__kofConfig && key in globalThis.__kofConfig) {
                        return globalThis.__kofConfig[key];
                    }
                } catch (e) {}
                try {
                    // arquivo kof.config no diretório de trabalho (precedência 4)
                    if (!globalThis.__kofConfigFile) {
                        globalThis.__kofConfigFile = {};
                        // Presença por "in" (não `kof_platform.readFile` truthy): o
                        // shim #104 devolve função lançadora p/ prop ausente, então
                        // `kof_platform.readFile` seria truthy fora do host e a
                        // chamada lançaria (engolida pelo catch → config vazia
                        // silenciosa; §653).
                        if (typeof kof_platform !== 'undefined' && ("readFile" in kof_platform)) {
                            const text = kof_platform.readFile('kof.config');
                            if (text) {
                                for (const line of String(text).split('\\n')) {
                                    const t = line.trim();
                                    if (!t || t.startsWith('#')) continue;
                                    const eq = t.indexOf('=');
                                    if (eq <= 0) continue;
                                    globalThis.__kofConfigFile[t.slice(0, eq).trim()] = t.slice(eq + 1).trim();
                                }
                            }
                        }
                    }
                    if (key in globalThis.__kofConfigFile) return kofConfigInterpolate(globalThis.__kofConfigFile[key]);
                } catch (e) {}
                return null;
            }

            """;
}
