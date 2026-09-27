package dev.kof.compiler.nat;

/**
 * Fatia B39 — §107: toString de coleção (List/Set/Map) para riscv64.
 * Port 1:1 do {@code RuntimeCollectionToString} x86_64: mesmos helpers,
 * mesma ABI (a0=container, a1=PONTEIRO do nó descritor do elem —
 * NativePrintDescriptors; Map: a1=nó chave, a2=nó valor) e MESMA gramática
 * de nó (0=int/char/short/byte, 1=String, 2=Long, 3=Bool, 4=Double,
 * 5=Float, 7=caixa de valor de Map, 8=objeto com toString na vtable,
 * 9=List/Set aninhada, 10=Map aninhado, 6/desconhecido → "?"; null de
 * referência → "null", oracle JVM). Double/Float (4/5) chamam
 * kof_double_to_string/kof_float_to_string (slice B45, FLT001).
 *
 * <p>Disciplina de frame: os helpers do runtime riscv salvam SUBCONJUNTOS
 * INCONSISTENTES dos callee-saved ({@code kof_string_from_literal} preserva
 * s0,s1,s3; {@code kof_int_to_string} preserva s0,s1,s3,s4,s5; cada um
 * CLOBBER o resto), e todos usam t0..t6 livremente. Nenhum registrador é
 * confiável ATRAVÉS de um `call` — todo estado do laço (container, tag,
 * size, acc, sep, i, keyStr) vive em SLOTS DO PRÓPRIO FRAME e é recarregado
 * a cada bloco. Não há GC no riscv (bump-pointer), então o slot não precisa
 * ser "raiz" — só sobreviver ao clobber. aarch64 herda via tradutor (li/mv/
 * sd/ld/lw/sw/beqz/bnez/blt/bge/beq/bne/j/call/ret/la/slli/neg/rem/todos
 * cobertos; diretivas .passam verbatim).
 */
final class NativeRiscvAsmRtB39 {

    private NativeRiscvAsmRtB39() {}

