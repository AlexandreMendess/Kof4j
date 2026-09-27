package dev.kof.compiler.nat;

import dev.kof.compiler.IRClass;

import java.nio.charset.StandardCharsets;
import java.util.Set;

/**
 * DB-3/DB-1 cross, slice E-parte-3 (23/09, lane gaps-db): emissor do resolver
 * {@code kof_orm_ctors} para o riscv64 — a ponte className → (vtable, typeId,
 * totalSize) que as faces de LEITURA row-object ({@code kof_orm_find} e,
 * depois, all/where/page) precisam para CONSTRUIR o record no runtime.
 * Espelha {@link NativeOrmCtors} (x86) com ABI riscv explícita:
 * {@code kof_orm_ctors(a0=body ptr, a1=len) -> a0=vtab (0=miss),
 * a1=typeId, a2=totalSize}.
 *
 * <p>Emitido por-PROGRAMA (as constantes vtable/typeId/size só existem aqui),
 * dentro da região podada do runtime ({@code rtStart}/{@code rtEnd} do
 * {@code NativeArchEmitter}) — se nenhuma face de leitura for usada, ele é
 * podado como o resto. No caminho aarch64 ele entra no riscv ANTES da
 * tradução (o tradutor cobre lbu/bne/la/add/li/ret).
 */
final class NativeRiscvOrmCtors {

    private NativeRiscvOrmCtors() {}

    /** Mesma regra de casamento de {@code NativeOrmCtors.findClass}. */
    private static IRClass findClass(NativeBackend nb, String className) {
        for (IRClass clazz : nb.allClassesMap.values()) {
            if (clazz.name().equals(className) || clazz.name().endsWith("/" + className)
                    || className.endsWith("/" + clazz.name())
                    || className.equals(nb.sanitizeName(clazz.name()))) {
                return clazz;
            }
        }
        return null;
    }

    static String emit(NativeBackend nb, Set<String> classNames) {
        StringBuilder sb = new StringBuilder();
        sb.append("            # ---- kof_orm_ctors (riscv): className -> (vtab,tid,size) ---\n");
        sb.append("            .globl kof_orm_ctors\n");
        sb.append("            .type kof_orm_ctors, @function\n");
        sb.append("            kof_orm_ctors:\n");
        int n = 0;
        for (String cn : classNames) {
            IRClass clazz = findClass(nb, cn);
            if (clazz == null) continue;
            int typeId = clazz.typeId();
            int totalSize = nb.getLayout(clazz).totalSize();
            String vtab = nb.sanitizeName(clazz.name()) + "_vtable";
            byte[] bytes = cn.getBytes(StandardCharsets.UTF_8);
            sb.append("            li   t0, ").append(bytes.length).append("\n");
            sb.append("            bne  a1, t0, .Lovc_n").append(n).append("\n");
            sb.append("            li   t3, 0\n");
            sb.append("        .Lovc_c").append(n).append(":\n");
            sb.append("            bge  t3, t0, .Lovc_ce").append(n).append("\n");
            sb.append("            add  t4, a0, t3\n");
            sb.append("            lbu  t5, 0(t4)\n");
            sb.append("            la   t6, .Lovc_b").append(n).append("\n");
            sb.append("            add  t6, t6, t3\n");
            sb.append("            lbu  t6, 0(t6)\n");
            sb.append("            bne  t5, t6, .Lovc_n").append(n).append("\n");
            sb.append("            addi t3, t3, 1\n");
            sb.append("            j    .Lovc_c").append(n).append("\n");
            sb.append("        .Lovc_ce").append(n).append(":\n");
            sb.append("            add  t4, a0, t3\n");
            sb.append("            lbu  t5, 0(t4)\n");
            sb.append("            bnez t5, .Lovc_n").append(n).append("\n");
            sb.append("            la   a0, ").append(vtab).append("\n");
            sb.append("            li   a1, ").append(typeId).append("\n");
            sb.append("            li   a2, ").append(totalSize).append("\n");
            sb.append("            ret\n");
            sb.append("        .Lovc_n").append(n).append(":\n");
            n++;
        }
        sb.append("            li   a0, 0\n");
        sb.append("            ret\n");
        sb.append("        .section .rodata\n");
        int m = 0;
        for (String cn : classNames) {
            IRClass clazz = findClass(nb, cn);
            if (clazz == null) continue;
            byte[] bytes = cn.getBytes(StandardCharsets.UTF_8);
            sb.append("        .Lovc_b").append(m).append(":\n");
            sb.append("            .ascii \"");
            for (byte b : bytes) {
                char c = (char) (b & 0xFF);
                if (c == '"' || c == '\\') sb.append('\\');
                sb.append(c);
            }
            sb.append("\"\n");
            m++;
        }
        sb.append("        .section .text\n");
        return sb.toString();
    }
}
