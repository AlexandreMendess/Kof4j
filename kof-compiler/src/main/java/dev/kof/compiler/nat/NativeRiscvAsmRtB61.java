package dev.kof.compiler.nat;

// §423 (TIER 13.4, 23/09): canais no runtime cross — kof_channel_new/send/
// receive em asm riscv64 (aarch64 herda via NativeAarch64Translator). Port de
// RuntimeChannel.emitChannel (x86): mesma struct (56B: head@0 tail@8 count@16
// lock@20) e mesmo nó (16B: value@0 next@8), FIFO de lista ligada com spinlock
// atomico + futex (kof_plat_sync) e polling de 1ms quando vazio.
//
// Diferencas medidas vs x86: (1) o lock e amoswap.w (o `lock cmpxchg` x86 nao
// existe no riscv; a tradutor aarch64 mapeia para swpal) + fence rw,rw nas
// bordas (release/acquire); (2) futex PRIVATE (128/129) — a convencao da lane
// cross (NativeRiscvSpawn), sem o FUTEX_WAIT/WAKE compartilhado do x86;
// (3) usleep(1000) vira kof_time_sleep(1ms) (nanosleep, sem libc).
//
// ABI: kof_channel_send(a0=chan, a1=value) -> void; kof_channel_receive(a0=chan)
// -> value@a0. O valor e um qword cru (caixa §284 quando o canal e BARE — a
// caixa e emitida no SEND pelo ChannelWrites, como no x86).
public final class NativeRiscvAsmRtB61 {

    private NativeRiscvAsmRtB61() {}

    static String RISCV_RUNTIME_ASM_B_61 = """
            # ---- §423: canais (port RuntimeChannel) ----
            .globl kof_channel_new
            .type kof_channel_new, @function
            kof_channel_new:
                addi sp, sp, -16
                sd   ra, 8(sp)
                li   a0, 56
                call kof_alloc
                sd   zero, 0(a0)            # head = 0
                sd   zero, 8(a0)            # tail = 0
                sw   zero, 16(a0)           # count = 0
                sw   zero, 20(a0)           # lock = 0
                ld   ra, 8(sp)
                addi sp, sp, 16
                ret

            # kof_channel_send(a0=chan, a1=value)
            .globl kof_channel_send
            .type kof_channel_send, @function
            kof_channel_send:
                addi sp, sp, -48
                sd   ra, 40(sp)
                sd   s0, 32(sp)             # chan
                sd   s1, 24(sp)             # value
                sd   s2, 16(sp)             # no
                sd   s3, 8(sp)              # tail
                mv   s0, a0
                mv   s1, a1
            .Lrchan_send_lock:
                li   t1, 1
                addi t2, s0, 20
                amoswap.w t0, t1, (t2)
                beqz t0, .Lrchan_send_locked
                # futex WAIT no lock (val=1); &lock=a0, op=128 (PRIVATE)
                addi a0, s0, 20
                li   a1, 128
                li   a2, 1
                li   a3, 0
                call kof_plat_sync
                j    .Lrchan_send_lock
            .Lrchan_send_locked:
                fence rw, rw                # acquire (pareia o release)
                li   a0, 16
                call kof_alloc
                mv   s2, a0
                sd   s1, 0(s2)              # no.value = value
                sd   zero, 8(s2)            # no.next = 0
                ld   s3, 8(s0)              # tail
                beqz s3, .Lrchan_send_empty
                sd   s2, 8(s3)              # tail->next = no
                sd   s2, 8(s0)              # tail = no
                j    .Lrchan_send_count
            .Lrchan_send_empty:
                sd   s2, 0(s0)              # head = no
                sd   s2, 8(s0)              # tail = no
            .Lrchan_send_count:
                lw   t0, 16(s0)
                addi t0, t0, 1
                sw   t0, 16(s0)             # count++
                fence rw, rw                # release antes de liberar o lock
                sw   zero, 20(s0)           # unlock
                addi a0, s0, 20
                li   a1, 129                # FUTEX_WAKE_PRIVATE
                li   a2, 1
                li   a3, 0
                call kof_plat_sync
                li   a0, 0
                ld   s3, 8(sp)
                ld   s2, 16(sp)
                ld   s1, 24(sp)
                ld   s0, 32(sp)
                ld   ra, 40(sp)
                addi sp, sp, 48
                ret

            # kof_channel_receive(a0=chan) -> value@a0
            .globl kof_channel_receive
            .type kof_channel_receive, @function
            kof_channel_receive:
                addi sp, sp, -48
                sd   ra, 40(sp)
                sd   s0, 32(sp)             # chan
                sd   s1, 24(sp)             # no
                sd   s2, 16(sp)             # value
                mv   s0, a0
            .Lrchan_recv_lock:
                li   t1, 1
                addi t2, s0, 20
                amoswap.w t0, t1, (t2)
                beqz t0, .Lrchan_recv_locked
                addi a0, s0, 20
                li   a1, 128
                li   a2, 1
                li   a3, 0
                call kof_plat_sync
                j    .Lrchan_recv_lock
            .Lrchan_recv_locked:
                fence rw, rw                # acquire
                lw   t0, 16(s0)
                beqz t0, .Lrchan_recv_empty
                ld   s1, 0(s0)              # no = head
                ld   s2, 0(s1)              # value = no.value
                ld   t1, 8(s1)              # next
                sd   t1, 0(s0)              # head = next
                bnez t1, .Lrchan_recv_notempty
                sd   zero, 8(s0)            # §485: fila esvaziou -> tail = 0
            .Lrchan_recv_notempty:
                lw   t0, 16(s0)
                addi t0, t0, -1
                sw   t0, 16(s0)             # count--
                mv   a0, s1
                call kof_free
                fence rw, rw                # release
                sw   zero, 20(s0)           # unlock
                addi a0, s0, 20
                li   a1, 129
                li   a2, 1
                li   a3, 0
                call kof_plat_sync
                mv   a0, s2                 # resultado
                ld   s2, 16(sp)
                ld   s1, 24(sp)
                ld   s0, 32(sp)
                ld   ra, 40(sp)
                addi sp, sp, 48
                ret
            .Lrchan_recv_empty:
                fence rw, rw
                sw   zero, 20(s0)           # libera o lock antes de dormir
                addi a0, s0, 20
                li   a1, 129
                li   a2, 1
                li   a3, 0
                call kof_plat_sync
                li   a0, 1
                call kof_time_sleep         # 1ms (nasleep; preserva s-regs)
                j    .Lrchan_recv_lock
            """;
}
