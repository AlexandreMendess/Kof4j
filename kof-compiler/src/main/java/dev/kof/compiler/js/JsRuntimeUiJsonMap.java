package dev.kof.compiler.js;

/** kof-runtime.mjs — decode de mapa JSON (§103.1, GitHub #103). */
public final class JsRuntimeUiJsonMap {
    private JsRuntimeUiJsonMap() {
    }

    static  String JSON_MAP_RUNTIME = """
            // §103.1 (#103): decode<Map<String,T>> no JS — monta um Map
            // real (o else dava objeto puro: m.size/m.get quebravam); com
            // decoder, cada valor é bindado à classe.
            export function kofJsonDeep(v) {
                if (Array.isArray(v)) return v.map(kofJsonDeep);
                if (v !== null && typeof v === 'object' && !(v instanceof Map)) {
                    const m = new Map();
                    for (const [k, x] of Object.entries(v)) m.set(k, kofJsonDeep(x));
                    return m;
                }
                return v;
            }

            export function kofJsonDecodeMap(obj) {
                const m = new Map();
                if (obj && typeof obj === 'object')
                    for (const [k, v] of Object.entries(obj)) m.set(k, kofJsonDeep(v));
                return m;
            }

            export function kofJsonDecodeObjectMap(obj, decoder) {
                const m = new Map();
                if (obj && typeof obj === 'object')
                    for (const [k, v] of Object.entries(obj)) m.set(k, decoder(v));
                return m;
            }

            // §106 residual (13/09): encode<Map<String,T>> no JS. JSON.stringify
            // num `Map` devolve '{}' (Map não tem own enumerable properties) —
            // o §106 declarou "4 backends" mas NUNCA testou o JS (matriz não
            // cobria json.encode(Map)). Aqui montamos o objeto com chaves
            // SORTED (mesma semântica JVM/nativo/interp, decisão 2b) via
            // kofJsonEncode por valor (escapa string/número/bool/null
            // corretamente; `tag` fica na assinatura por simetria).
            export function kofJsonEncodeMap(map, tag) {
                const keys = [...map.keys()].sort();
                const parts = [];
                for (const k of keys) {
                    parts.push(JSON.stringify(String(k)) + ':' + kofJsonEncode(map.get(k)));
                }
                return '{' + parts.join(',') + '}';
            }

            // §659b: um campo Long do Kof é um BigInt do JS, e o JSON.stringify
            // recusa BigInt (TypeError: Do not know how to serialize a BigInt) —
            // o encode JSON de qualquer valor com Long (escalar, campo de record,
            // elemento de lista, valor de map) quebrava o processo. kofJsonEncode
            // serializa BigInt como o INTEIRO DECIMAL cru (paridade JVM/nativo/
            // Script). A pré-checagem kofJsonHasBig mantém byte-idêntico o
            // caminho SEM Long (o JSON.stringify puro), então nenhum programa sem
            // Long muda de saída.
            export function kofJsonHasBig(v) {
                if (typeof v === 'bigint') return true;
                if (Array.isArray(v)) {
                    for (const x of v) if (kofJsonHasBig(x)) return true;
                    return false;
                }
                if (v !== null && typeof v === 'object') {
                    for (const k of Object.keys(v)) if (kofJsonHasBig(v[k])) return true;
                    return false;
                }
                return false;
            }

            // §659d: Double inteiro mantém o .0 (JSON.stringify(1.0) → "1";
            // o JVM/Script/nativo imprimem "1.0"). Não-inteiro segue o caminho
            // geral. O registro de campos Double por classe é a face 2 (o
            // record precisa do registro por campo).
            export function kofJsonEncodeDouble(v) {
                return (typeof v === 'number' && Number.isInteger(v)) ? v + '.0'
                        : kofJsonEncode(v);
            }

            // §659d (face record): o toJSON de um record com campo Double/Float
            // inteiro emite a SENTINELA (a mesma mecânica do §659b) e o strip
            // abaixo a devolve COM o .0 — paridade JVM/Script/nativo.
            export function kofJsonHasRecord(v) {
                if (Array.isArray(v)) {
                    for (const x of v) if (kofJsonHasRecord(x)) return true;
                    return false;
                }
                if (v !== null && typeof v === 'object' && !(v instanceof Map)) {
                    if (typeof v.toJSON === 'function') return true;
                    for (const k of Object.keys(v)) if (kofJsonHasRecord(v[k])) return true;
                    return false;
                }
                return false;
            }

            export function kofJsonEncode(v) {
                if (!kofJsonHasBig(v) && !kofJsonHasRecord(v)) return JSON.stringify(v);
                return JSON.stringify(v, (k, x) => typeof x === 'bigint'
                        ? 'KOFBIGINT_SENTINEL_' + x.toString() + '_END' : x)
                    .replace(/"KOFBIGINT_SENTINEL_(-?[0-9]+)_END"/g, '$1')
                    .replace(/"KOFDBL_SENTINEL_(-?[0-9]+\\.[0-9])_END"/g, '$1');
            }

            """;

}
