package dev.kof.compiler.nat;

// CONC001 (15/09): helpers de concorrência de alta ordem no runtime cross —
// kof_done/kof_poll (leitura não-bloqueante do handle), kof_cancel/kof_cancelled
// (cancel cooperativo por TID real), kof_select_any (polling anyOf) e
// kof_await_timeout (polling 1ms + throw no timeout). Port de
// RuntimeConcurrency.java (x86) sobre o clone+futex do NativeRiscvSpawn.
// Divergências documentadas vs x86: (1) gettid(178) no lugar de pthread_self;
// (2) o TID do filho é gravado pelo PRÓPRIO kernel via clone ctid
// (&handle->tid) — pthread_create grava no pai; (3) handle 32→64B
// (tid@32 cancelEntry@40 exc@48 pending@56); (4) §129 port 19/09: a chain EH
// é POR-TID (tabela kof_exc_slots em B4) e o trampoline instala o catch
// per-worker — um throw sem try publica em handle->exc e
// selectAny/awaitTimeout/await relançam no consumidor (paridade x86/JVM).
public final class NativeRiscvAsmRtB48 {

    private NativeRiscvAsmRtB48() {}

    static String RISCV_RUNTIME_ASM_B_48 = """
            # ---- CONC001: cancel por TID real + helpers (port RuntimeConcurrency) ----
            # tabela 256 entries de 16B [tid(8), flag(8)], chave = gettid(178),
            # probe linear a partir do hash phi; tid=0 = vazio.
            .section .data
            .align 3
            kof_cancel_slots: .space 4096
            .section .text

            # §129 (port riscv/aarch 19/09): kof_exc_slot() (helper = &chain da
            # thread atual, chave gettid) vive em B4, ao lado da tabela
            # kof_exc_slots — mantê-lo lá evita puxar esta peça (CONC001) ao
            # link de um programa que só dá throw. Ver comentário em B4.

            # kof_cancel_slot_insert(tid@a0) -> entry@a0 (flag=0) ou 0
            .globl kof_cancel_slot_insert
            kof_cancel_slot_insert:
                addi sp, sp, -32
                sd   ra, 24(sp)
                sd   s0, 16(sp)
                sd   s1, 8(sp)
                sd   s2, 0(sp)
                mv   s0, a0                 # tid
                li   t0, 0x9E3779B97F4A7C15
                mul  s1, s0, t0
                srli s1, s1, 56             # slot base
                li   s2, 0                  # i = 0
            .Lkcsi_loop:
                li   t0, 256
                bge  s2, t0, .Lkcsi_full
                add  t0, s2, s1
                andi t0, t0, 255
                slli t0, t0, 4
                la   t1, kof_cancel_slots
                add  t1, t1, t0             # entry
                ld   t2, 0(t1)
                beqz t2, .Lkcsi_claim
                beq  t2, s0, .Lkcsi_reuse
                addi s2, s2, 1
                j    .Lkcsi_loop
            .Lkcsi_claim:
                sd   s0, 0(t1)
            .Lkcsi_reuse:
                sd   zero, 8(t1)            # flag = 0
                mv   a0, t1
                j    .Lkcsi_out
            .Lkcsi_full:
                li   a0, 0
            .Lkcsi_out:
                ld   s2, 0(sp)
                ld   s1, 8(sp)
                ld   s0, 16(sp)
                ld   ra, 24(sp)
                addi sp, sp, 32
                ret

            # kof_cancel_slot_find(tid@a0) -> entry@a0 ou 0
            .globl kof_cancel_slot_find
            kof_cancel_slot_find:
                addi sp, sp, -32
                sd   ra, 24(sp)
                sd   s0, 16(sp)
                sd   s1, 8(sp)
                sd   s2, 0(sp)
                mv   s0, a0
                li   t0, 0x9E3779B97F4A7C15
                mul  s1, s0, t0
                srli s1, s1, 56
                li   s2, 0
            .Lkcsf_loop:
                li   t0, 256
                bge  s2, t0, .Lkcsf_none
                add  t0, s2, s1
                andi t0, t0, 255
                slli t0, t0, 4
                la   t1, kof_cancel_slots
                add  t1, t1, t0
                ld   t2, 0(t1)
                beq  t2, s0, .Lkcsf_hit
                addi s2, s2, 1
                j    .Lkcsf_loop
            .Lkcsf_hit:
                mv   a0, t1
                j    .Lkcsf_out
            .Lkcsf_none:
                li   a0, 0
            .Lkcsf_out:
                ld   s2, 0(sp)
                ld   s1, 8(sp)
                ld   s0, 16(sp)
                ld   ra, 24(sp)
                addi sp, sp, 32
                ret

            # kof_done(handle@a0) -> bool (não-bloqueante)
            .globl kof_done
            kof_done:
                beqz a0, .Lkof_done_zero
                lw   t0, 0(a0)
                li   t1, 2
                bne  t0, t1, .Lkof_done_zero
                fence r, rw                 # §256: acquire — pareia o release
                                            # do trampoline antes de ler done
                lbu  a0, 4(a0)
                ret
            .Lkof_done_zero:
                li   a0, 0
                ret

            # kof_poll(handle@a0) -> valor se pronto, 0 se não (não bloqueia)
            .globl kof_poll
            kof_poll:
                beqz a0, .Lkof_poll_zero
                lw   t0, 0(a0)
                li   t1, 2
                bne  t0, t1, .Lkof_poll_zero
                fence r, rw                 # §256: acquire antes da leitura de
                                            # done/result (par release-publish)
                lbu  t0, 4(a0)
                beqz t0, .Lkof_poll_zero
                ld   a0, 8(a0)
                ret
            .Lkof_poll_zero:
                li   a0, 0
                ret

            # kof_cancel(handle@a0) -> bool (cooperativo: flag da entry do TID)
            # NÃO-LEAF (chama slot_find): PRECISA salvar o ra — no riscv o
            # `call` sobrescreve o registrador ra e o `ret` final usaria o ra
            # do call interno = loop infinito em si mesmo (achado real: o
            # qemu -d exec mostrou 2.2M execuções do bloco de retorno).
            .globl kof_cancel
            kof_cancel:
                addi sp, sp, -16
                sd   ra, 8(sp)
                beqz a0, .Lkof_cancel_no
                lw   t0, 0(a0)
                li   t1, 2
                bne  t0, t1, .Lkof_cancel_no
                sd   a0, 0(sp)              # §286: handle vivo p/ o caminho pending
                ld   a0, 32(a0)             # tid (kernel grava via clone ctid)
                beqz a0, .Lkof_cancel_no    # nunca disparou
                call kof_cancel_slot_find
                bnez a0, .Lkof_cancel_hit
                # §286: handle criado mas a trampoline ainda não registrou a
                # entry (janela de agendamento sob carga → o cancel se perdia).
                # pending=1 + re-check — Dekker com o `fence rw,rw` da trampoline.
                ld   t1, 0(sp)
                li   t0, 1
                sd   t0, 56(t1)             # pending = 1
                fence rw, rw
                ld   a0, 32(t1)
                call kof_cancel_slot_find
                beqz a0, .Lkof_cancel_yes   # vale via pending no start
            .Lkof_cancel_hit:
                li   t0, 1
                sd   t0, 8(a0)              # flag = 1
            .Lkof_cancel_yes:
                mv   a0, t0
                j    .Lkof_cancel_out
            .Lkof_cancel_no:
                li   a0, 0
            .Lkof_cancel_out:
                ld   ra, 8(sp)
                addi sp, sp, 16
                ret

            # kof_cancelled() -> flag do TID ATUAL (0 fora de worker)
            .globl kof_cancelled
            kof_cancelled:
                addi sp, sp, -16
                sd   ra, 8(sp)
                call kof_plat_thread_id     # gettid
                call kof_cancel_slot_find
                beqz a0, .Lkof_cancelled_no
                ld   a0, 8(a0)
                ld   ra, 8(sp)
                addi sp, sp, 16
                ret
            .Lkof_cancelled_no:
                li   a0, 0
                ld   ra, 8(sp)
                addi sp, sp, 16
                ret

            # kof_select_any(list@a0) -> valor do primeiro handle pronto
            # (polling 1ms; paridade JVM anyOf)
            .globl kof_select_any
            kof_select_any:
                addi sp, sp, -48
                sd   ra, 40(sp)
                sd   s0, 32(sp)             # list
                sd   s1, 24(sp)             # size
                sd   s2, 16(sp)             # index
                sd   s3, 8(sp)              # handle
                mv   s0, a0
                beqz s0, .Lksa_no
                call kof_list_size
                sext.w s1, a0
                beqz s1, .Lksa_no
                li   s2, 0
            .Lksa_scan:
                bge  s2, s1, .Lksa_wait
                mv   a0, s0
                mv   a1, s2
                call kof_list_get
                beqz a0, .Lksa_next
                lw   t0, 0(a0)
                li   t1, 2
                bne  t0, t1, .Lksa_next
                lbu  t0, 4(a0)
                beqz t0, .Lksa_next
                fence r, rw                 # §256: acquire — o mfence x86 tem
                                            # par cross; sem isto TCF pode
                                            # atrasar a visao de done/result
                mv   s3, a0
                ld   a0, 48(s3)             # §129: exc (hoje sempre 0 no cross)
                beqz a0, .Lksa_val
                call kof_throw_string
            .Lksa_val:
                ld   a0, 8(s3)
                j    .Lksa_out
            .Lksa_next:
                addi s2, s2, 1
                j    .Lksa_scan
            .Lksa_wait:
                li   a0, 1
                call kof_time_sleep        # 1ms (preserva s-regs)
                li   s2, 0                  # re-scan
                j    .Lksa_scan
            .Lksa_no:
                li   a0, 0
            .Lksa_out:
                ld   s3, 8(sp)
                ld   s2, 16(sp)
                ld   s1, 24(sp)
                ld   s0, 32(sp)
                ld   ra, 40(sp)
                addi sp, sp, 48
                ret

            # kof_await_timeout(handle@a0, ms@a1) -> valor ou throw (polling 1ms)
            .globl kof_await_timeout
            kof_await_timeout:
                addi sp, sp, -32
                sd   ra, 24(sp)
                sd   s0, 16(sp)             # handle
                sd   s1, 8(sp)              # ms restantes
                beqz a0, .Lkat_zero
                lw   t0, 0(a0)
                li   t1, 2
                bne  t0, t1, .Lkat_zero
                mv   s0, a0
                mv   s1, a1
            .Lkat_poll:
                lbu  t0, 4(s0)
                bnez t0, .Lkat_result
                blez s1, .Lkat_timeout
                li   a0, 1
                call kof_time_sleep
                addi s1, s1, -1
                j    .Lkat_poll
            .Lkat_result:
                ld   a0, 48(s0)             # exc (hoje sempre 0 no cross)
                beqz a0, .Lkat_val
                call kof_throw_string
            .Lkat_val:
                ld   a0, 8(s0)
                j    .Lkat_out
            .Lkat_timeout:
                la   a0, .Lstr_await_timeout
                call kof_throw_string
            .Lkat_zero:
                li   a0, 0
            .Lkat_out:
                ld   s1, 8(sp)
                ld   s0, 16(sp)
                ld   ra, 24(sp)
                addi sp, sp, 32
                ret

            .section .data
            .Lstr_await_timeout: .asciz "awaitTimeout: timed out"
            .section .text
            """;
}
