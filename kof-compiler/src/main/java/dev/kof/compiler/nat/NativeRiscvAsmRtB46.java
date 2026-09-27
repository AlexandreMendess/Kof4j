package dev.kof.compiler.nat;

// DB001 fatia 1 (15/09, dono = 192.168.100.18): auxiliares do consumidor DB
// cross — kof_io_strlen (NUL-terminado, para os char* devolvidos pela
// libsqlite3), kof_io_make_string (KofString de ptr+len cru) e o builder JSON
// (kof_json_builder_new/grow/char/str/result) que monta o objeto {"col":val}
// de cada linha do db.query. Ports 1:1 de RuntimeIo1.java (x86) e
// RuntimeJsonBuilder.java. Builder layout (32B, typeId=101): typeId@0
// super@4 vtable@8 len@16 cap@20 buffer@24. KofStr: typeId@0 super@4
// vtable@8 len@16 cap@20 data@24.
public final class NativeRiscvAsmRtB46 {

    private NativeRiscvAsmRtB46() {}

    static String RISCV_RUNTIME_ASM_B_46 = """
            # kof_io_strlen(str@a0) -> byte count (sem o NUL). Port RuntimeIo1:53.
            .globl kof_io_strlen
            kof_io_strlen:
                mv   t0, a0
                li   a0, 0
            .Lio_strlen_loop:
                add  t1, t0, a0
                lbu  t2, 0(t1)
                beqz t2, .Lio_strlen_done
                addi a0, a0, 1
                j    .Lio_strlen_loop
            .Lio_strlen_done:
                ret

            # kof_io_make_string(data@a0, len@a1) -> KofStr*. Port RuntimeIo1:24.
            .globl kof_io_make_string
            kof_io_make_string:
                addi sp, sp, -32
                sd   ra, 24(sp)
                sd   s0, 16(sp)
                sd   s1, 8(sp)
                sd   s2, 0(sp)
                mv   s0, a0
                mv   s1, a1
                addi a0, s1, 25
                call kof_alloc
                mv   s2, a0
                li   t0, 1
                sw   t0, 0(s2)
                sw   zero, 4(s2)
                sd   zero, 8(s2)
                sw   s1, 16(s2)
                sw   zero, 20(s2)
                addi a0, s2, 24
                mv   a1, s0
                mv   a2, s1
                call kof_memcpy
                addi t0, s2, 24
                add  t0, t0, s1
                sb   zero, 0(t0)
                mv   a0, s2
                ld   s2, 0(sp)
                ld   s1, 8(sp)
                ld   s0, 16(sp)
                ld   ra, 24(sp)
                addi sp, sp, 32
                ret

            # kof_json_builder_new() -> builder* (typeId=101, len=0, cap=64).
            .globl kof_json_builder_new
            kof_json_builder_new:
                addi sp, sp, -16
                sd   ra, 8(sp)
                sd   s0, 0(sp)
                li   a0, 32
                call kof_alloc
                mv   s0, a0
                li   t0, 101
                sw   t0, 0(s0)
                sw   zero, 4(s0)
                sd   zero, 8(s0)
                sw   zero, 16(s0)
                li   t0, 64
                sw   t0, 20(s0)
                li   a0, 64
                call kof_alloc
                sd   a0, 24(s0)
                mv   a0, s0
                ld   s0, 0(sp)
                ld   ra, 8(sp)
                addi sp, sp, 16
                ret

            # kof_json_builder_grow(builder@a0): cap*=2, realloc, copia len.
            .globl kof_json_builder_grow
            kof_json_builder_grow:
                addi sp, sp, -32
                sd   ra, 24(sp)
                sd   s0, 16(sp)
                sd   s1, 8(sp)
                sd   s2, 0(sp)
                mv   s0, a0
                lw   t0, 20(s0)
                slli t0, t0, 1
                sw   t0, 20(s0)
                mv   a0, t0
                call kof_alloc
                mv   s1, a0
                lw   s2, 16(s0)
                mv   a0, s1
                ld   a1, 24(s0)
                mv   a2, s2
                call kof_memcpy
                sd   s1, 24(s0)
                mv   a0, s0
                ld   s2, 0(sp)
                ld   s1, 8(sp)
                ld   s0, 16(sp)
                ld   ra, 24(sp)
                addi sp, sp, 32
                ret

            # kof_json_builder_char(builder@a0, char@a1)
            .globl kof_json_builder_char
            kof_json_builder_char:
                addi sp, sp, -16
                sd   ra, 8(sp)
                sd   s0, 0(sp)
                mv   s0, a0
                lw   t0, 16(s0)
                lw   t1, 20(s0)
                bgeu t1, t0, .Ljson_bch_ok
                mv   a0, s0
                call kof_json_builder_grow
            .Ljson_bch_ok:
                lw   t0, 16(s0)
                ld   t1, 24(s0)
                add  t1, t1, t0
                sb   a1, 0(t1)
                addi t0, t0, 1
                sw   t0, 16(s0)
                ld   s0, 0(sp)
                ld   ra, 8(sp)
                addi sp, sp, 16
                ret

            # kof_json_builder_str(builder@a0, str@a1) — anexa KofStr*.
            .globl kof_json_builder_str
            kof_json_builder_str:
                addi sp, sp, -32
                sd   ra, 24(sp)
                sd   s0, 16(sp)
                sd   s1, 8(sp)
                sd   s2, 0(sp)
                mv   s0, a0
                mv   s1, a1
                lw   s2, 16(s1)
            .Ljson_bst_grow:
                lw   t0, 16(s0)
                add  t0, t0, s2
                lw   t1, 20(s0)
                bgeu t1, t0, .Ljson_bst_ok
                mv   a0, s0
                call kof_json_builder_grow
                j    .Ljson_bst_grow
            .Ljson_bst_ok:
                lw   t0, 16(s0)
                ld   t1, 24(s0)
                add  a0, t1, t0
                addi a1, s1, 24
                mv   a2, s2
                call kof_memcpy
                lw   t0, 16(s0)
                add  t0, t0, s2
                sw   t0, 16(s0)
                ld   s2, 0(sp)
                ld   s1, 8(sp)
                ld   s0, 16(sp)
                ld   ra, 24(sp)
                addi sp, sp, 32
                ret

            # kof_json_builder_result(builder@a0) -> KofStr* congelado.
            .globl kof_json_builder_result
            kof_json_builder_result:
                addi sp, sp, -32
                sd   ra, 24(sp)
                sd   s0, 16(sp)
                sd   s1, 8(sp)
                sd   s2, 0(sp)
                mv   s0, a0
                lw   s2, 16(s0)
                addi a0, s2, 25
                call kof_alloc
                mv   s1, a0
                li   t0, 1
                sw   t0, 0(s1)
                sw   zero, 4(s1)
                sd   zero, 8(s1)
                sw   s2, 16(s1)
                sw   zero, 20(s1)
                addi a0, s1, 24
                ld   a1, 24(s0)
                mv   a2, s2
                call kof_memcpy
                addi t0, s1, 24
                add  t0, t0, s2
                sb   zero, 0(t0)
                mv   a0, s1
                ld   s2, 0(sp)
                ld   s1, 8(sp)
                ld   s0, 16(sp)
                ld   ra, 24(sp)
                addi sp, sp, 32
                ret

            # §284-map (18/09): port do kof_json_encode_map x86 (RuntimeJsonEncode
            # §106) — a face nunca existiu no cross (call nao-resolvida = link
            # quebrado; hoje so era mascarada porque os E2e json-map eram x86).
            # Semaforo de valor: 1=string, 2=bool cru, 7=CAIXA numerica MAGIC
            # (desembale via kof_box_to_string; miss null → "null" cru = oraculo
            # JVM medido), senao int cru. Frame 128: ra@120 s8@112 i@96 min@88
            # tmp@80 j@72 tag@48 str-spill@40 chave@32 nullstr@0 (48B).
            # (sem comentario inline nas instrucoes: o tradutor aarch64 so
            #  trata "#" como inicio de linha — linhas `X  # ...` viram lixo.)
            .globl kof_json_encode_map
            kof_json_encode_map:
                addi sp, sp, -128
                sd   ra, 120(sp)
                sd   s8, 112(sp)
                sw   zero, 96(sp)
                sw   zero, 88(sp)
                sd   zero, 80(sp)
                sd   zero, 72(sp)
                mv   s0, a0
                sw   a1, 48(sp)
                la   t0, .Lkjr_nullstr
                sd   t0, 0(sp)
                call kof_map_keys
                mv   s2, a0
                lw   s3, 16(s2)
            .Lkjr_si:
                lw   t1, 96(sp)
                sub  t2, t1, s3
                bgez t2, .Lkjr_sdone
                sw   t1, 88(sp)
                addi t0, t1, 1
                sw   t0, 72(sp)
            .Lkjr_sj:
                lw   t0, 72(sp)
                sub  t2, t0, s3
                bgez t2, .Lkjr_swap
                mv   a0, s2
                mv   a1, t0
                call kof_list_get
                lw   t2, 88(sp)
                mv   s1, a0
                mv   a0, s2
                mv   a1, t2
                call kof_list_get
                mv   a1, a0
                mv   a0, s1
                call String_compareTo
                bltz a0, .Lkjr_swapmin
                j    .Lkjr_next
            .Lkjr_swapmin:
                lw   t0, 72(sp)
                sw   t0, 88(sp)
            .Lkjr_next:
                lw   t0, 72(sp)
                addi t0, t0, 1
                sw   t0, 72(sp)
                j    .Lkjr_sj
            .Lkjr_swap:
                lw   t0, 96(sp)
                lw   t1, 88(sp)
                beq  t0, t1, .Lkjr_iinc
                mv   a0, s2
                mv   a1, t0
                call kof_list_get
                sd   a0, 80(sp)
                mv   a0, s2
                lw   a1, 88(sp)
                call kof_list_get
                mv   a2, a0
                mv   a0, s2
                lw   a1, 96(sp)
                call kof_list_set
                mv   a0, s2
                lw   a1, 88(sp)
                ld   a2, 80(sp)
                call kof_list_set
            .Lkjr_iinc:
                lw   t0, 96(sp)
                addi t0, t0, 1
                sw   t0, 96(sp)
                j    .Lkjr_si
            .Lkjr_sdone:
                call kof_json_builder_new
                mv   s8, a0
                mv   a0, s8
                li   a1, 123
                call kof_json_builder_char
                sw   zero, 96(sp)
            .Lkjr_loop:
                lw   t0, 96(sp)
                sub  t2, t0, s3
                bgez t2, .Lkjr_done
                beqz t0, .Lkjr_nocomma
                mv   a0, s8
                li   a1, 44
                call kof_json_builder_char
            .Lkjr_nocomma:
                mv   a0, s2
                lw   a1, 96(sp)
                call kof_list_get
                mv   s1, a0
                mv   a0, s1
                call kof_json_encode_string
                sd   a0, 56(sp)
                mv   a0, s8
                ld   a1, 56(sp)
                call kof_json_builder_str
                mv   a0, s8
                li   a1, 58
                call kof_json_builder_char
                mv   a0, s0
                mv   a1, s1
                call kof_map_get
                lw   t2, 48(sp)
                li   t0, 1
                beq  t2, t0, .Lkjr_vstr
                li   t0, 2
                beq  t2, t0, .Lkjr_vbool
                li   t0, 3
                beq  t2, t0, .Lkjr_vdouble
                li   t0, 5
                beq  t2, t0, .Lkjr_vlong
                li   t0, 7
                beq  t2, t0, .Lkjr_vbox
                call kof_json_encode_int
                j    .Lkjr_vapp
            .Lkjr_vdouble:
                call kof_json_encode_double
                j    .Lkjr_vapp
            .Lkjr_vlong:
                call kof_json_encode_long
                j    .Lkjr_vapp
            .Lkjr_vbox:
                beqz a0, .Lkjr_vnull
                ld   t0, 8(a0)
                li   t1, 2
                beq  t0, t1, .Lkjr_vblong
                call kof_box_to_string
                j    .Lkjr_vapp
            .Lkjr_vblong:
                ld   a0, 16(a0)
                call kof_long_to_string
                j    .Lkjr_vapp
            .Lkjr_vnull:
                addi a0, sp, 0
                j    .Lkjr_vapp
            .Lkjr_vstr:
                call kof_json_encode_string
                j    .Lkjr_vapp
            .Lkjr_vbool:
                call kof_json_encode_bool
            .Lkjr_vapp:
                sd   a0, 56(sp)
                mv   a0, s8
                ld   a1, 56(sp)
                call kof_json_builder_str
                lw   t0, 96(sp)
                addi t0, t0, 1
                sw   t0, 96(sp)
                j    .Lkjr_loop
            .Lkjr_done:
                mv   a0, s8
                li   a1, 125
                call kof_json_builder_char
                mv   a0, s8
                call kof_json_builder_result
                ld   s8, 112(sp)
                ld   ra, 120(sp)
                addi sp, sp, 128
                ret
            .section .rodata
            .Lkjr_nullstr:
                .word 1
                .word 0
                .word 0
                .word 0
                .word 4
                .word 0
                .ascii "null"
            .text
            """;
}