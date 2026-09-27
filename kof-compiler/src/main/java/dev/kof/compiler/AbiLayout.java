package dev.kof.compiler;

import java.util.ArrayList;
import java.util.List;

/**
 * FFI struct/array ABI — slice 3.8a (spec {@code docs/development/ffi-abi-structs.md} §3).
 *
 * Pure layout + argument-classification engine: given a struct's scalar fields
 * and a target ABI it computes the C layout (size/align/offsets) and the
 * register classes the value is passed in. It binds nothing and changes no
 * language surface — 3.8b (JVM {@code StructLayout}) and 3.7 (native asm)
 * consume it, and the D6-1..D6-5 decisions (which Kof value maps to a struct)
 * are orthogonal to it.
 *
 * Golden = real measurement, never memory: every rule below is locked by
 * {@code AbiLayoutTest} against GCC 13.3 on the host (x86-64), plus
 * {@code aarch64-linux-gnu-gcc} and {@code riscv64-linux-gnu-gcc} (both 13.3),
 * and re-checked live with {@code _Static_assert} on the three targets when the
 * toolchains are present. The measurements that corrected the draft prose are
 * noted inline.
 */
public final class AbiLayout {

    private AbiLayout() {}

    /** Target ABIs the native/FFI backends emit for. */
    public enum Abi { SYSV_X86_64, AAPCS64, RISCV64 }

    /**
     * Argument class of an eightbyte (SysV), eightword (AAPCS64) or doubleword
     * (riscv64). {@code MEMORY}/{@code BYREF}/{@code HFA} are aggregate: a single
     * entry describes the whole value.
     */
    public enum ArgClass { INTEGER, SSE, MEMORY, BYREF, HFA }

    /** Scalar C types a Kof struct field maps to 1:1 (size/align in bytes). */
    public enum Scalar {
        BOOL(1, 1, false),
        CHAR(2, 2, false),
        INT(4, 4, false),
        LONG(8, 8, false),
        FLOAT(4, 4, true),
        DOUBLE(8, 8, true),
        POINTER(8, 8, false);

        public final int size;
        public final int align;
        public final boolean floating;

        Scalar(int size, int align, boolean floating) {
            this.size = size;
            this.align = align;
            this.floating = floating;
        }
    }

    public record Field(String name, Scalar type) {}

    /**
     * @param size    total struct size (rounded up to {@code align})
     * @param align   struct alignment (max field alignment)
     * @param offsets byte offset of each field, same order as the input
     * @param classes argument classes left to right (one entry for MEMORY/BYREF/HFA)
     */
    public record Layout(int size, int align, int[] offsets, List<ArgClass> classes) {

        /** Passed in memory / by reference, never in registers. */
        public boolean byMemory() {
            return classes.size() == 1
                    && (classes.get(0) == ArgClass.MEMORY || classes.get(0) == ArgClass.BYREF);
        }

        /** AAPCS64 homogeneous floating-point aggregate (SIMD registers). */
        public boolean isHfa() {
            return classes.size() == 1 && classes.get(0) == ArgClass.HFA;
        }
    }

    /** Computes the layout of a struct with the given scalar fields on {@code abi}. */
    public static Layout of(Abi abi, List<Field> fields) {
        int offset = 0;
        int structAlign = 1;
        int[] offsets = new int[fields.size()];
        for (int i = 0; i < fields.size(); i++) {
            Scalar t = fields.get(i).type();
            offset = alignUp(offset, t.align);
            offsets[i] = offset;
            offset += t.size;
            structAlign = Math.max(structAlign, t.align);
        }
        int size = alignUp(offset, structAlign);
        return new Layout(size, structAlign, offsets, classify(abi, fields, offsets, size));
    }

    private static List<ArgClass> classify(Abi abi, List<Field> fields, int[] offsets, int size) {
        switch (abi) {
            case SYSV_X86_64:
                return sysv(fields, offsets, size);
            case AAPCS64:
                return aapcs64(fields, size);
            case RISCV64:
                return riscv64(fields, offsets, size);
            default:
                throw new IllegalArgumentException("unknown ABI: " + abi);
        }
    }

