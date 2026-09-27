package dev.kof.compiler.nat;

// DB-3/DB-1 cross, slice E parte 2b (23/09, lane gaps-db): kof_orm_save_all
// no riscv64 — F2c3 de RuntimeOrm10 x86. aarch64 herda via tradutor.
//
// Contrato (host JvmOrmRuntime.kof_orm_save_all + RuntimeOrm10): loop
// kof_list_size / kof_list_get acumulando kof_orm_save por item; a instância
// patchada devolvida pelo save é DESCARTADA (a List de entrada guarda os
// objetos originais, lidos de volta por all/where). Retorna true quando o
// loop termina; erro de SQL lança "sqlite: " + errmsg pelo próprio save (R6).
// Sem className: o item já carrega vtable/typeId (o save é row-object).
//
// GC-safe: id/items/table/schema ficam em SLOTS de pilha (o scan conservativo
// do GC vê a stack); o item intermediário só vive em registrador durante o
// call do save, mas a List está no slot 8 — como o x86 (slot 8(%rsp)).
//
// ARMADILHA aarch64 (ver RtB55/debugging-native §6): s10 -> x16 é
// caller-saved, então NADA de estado vivo em s10 através de call; o índice
// fica no slot 32(sp).
public final class NativeRiscvAsmRtB56 {

    private NativeRiscvAsmRtB56() {}

    static String RISCV_RUNTIME_ASM_B_56 = """
            .section .text
            # ---------------------------------------------------------------
            # kof_orm_save_all(id*, items*, table*, schema*) -> Bool (a0=1)
            #   loop kof_list_get -> kof_orm_save (retorno descartado).
            #   slots: 0 id | 8 items | 16 table | 24 schema | 32 i | 40 n
            # ---------------------------------------------------------------
            .globl kof_orm_save_all
            .type kof_orm_save_all, @function
            kof_orm_save_all:
                addi sp, sp, -96
                sd   ra, 88(sp)
                sd   a0, 0(sp)
                sd   a1, 8(sp)
                sd   a2, 16(sp)
                sd   a3, 24(sp)
                sd   zero, 32(sp)
                ld   a0, 8(sp)
                call kof_list_size
                sd   a0, 40(sp)
            .L56_loop:
                ld   t0, 32(sp)
                ld   t1, 40(sp)
                bge  t0, t1, .L56_done
                ld   a0, 8(sp)
                mv   a1, t0
                call kof_list_get
                mv   a1, a0
                ld   a0, 0(sp)
                ld   a2, 16(sp)
                ld   a3, 24(sp)
                call kof_orm_save
                ld   t0, 32(sp)
                addi t0, t0, 1
                sd   t0, 32(sp)
                j    .L56_loop
            .L56_done:
                li   a0, 1
                ld   ra, 88(sp)
                addi sp, sp, 96
                ret
            """;
}
