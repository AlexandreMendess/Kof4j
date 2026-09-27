package dev.kof.compiler.js;

/**
 * Runtime JS do {@code kof.buffer} / tipo nominal {@code Buffer(U8)}
 * (D-R3-BUFFER / D6-3, 21/09). Slice incremental (R6-SCOPE): {@code
 * buffer.alloc(Int)} e {@code Buffer.bytes()} com o MESMO contrato do JVM —
 * um buffer de bytes gerenciado pela linguagem (o programador nunca aloca nem
 * libera; D-R3-HANDLE-LIFETIME). O FFI out-buffer (token {@code B}) segue
 * {@code FFI002} no JS (fatia separada); aqui o namespace passa a existir.
 */
final class JsRuntimeBuffer {

    private JsRuntimeBuffer() {}

    static String BUFFER_RUNTIME = """
            // ── kof.buffer — Buffer(U8) (D-R3-BUFFER) no alvo JS ──────────
            // Espelha KofRuntime$Buffer do JVM: um byte-container opaco. Nome
            // interno (KofBufferBox) p/ não sombrear o `Buffer` global do Node.
            class KofBufferBox {
                constructor(n) { this.data = new Uint8Array(n < 0 ? 0 : n); }
                toString() { return "Buffer[" + this.data.length + "]"; }
            }
            export function kof_buffer_alloc(n) {
                return new KofBufferBox(Number(n));
            }
            export function kof_buffer_bytes(b) {
                return b == null ? [] : Array.from(b.data);
            }
            """;
}