    /**
     * x86-64 System V: classify each eightbyte; a value larger than 16 B goes to
     * memory. INTEGER dominates SSE inside one eightbyte (measured: {@code Mixed}
     * has eightbyte0 = INTEGER, eightbyte1 = SSE).
     */
    private static List<ArgClass> sysv(List<Field> fields, int[] offsets, int size) {
        List<ArgClass> out = new ArrayList<>();
        if (size > 16) {
            out.add(ArgClass.MEMORY);
            return out;
        }
        int eightbytes = (size + 7) / 8;
        for (int e = 0; e < eightbytes; e++) {
            int lo = e * 8;
            int hi = lo + 8;
            ArgClass cls = null;
            for (int i = 0; i < fields.size(); i++) {
                Scalar t = fields.get(i).type();
                if (offsets[i] + t.size <= lo || offsets[i] >= hi) {
                    continue;
                }
                cls = merge(cls, t.floating ? ArgClass.SSE : ArgClass.INTEGER);
            }
            out.add(cls == null ? ArgClass.INTEGER : cls);
        }
        return out;
    }

    private static ArgClass merge(ArgClass a, ArgClass b) {
        if (a == null) {
            return b;
        }
        if (b == null) {
            return a;
        }
        return (a == ArgClass.INTEGER || b == ArgClass.INTEGER) ? ArgClass.INTEGER : ArgClass.SSE;
    }

    /**
     * AArch64 AAPCS64: a homogeneous float aggregate (all the same float type,
     * 1..4 members) uses SIMD registers; otherwise &le; 16 B is passed in core
     * eightwords and a larger value by reference (measured: {@code Big} arrives
     * as a pointer in x0 — the draft said "stack").
     */
    private static List<ArgClass> aapcs64(List<Field> fields, int size) {
        List<ArgClass> out = new ArrayList<>();
        if (isHfa(fields)) {
            out.add(ArgClass.HFA);
            return out;
        }
        if (size > 16) {
            out.add(ArgClass.BYREF);
            return out;
        }
        int eightwords = (size + 7) / 8;
        for (int i = 0; i < eightwords; i++) {
            out.add(ArgClass.INTEGER);
        }
        return out;
    }

    /**
     * riscv64 LP64D: &le; 16 B is passed in registers; a struct of at most two
     * fields is <em>flattened</em> — floating fields to FP registers, integer
     * fields packed into 64-bit integer registers; more than two fields is
     * packed into integer doublewords; a larger value goes by reference.
     * All four branches measured (see {@code AbiLayoutTest}).
     */
    private static List<ArgClass> riscv64(List<Field> fields, int[] offsets, int size) {
        List<ArgClass> out = new ArrayList<>();
        if (size > 16) {
            out.add(ArgClass.BYREF);
            return out;
        }
        if (fields.size() <= 2) {
            int intSlot = -1;
            for (int i = 0; i < fields.size(); i++) {
                Scalar t = fields.get(i).type();
                if (t.floating) {
                    out.add(ArgClass.SSE);
                    continue;
                }
                int slot = offsets[i] / 8;
                if (slot != intSlot) {
                    out.add(ArgClass.INTEGER);
                    intSlot = slot;
                }
            }
            return out;
        }
        int doublewords = (size + 7) / 8;
        for (int i = 0; i < doublewords; i++) {
            out.add(ArgClass.INTEGER);
        }
        return out;
    }

    private static boolean isHfa(List<Field> fields) {
        if (fields.isEmpty() || fields.size() > 4) {
            return false;
        }
        Scalar first = fields.get(0).type();
        if (!first.floating) {
            return false;
        }
        for (Field f : fields) {
            if (f.type() != first) {
                return false;
            }
        }
        return true;
    }

    private static int alignUp(int value, int align) {
        return (value + align - 1) / align * align;
    }
}