    static  String RISCV_RUNTIME_ASM_B_39 = """
            .section .rodata
            .align 3
            .Lc2s_lbr:   .ascii "["
            .Lc2s_rbr:   .ascii "]"
            .Lc2s_lcur:  .ascii "{"
            .Lc2s_rcur:  .ascii "}"
            .Lc2s_comma: .ascii ", "
            .Lc2s_eq:    .ascii "="
            .Lc2s_q:     .ascii "?"
            .Lc2s_null:  .ascii "null"

            .section .text

            # kof_elem_to_string(a0=&slot, a1=&nó descritor) -> a0 String*
            # Nó (NativePrintDescriptors, .rodata): byte 0 = tipo;
            # 0=int/char/short/byte (word), 1=String (ponteiro), 2=Long
            # (doubleword), 3=Bool, 4=Double (doubleword), 5=Float (word),
            # 7=caixa de valor de Map; 8=objeto com toString na vtable
            # (off u16 no nó[1..2]); 9=List/Set aninhada (filho = nó+1);
            # 10=Map aninhado (nó = [10, valOffLo, valOffHi, chave..., valor...]);
            # QUALQUER outra → "?" — recusa honesta, nunca lixo. Referência
            # null (1/8/9/10) → "null" (oracle JVM).
            # Guarda `ra`: os ramos chamam (call/jalr esmaga ra — sem
            # save/restore o ret voltaria p/ lixo; só tag 1, que é puro ld,
            # não chama).
            # FLT001 (15/09): tags 4/5 agora são suportadas — kof_double_to_string
            # /kof_float_to_string (slice B45, libc snprintf/strtod + link
            # dinâmico sob demanda). Espelha o RuntimeCollectionToString x86.
            .globl kof_elem_to_string
            kof_elem_to_string:
                addi sp, sp, -16
                sd   ra, 8(sp)
                lbu  t0, 0(a1)
                li   t1, 8
                beq  t0, t1, .Lce_rec
                li   t1, 9
                beq  t0, t1, .Lce_list
                li   t1, 10
                beq  t0, t1, .Lce_map
                li   t1, 11
                beq  t0, t1, .Lce_arr
                li   t1, 1
                beq  t0, t1, .Lce_str
                li   t1, 2
                beq  t0, t1, .Lce_long
                li   t1, 3
                beq  t0, t1, .Lce_bool
                li   t1, 4
                beq  t0, t1, .Lce_double
                li   t1, 5
                beq  t0, t1, .Lce_float
                li   t1, 7
                beq  t0, t1, .Lce_boxed
                beqz t0, .Lce_int
                j    .Lce_q
            .Lce_int:
                lw   a0, 0(a0)
                call kof_int_to_string
                j    .Lce_ret
            .Lce_str:
                ld   a0, 0(a0)
                j    .Lce_ret
            .Lce_long:
                ld   a0, 0(a0)
                call kof_int_to_string
                j    .Lce_ret
            .Lce_bool:
                lw   a0, 0(a0)
                call kof_bool_to_string
                j    .Lce_ret
            .Lce_double:
                ld   a0, 0(a0)
                call kof_double_to_string
                j    .Lce_ret
            .Lce_float:
                lw   a0, 0(a0)
                call kof_float_to_string
                j    .Lce_ret
            .Lce_boxed:                     # 7 (§284-map): caixa de slot de Map
                ld   a0, 0(a0)
                call kof_box_to_string      # nao-box passa cru — espelha x86
                j    .Lce_ret
            # 8 = obj.toString() via vtable (MESMA forma do ramo genérico do
            # dispatch; off = índice×8, u16 no nó[1..2]).
            .Lce_rec:
                ld   a0, 0(a0)
                beqz a0, .Lce_null
                ld   t0, 8(a0)               # vtable
                lhu  t1, 1(a1)               # off (u16)
                add  t0, t0, t1
                ld   t0, 0(t0)               # entrada = *(base + off)
                jalr t0
                j    .Lce_ret
            # 9 = List/Set aninhada: slot carrega o container; o filho do nó
            # (a1+1) descreve os elementos dele.
            .Lce_list:
                ld   a0, 0(a0)
                beqz a0, .Lce_null
                addi a1, a1, 1
                call kof_list_to_string
                j    .Lce_ret
            # 10 = Map aninhada: chave = nó+3, valor = nó+valOff (3+len(chave)).
            .Lce_map:
                ld   a0, 0(a0)
                beqz a0, .Lce_null
                lhu  t1, 1(a1)               # valOff (u16)
                add  a2, a1, t1              # &nó valor
                addi a1, a1, 3               # &nó chave
                call kof_map_to_string
                j    .Lce_ret
            # §388-B: 11 = array primitivo aninhado; nó=[11,esz,child]; o slot
            # carrega o ponteiro do array interno; child = a1+2 (esz real vem
            # do header do bloco).
            .Lce_arr:
                ld   a0, 0(a0)
                beqz a0, .Lce_null
                addi a1, a1, 2
                call kof_array_to_string
                j    .Lce_ret
            .Lce_null:
                la   a0, .Lc2s_null
                li   a1, 4
                call kof_string_from_literal
                j    .Lce_ret
            .Lce_q:
                la   a0, .Lc2s_q
                li   a1, 1
                call kof_string_from_literal
            .Lce_ret:
                ld   ra, 8(sp)
                addi sp, sp, 16
                ret

            # kof_list_to_string / kof_set_to_string (a0=container,
            # a1=&nó descritor do elem)
            # -> a0 String* "[e1, e2, ...]". Set É um List (kof_set_new =
            # j kof_list_new, forma 100) — os dois partilham o asm.
            # Frame (72B): 0=container 8=desc 16=size 24=acc 32=i 40=sep
            # 48=elemStr 64=ra. Estado do laço NUNCA em registrador (lição
            # do doc: helpers riscv clobberam s-regs inconsistentes).
            .globl kof_list_to_string
            .globl kof_set_to_string
            kof_list_to_string:
            kof_set_to_string:
                addi sp, sp, -72
                sd   ra, 64(sp)
                sd   a0, 0(sp)           # container
                sd   a1, 8(sp)           # nó descritor (.rodata)
                lw   t0, 16(a0)
                sd   t0, 16(sp)          # size
                sd   zero, 32(sp)        # i = 0
                la   a0, .Lc2s_lbr
                li   a1, 1
                call kof_string_from_literal
                sd   a0, 24(sp)          # acc = "["
                ld   t0, 16(sp)
                beqz t0, .Lcl_fin
            .Lcl_loop:
                ld   t1, 32(sp)          # i
                beqz t1, .Lcl_sep0
                la   a0, .Lc2s_comma
                li   a1, 2
                call kof_string_from_literal
                sd   a0, 40(sp)          # sep = ", "
                j    .Lcl_haveSep
            .Lcl_sep0:
                la   a0, .Lc2s_lbr       # ponteiro qualquer + LEN 0 = ""
                li   a1, 0
                call kof_string_from_literal
                sd   a0, 40(sp)          # sep = ""
            .Lcl_haveSep:
                ld   a0, 0(sp)           # container
                ld   t0, 32(sp)          # i
                ld   t2, 24(a0)          # data
                slli t0, t0, 3
                add  a0, t2, t0          # &data[i]
                ld   a1, 8(sp)           # nó descritor
                call kof_elem_to_string
                sd   a0, 48(sp)          # elemStr
                ld   a0, 40(sp)          # sep
                ld   a1, 48(sp)          # elemStr
                call kof_string_concat   # sep + elem
                sd   a0, 48(sp)
                ld   a0, 24(sp)          # acc
                ld   a1, 48(sp)
                call kof_string_concat   # acc + (sep+elem)
                sd   a0, 24(sp)          # acc
                ld   t0, 32(sp)
                addi t0, t0, 1
                sd   t0, 32(sp)
                ld   t0, 32(sp)
                ld   t1, 16(sp)
                blt  t0, t1, .Lcl_loop
            .Lcl_fin:
                la   a0, .Lc2s_rbr
                li   a1, 1
                call kof_string_from_literal
                sd   a0, 48(sp)          # "]"
                ld   a0, 24(sp)          # acc
                ld   a1, 48(sp)
                call kof_string_concat   # acc + "]"
                ld   ra, 64(sp)
                addi sp, sp, 72
                ret

            # kof_map_to_string (a0=map, a1=&nó chave, a2=&nó valor)
            # -> a0 String* "{k=v, ...}". Ordem de ARMAZENAMENTO (inserção)
            # — vetor linear vs buckets de hash do JVM (divergência de
            # arquitetura registrada §107; single-entry idêntico).
            # Frame (88B): 0=container 8=descChave 16=descValor 24=size
            # 32=acc 40=i 48=sep 56=keyStr 64="=" 80=ra.
            .globl kof_map_to_string
            kof_map_to_string:
                addi sp, sp, -88
                sd   ra, 80(sp)
                sd   a0, 0(sp)           # container
                sd   a1, 8(sp)           # nó chave (.rodata)
                sd   a2, 16(sp)          # nó valor (.rodata)
                lw   t0, 16(a0)
                sd   t0, 24(sp)          # size
                sd   zero, 40(sp)        # i = 0
                la   a0, .Lc2s_lcur
                li   a1, 1
                call kof_string_from_literal
                sd   a0, 32(sp)          # acc = "{"
                ld   t0, 24(sp)
                beqz t0, .Lcm_fin
            .Lcm_loop:
                ld   t1, 40(sp)          # i
                beqz t1, .Lcm_sep0
                la   a0, .Lc2s_comma
                li   a1, 2
                call kof_string_from_literal
                sd   a0, 48(sp)          # sep = ", "
                j    .Lcm_haveSep
            .Lcm_sep0:
                la   a0, .Lc2s_lcur
                li   a1, 0
                call kof_string_from_literal
                sd   a0, 48(sp)          # sep = ""
            .Lcm_haveSep:
                ld   t2, 0(sp)           # container
                ld   t0, 40(sp)          # i
                slli t0, t0, 3
                ld   t3, 24(t2)          # keys ptr
                add  a0, t3, t0          # &keys[i]
                ld   a1, 8(sp)           # nó chave
                call kof_elem_to_string
                sd   a0, 56(sp)          # keyStr
                la   a0, .Lc2s_eq
                li   a1, 1
                call kof_string_from_literal
                sd   a0, 64(sp)          # "="
                ld   a0, 56(sp)          # keyStr
                ld   a1, 64(sp)
                call kof_string_concat   # keyStr + "="
                sd   a0, 56(sp)          # "chave="
                ld   t2, 0(sp)
                ld   t0, 40(sp)
                slli t0, t0, 3
                ld   t3, 32(t2)          # vals ptr
                add  a0, t3, t0          # &vals[i]
                ld   a1, 16(sp)          # nó valor
                call kof_elem_to_string
                sd   a0, 64(sp)          # valStr
                ld   a0, 56(sp)          # "chave="
                ld   a1, 64(sp)
                call kof_string_concat   # "chave=valor"
                sd   a0, 56(sp)
                ld   a0, 48(sp)          # sep
                ld   a1, 56(sp)
                call kof_string_concat   # sep + "chave=valor"
                sd   a0, 56(sp)
                ld   a0, 32(sp)          # acc
                ld   a1, 56(sp)
                call kof_string_concat   # acc + ...
                sd   a0, 32(sp)          # acc
                ld   t0, 40(sp)
                addi t0, t0, 1
                sd   t0, 40(sp)
                ld   t0, 40(sp)
                ld   t1, 24(sp)
                blt  t0, t1, .Lcm_loop
            .Lcm_fin:
                la   a0, .Lc2s_rcur
                li   a1, 1
                call kof_string_from_literal
                sd   a0, 56(sp)
                ld   a0, 32(sp)
                ld   a1, 56(sp)
                call kof_string_concat
                ld   ra, 80(sp)
                addi sp, sp, 88
                ret

            # §388-B: kof_array_to_string(a0 = array cru, a1 = descritor do
            # componente). Bloco [tag@0][len@16][esz@20][data@24] — MESMA fonte
            # elementTypeSize do alloc. Espelha o kof_list_to_string acima no
            # formato ([a, b]) e no mecanismo; a diferença é o carregamento:
            # packed por esz (1/2/4/8) num slot de 8 bytes na pilha (o
            # kof_elem_to_string lê pelo TAG, não pela passada do bloco — sem
            # staging um bool[2] leria 4 bytes vizinhos). Aninhado = tag 11
            # recursa aqui. Aarch64 herda via tradutor (regra 5).
            .globl kof_array_to_string
            kof_array_to_string:
                addi sp, sp, -104
                sd   ra, 96(sp)
                sd   a0, 0(sp)            # array (raiz)
                sd   a1, 8(sp)            # descritor do componente
                lw   t0, 16(a0)
                sd   t0, 16(sp)           # len
                lw   t0, 20(a0)
                sd   t0, 72(sp)           # esz
                addi t0, a0, 24
                sd   t0, 64(sp)           # data
                sd   zero, 32(sp)         # i = 0
                la   a0, .Lc2s_lbr
                li   a1, 1
                call kof_string_from_literal
                sd   a0, 24(sp)           # acc = '['
                ld   t0, 16(sp)
                beqz t0, .Lca_fin
            .Lca_loop:
                ld   t1, 32(sp)
                beqz t1, .Lca_sep0
                la   a0, .Lc2s_comma
                li   a1, 2
                call kof_string_from_literal
                sd   a0, 40(sp)
                j    .Lca_haveSep
            .Lca_sep0:
                la   a0, .Lc2s_lbr
                li   a1, 0
                call kof_string_from_literal
                sd   a0, 40(sp)
            .Lca_haveSep:
                ld   t0, 32(sp)           # i
                ld   t1, 72(sp)           # esz
                mul  t0, t0, t1
                ld   t2, 64(sp)
                add  t0, t2, t0           # &elem[i]
                sd   zero, 88(sp)         # slot de 8B
                li   t3, 4
                beq  t1, t3, .Lca_ld4
                li   t3, 1
                beq  t1, t3, .Lca_ld1
                li   t3, 2
                beq  t1, t3, .Lca_ld2
                ld   t4, 0(t0)            # esz 8 (long/double/String/array)
                sd   t4, 88(sp)
                j    .Lca_sep
            .Lca_ld4:
                lw   t4, 0(t0)
                sd   t4, 88(sp)
                j    .Lca_sep
            .Lca_ld2:
                lhu  t4, 0(t0)
                sd   t4, 88(sp)
                j    .Lca_sep
            .Lca_ld1:
                lbu  t4, 0(t0)
                sd   t4, 88(sp)
            .Lca_sep:
                addi a0, sp, 88
                ld   a1, 8(sp)
                call kof_elem_to_string
                sd   a0, 48(sp)           # elemStr
                ld   a0, 40(sp)
                ld   a1, 48(sp)
                call kof_string_concat    # sep + elem
                sd   a0, 48(sp)
                ld   a0, 24(sp)
                ld   a1, 48(sp)
                call kof_string_concat    # acc + (sep + elem)
                sd   a0, 24(sp)
                ld   t0, 32(sp)
                addi t0, t0, 1
                sd   t0, 32(sp)
                ld   t0, 32(sp)
                ld   t1, 16(sp)
                blt  t0, t1, .Lca_loop
            .Lca_fin:
                la   a0, .Lc2s_rbr
                li   a1, 1
                call kof_string_from_literal
                sd   a0, 56(sp)
                ld   a0, 24(sp)
                ld   a1, 56(sp)
                call kof_string_concat
                ld   ra, 96(sp)
                addi sp, sp, 104
                ret
            """;
}
