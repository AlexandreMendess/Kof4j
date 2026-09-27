package dev.kof.compiler.nat;
import dev.kof.compiler.BuiltinTypes;
import dev.kof.compiler.Type;

/**
 * §107 record/nested (face (3) do native-multiarch, 19/09): descritores de
 * impressão de elementos de coleção para o ASM x86. O argumento do
 * {@code kof_{list,set,map}_to_string} x86 deixou de ser UM byte de tag e
 * passou a ser um PONTEIRO para um nó em `.rodata` emitido no próprio
 * call-site — o typer sabe o tipo estático completo do elemento em tempo de
 * compilação (SEM056: coleção homogênea), então a recursão do print é
 * dirigida por este byte-code, não por tags runtime.
 *
 * <p>Gramática do nó (bytes):
 * <ul>
 *   <li>{@code 0..7} — terminal, MESMA numeração do §107 escalares/§284-map
 *       (0=int/char/short/byte, 1=String, 2=Long, 3=Bool, 4=Double, 5=Float,
 *       6=desconhecido→"?", 7=caixa de valor de Map);</li>
 *   <li>{@code 8, offLo, offHi} — objeto com {@code toString} na vtable
 *       (records e classes que o definem; off = índice×8; sem vtable → 6);</li>
 *   <li>{@code 9, <child>} — List/Set aninhada; child descreve os elementos;</li>
 *   <li>{@code 10, valOffLo, valOffHi, <key>, <val>} — Map aninhado;
 *       valOff = 3 + len(key) (distância do início do nó ao nó do valor).</li>
 *   <li>{@code 11, esz, <child>} — array primitivo aninhado (§388-B);
 *       esz = tamanho do elemento no bloco (mesma fonte do
 *       {@code elementTypeSize} do alloc); child descreve o componente.</li>
 * </ul>
 *
 * <p>Profundidade/tamanho: nó que estoura o cap ({@value #MAX_BYTES} bytes)
 * degrada honestamente para {@code 6} ("?") — nunca lixo de ponteiro (R6).
 * O lado riscv/aarch64 mantém as tags legadas (riscv emite o byte
 * imediatamente, como antes) — a face cross do record/aninhado continua
 * catalogada em docs/development/native-multiarch.md.
 */
public final class NativePrintDescriptors {

    private NativePrintDescriptors() {}

    static final int REC = 8;
    static final int NLIST = 9;
    static final int NMAP = 10;
    static final int NARRAY = 11;
    static final int MAX_BYTES = 64;

    /** Nó descritor do tipo de elemento; {@code mapValuePos} replica o
     *  §284-map: valor de Map da família {int,char,short,byte,long} é caixa
     *  física → tag 7. */
    static byte[] node(NativeBackend nb, Type t, boolean mapValuePos) {
        byte[] n = node0(nb, t, mapValuePos);
        return n.length > MAX_BYTES ? new byte[]{6} : n;
    }

    private static byte[] node0(NativeBackend nb, Type t, boolean mapValuePos) {
        Type e = t instanceof Type.NullableType nt ? nt.inner() : t;
        if (e instanceof Type.PrimitiveType pt) {
            if (mapValuePos && NativeBoxTags.mapValueTag(t) == 7) return new byte[]{7};
            switch (pt.name()) {
                case "int", "char", "short", "byte": return new byte[]{0};
                case "long": return new byte[]{2};
                case "bool": return new byte[]{3};
                case "float": return new byte[]{5};
                default: return NativeTypeKinds.isDoubleType(pt)
                        ? new byte[]{4} : new byte[]{6};
            }
        }
        if (BuiltinTypes.isString(e)) return new byte[]{1};
        // §388-B: array primitivo (cru ou aninhado) — [11][esz][child].
        if (e instanceof Type.ArrayType at) {
            byte[] child = node0(nb, at.componentType(), false);
            if (child.length > MAX_BYTES - 2) return new byte[]{6};
            byte[] out = new byte[2 + child.length];
            out[0] = NARRAY;
            out[1] = (byte) NativeOpHelpers.elementTypeSize(nb, at.componentType());
            System.arraycopy(child, 0, out, 2, child.length);
            return out;
        }
        if (e instanceof Type.ClassType ct) {
            if (BuiltinTypes.isList(ct) || BuiltinTypes.isSet(ct)) {
                Type elem = BuiltinTypes.isList(ct)
                        ? BuiltinTypes.listElement(ct) : BuiltinTypes.setElement(ct);
                byte[] child = node0(nb, elem, false);
                if (child.length > MAX_BYTES - 1) return new byte[]{6};
                byte[] out = new byte[1 + child.length];
                out[0] = NLIST;
                System.arraycopy(child, 0, out, 1, child.length);
                return out;
            }
            if (BuiltinTypes.isMap(ct)) {
                byte[] key = node0(nb, BuiltinTypes.mapKey(ct), false);
                byte[] val = node0(nb, BuiltinTypes.mapValue(ct), true);
                int valOff = 3 + key.length;
                if (key.length > MAX_BYTES || val.length > MAX_BYTES - valOff
                        || valOff + val.length > MAX_BYTES) {
                    return new byte[]{6};
                }
                byte[] out = new byte[valOff + val.length];
                out[0] = NMAP;
                out[1] = (byte) (valOff & 0xff);
                out[2] = (byte) ((valOff >> 8) & 0xff);
                System.arraycopy(key, 0, out, 3, key.length);
                System.arraycopy(val, 0, out, valOff, val.length);
                return out;
            }
            int tosIdx = nb.findVirtualMethodIndex(ct.name(), "toString",
                    java.util.List.of());
            if (tosIdx >= 0) {
                int off = tosIdx * 8;
                return new byte[]{REC, (byte) (off & 0xff), (byte) ((off >> 8) & 0xff)};
            }
        }
        return new byte[]{6};
    }

    /** Asciifica o nó como `.byte`s rotulados na troca de seção idiomática
     *  (RuntimeStringBase/emitRodata) e devolve o rótulo. */
    static String emit(StringBuilder sb, int id, byte[] node) {
        String label = ".Lpd_" + id;
        sb.append("    .section .rodata\n");
        sb.append(label).append(":\n");
        sb.append("    .byte ");
        for (int i = 0; i < node.length; i++) {
            if (i > 0) sb.append(", ");
            sb.append(node[i] & 0xff);
        }
        sb.append("\n");
        sb.append("    .section .text\n");
        return label;
    }
}
