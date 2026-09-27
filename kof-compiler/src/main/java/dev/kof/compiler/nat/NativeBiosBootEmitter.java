package dev.kof.compiler.nat;

import dev.kof.compiler.IRClass;

/**
 * B-3 (PLAN-BAREMETAL-BOOT): emissão do entry LEGACY BIOS em DUAS FASES.
 *
 * <p><b>LBA 0 (stub, ≤510 bytes):</b> modo real 16-bit — segmentos, pilha e a
 * carga das fases seguintes do PRÓPRIO boot via EDD {@code int 0x13, ah=0x42}
 * (4 setores fixos para {@code 0x7E00}, contíguo ao setor 0) + far-jump. A
 * lógica completa (carga do payload Kof, long mode, cópia, salto) não cabe em
 * um setor — o stage2 a continua.
 *
 * <p><b>Layout do disco flat (medição B-3b-3):</b> LBA 0 = stub; LBA 1..4 =
 * {@code .boot2} (stage2, carregado em {@code 0x7E00}); LBA {@code HEADER_LBA}
 * (34) = {@code .payload} (header KOFPAYLD + contagem de setores, preenchida
 * pelo patch pós-objcopy do NativeAssembler); LBA {@code PROGRAM_LBA} (1986)..N
 * = o programa Kof (VMA {@code 0x100000}). O staging do payload em modo real
 * fica em {@code 0xC000} — DEPOIS das tabelas de página (0x9000..0xBFFF), que o
 * payload não pode clobberear.
 *
 * <p>Extraído de {@link NativeMethodEmitter} (gate ≤500/≤600 linhas); a
 * responsabilidade aqui é só o assembly do boot.
 */
final class NativeBiosBootEmitter {

    // Layout (linker script do NativeAssembler): o arquivo flat = o espaço VMA
    // a partir de BOOT_BASE (0x7C00) — LBA = (VMA - BOOT_BASE) / 512. Estas
    // constantes são a fonte única: o linker script e o patch do header derivam
    // delas (nunca repita o literal do endereço num lado só).
    static final int BOOT_BASE = 0x7C00;
    static final int HEADER_VMA = 0xC000;      // header KOFPAYLD
    static final int PROGRAM_VMA = 0x100000;   // programa Kof
    static final int HEADER_LBA = (HEADER_VMA - BOOT_BASE) / 512;     // 34
    static final int PROGRAM_LBA = (PROGRAM_VMA - BOOT_BASE) / 512;   // 1986
    /** Offset do programa no arquivo flat (= VMA − BOOT_BASE). */
    static final int PROGRAM_FILE_OFF = PROGRAM_VMA - BOOT_BASE;      // 0xF8400
    /** Setores de stage2 que o stub carrega (fixo, ambos os lados). */
    private static final int BOOT2_SECTORS = 4;
    /** Staging do payload em modo real (header) — após as tabelas de página. */
    private static final int STAGING = 0xC000;
    /** Staging do programa (header + 1 setor). */
    private static final int STAGING_PROG = STAGING + 0x200;

    private NativeBiosBootEmitter() {}

    /** Emite o boot BIOS (stub + stage2 + header); no-op se a classe não tem {@code main}. */
    static void emit(StringBuilder sb, IRClass clazz) {
        boolean hasMain = clazz.methods().stream().anyMatch(m -> "main".equals(m.name()));
        if (!hasMain) return;
        emitStub(sb);
        emitStage2(sb);
        emitPayloadHeader(sb);
    }

    // ------------------------------------------------------------------
    // LBA 0 — stub de 512 bytes: segmentos + carga do stage2 + far-jump.
    // ------------------------------------------------------------------
    private static void emitStub(StringBuilder sb) {
        sb.append("\n.section .text.boot,\"ax\"\n");
        sb.append(".globl _start\n");
        sb.append(".code16\n");
        sb.append("_start:\n");
        sb.append("    cli\n");
        sb.append("    xorw %ax, %ax\n");
        sb.append("    movw %ax, %ds\n");
        sb.append("    movw %ax, %es\n");
        sb.append("    movw %ax, %ss\n");
        sb.append("    movw $0x7C00, %sp\n");
        sb.append("    movb %dl, kof_bios_drive\n");   // drive de boot (BIOS entrega em DL)
        // EDD: lê BOOT2_SECTORS setores (LBA 1..N) -> 0x7E00 (contíguo ao setor 0)
        sb.append("    movb $0x42, %ah\n");
        sb.append("    movb kof_bios_drive, %dl\n");
        sb.append("    movw $kof_bios_stub_dap, %si\n");
        sb.append("    int $0x13\n");
        sb.append("    jc kof_bios_stub_bad\n");       // carry = falha NOMEADA
        sb.append("    .byte 0xEA\n");                 // far jump -> stage2 (0:0x7E00)
        sb.append("    .word 0x7E00\n");
        sb.append("    .word 0\n");
        // falha NOMEADA: teletype + espelho no COM1 (a captura headless do qemu)
        sb.append("kof_bios_stub_bad:\n");
        sb.append("    movw $kof_bios_bad, %si\n");
        sb.append("kof_bios_stub_bad1:\n");
        sb.append("    lodsb\n");
        sb.append("    testb %al, %al\n");
        sb.append("    jz kof_bios_stub_halt\n");
        sb.append("    movb %al, %bl\n");
        sb.append("    movb $0x0E, %ah\n");
        sb.append("    int $0x10\n");
        sb.append("    movw $0x3FD, %dx\n");           // LSR do COM1
        sb.append("kof_bios_stub_txe:\n");
        sb.append("    inb %dx, %al\n");
        sb.append("    testb $0x20, %al\n");           // THR vazio
        sb.append("    jz kof_bios_stub_txe\n");
        sb.append("    movw $0x3F8, %dx\n");
        sb.append("    movb %bl, %al\n");
        sb.append("    outb %al, %dx\n");
        sb.append("    jmp kof_bios_stub_bad1\n");
        sb.append("kof_bios_stub_halt:\n");
        sb.append("    cli\n");
        sb.append("kof_bios_stub_h1:\n");
        sb.append("    hlt\n");
        sb.append("    jmp kof_bios_stub_h1\n");
        sb.append("kof_bios_stub_dap:\n");             // Disk Address Packet (EDD)
        sb.append("    .byte 0x10, 0\n");
        sb.append("    .word ").append(BOOT2_SECTORS).append("\n");
        sb.append("    .word 0x7E00\n");               // offset do buffer
        sb.append("    .word 0\n");                    // segmento
        sb.append("    .quad 1\n");                    // LBA 1
        sb.append("kof_bios_drive:\n");
        sb.append("    .byte 0\n");
        sb.append("kof_bios_bad:\n");
        sb.append("    .asciz \"KO-BIOS LOAD BAD\\r\\n\"\n");
        sb.append("    .org 510, 0\n");                // preenche até a assinatura
        sb.append("    .word 0xAA55\n");
    }

    // ------------------------------------------------------------------
    // LBA 1..4 — stage2 (roda em 0x7E00): carga do payload, long mode,
    // cópia do staging para a base 0x100000 e salto para o programa Kof.
    // ------------------------------------------------------------------
    private static void emitStage2(StringBuilder sb) {
        sb.append(".section .boot2,\"ax\"\n");
        sb.append(".code16\n");
        sb.append("kof_bios_stage2:\n");
        // B-3b-1: lê o header do payload (HEADER_LBA) para o staging (STAGING)
        sb.append("    movb $0x42, %ah\n");
        sb.append("    movb kof_bios_drive, %dl\n");
        sb.append("    movw $kof_bios_dap, %si\n");
        sb.append("    int $0x13\n");
        sb.append("    jc kof_bios_load_bad\n");       // carry = falha NOMEADA
        sb.append("    movl $0x50464F4B, %eax\n");     // magia "KOFP"
        sb.append("    cmpl %eax, ").append(STAGING).append("\n");
        sb.append("    jne kof_bios_load_bad\n");
        sb.append("    movl $0x444C5941, %eax\n");     // magia "AYLD"
        sb.append("    cmpl %eax, ").append(STAGING + 4).append("\n");
        sb.append("    jne kof_bios_load_bad\n");
        sb.append("    movw $kof_bios_ok, %si\n");
        sb.append("    call kof_bios_print\n");
        // B-3b-3: lê o RESTO do payload (PROGRAM_LBA..N) — a contagem vem do
        // header (STAGING+8, int LE, escrito pelo patch pós-objcopy); destino =
        // STAGING_PROG (após a página do header).
        sb.append("    movl $0, kof_bios_i\n");
        sb.append("kof_bios_load_loop:\n");
        sb.append("    movl ").append(STAGING + 8).append(", %eax\n"); // setores do payload
        sb.append("    cmpl %eax, kof_bios_i\n");
        sb.append("    jae kof_bios_load_done\n");
        sb.append("    movl kof_bios_i, %eax\n");
        sb.append("    shll $5, %eax\n");              // i*32 = i*512/16
        sb.append("    addw $").append(STAGING_PROG >> 4).append(", %ax\n"); // segmento
        sb.append("    movw %ax, kof_bios_dap+6\n");
        sb.append("    movw $0, kof_bios_dap+4\n");    // offset 0
        sb.append("    movl kof_bios_i, %eax\n");
        sb.append("    addl $").append(PROGRAM_LBA).append(", %eax\n"); // LBA = PROGRAM_LBA + i
        sb.append("    movl %eax, kof_bios_dap+8\n");
        sb.append("    movb $0x42, %ah\n");
        sb.append("    movb kof_bios_drive, %dl\n");
        sb.append("    movw $kof_bios_dap, %si\n");
        sb.append("    int $0x13\n");
        sb.append("    jc kof_bios_load_bad\n");       // carry = falha NOMEADA
        sb.append("    incl kof_bios_i\n");
        sb.append("    jmp kof_bios_load_loop\n");
        sb.append("kof_bios_load_done:\n");
        // --- B-3b-2: A20 + GDT flat + transição para long mode (x86_64) ---
        sb.append("    movw $0x2401, %ax\n");          // A20 (fast gate)
        sb.append("    int $0x15\n");
        sb.append("    cli\n");
        sb.append("    lgdt kof_bios_gdt_desc\n");
        sb.append("    movl %cr0, %eax\n");
        sb.append("    orl $1, %eax\n");               // CR0.PE
        sb.append("    movl %eax, %cr0\n");
        sb.append("    .byte 0x66, 0xEA\n");           // far jump -> code32 (0x08)
        sb.append("    .long kof_bios_pm32\n");
        sb.append("    .word 0x08\n");
        sb.append("kof_bios_gdt_desc:\n");
        sb.append("    .word 0x1F\n");                 // 4 entradas * 8 - 1
        sb.append("    .long kof_bios_gdt\n");
        sb.append("    .align 8\n");
        sb.append("kof_bios_gdt:\n");
        sb.append("    .quad 0x0000000000000000\n");
        sb.append("    .quad 0x00CF9A000000FFFF\n"); // 0x08 code32
        sb.append("    .quad 0x00CF92000000FFFF\n"); // 0x10 data
        sb.append("    .quad 0x00AF9A000000FFFF\n"); // 0x18 code64 (L=1)
        sb.append(".code32\n");
        sb.append("kof_bios_pm32:\n");
        sb.append("    movw $0x10, %ax\n");
        sb.append("    movw %ax, %ds\n");
        sb.append("    movw %ax, %es\n");
        sb.append("    movw %ax, %ss\n");
        sb.append("    movw %ax, %fs\n");
        sb.append("    movw %ax, %gs\n");
        // tabelas de paginação: identidade do 1º MiB (2 MiB page)
        sb.append("    movl $0xA003, 0x9000\n");       // PML4[0] -> PDPT|P|RW
        sb.append("    movl $0, 0x9004\n");
        sb.append("    movl $0xB003, 0xA000\n");       // PDPT[0] -> PD|P|RW
        sb.append("    movl $0, 0xA004\n");
        sb.append("    movl $0x83, 0xB000\n");         // PD[0] = 0|P|RW|PS (2 MiB)
        sb.append("    movl $0, 0xB004\n");
        // B-3b-3: mapeia PD[1..15] -> 2..32 MiB (o payload Kof vive em
        // 0x100000..; 1 página de 2 MiB não cobre heap+pilha do programa)
        sb.append("    movl $1, %ecx\n");
        sb.append("kof_bios_pd_loop:\n");
        sb.append("    movl %ecx, %eax\n");
        sb.append("    shll $21, %eax\n");             // base física = i * 2 MiB
        sb.append("    orl $0x83, %eax\n");            // P|RW|PS
        sb.append("    movl %eax, %edx\n");
        sb.append("    movl %ecx, %eax\n");
        sb.append("    shll $3, %eax\n");
        sb.append("    addl $0xB000, %eax\n");
        sb.append("    movl %edx, (%eax)\n");
        sb.append("    movl $0, 4(%eax)\n");
        sb.append("    incl %ecx\n");
        sb.append("    cmpl $16, %ecx\n");
        sb.append("    jl kof_bios_pd_loop\n");
        sb.append("    movl $0x9000, %eax\n");
        sb.append("    movl %eax, %cr3\n");
        sb.append("    movl %cr4, %eax\n");
        sb.append("    orl $0x20, %eax\n");            // CR4.PAE
        sb.append("    movl %eax, %cr4\n");
        sb.append("    movl $0xC0000080, %ecx\n");     // EFER
        sb.append("    rdmsr\n");
        sb.append("    orl $0x100, %eax\n");           // EFER.LME
        sb.append("    wrmsr\n");
        sb.append("    movl %cr0, %eax\n");
        sb.append("    orl $0x80000000, %eax\n");      // CR0.PG
        sb.append("    movl %eax, %cr0\n");
        sb.append("    .byte 0xEA\n");                 // far jump -> code64 (0x18)
        sb.append("    .long kof_bios_lm64\n");
        sb.append("    .word 0x18\n");
        // --- prova viva (64-bit) + cópia do staging -> base fixa + salto ---
        sb.append(".code64\n");
        sb.append("kof_bios_lm64:\n");
        sb.append("    movw $0x10, %ax\n");
        sb.append("    movw %ax, %ds\n");
        sb.append("    movw %ax, %es\n");
        sb.append("    movw %ax, %ss\n");
        sb.append("    movl $kof_bios_lm64_msg, %esi\n");
        sb.append("kof_bios_lm64_loop:\n");
        sb.append("    lodsb\n");
        sb.append("    testb %al, %al\n");
        sb.append("    jz kof_bios_lm64_copy\n");
        sb.append("    movb %al, %bl\n");
        sb.append("kof_bios_lm64_txe:\n");
        sb.append("    movw $0x3FD, %dx\n");
        sb.append("    inb %dx, %al\n");
        sb.append("    testb $0x20, %al\n");
        sb.append("    jz kof_bios_lm64_txe\n");
        sb.append("    movw $0x3F8, %dx\n");
        sb.append("    movb %bl, %al\n");
        sb.append("    outb %al, %dx\n");
        sb.append("    jmp kof_bios_lm64_loop\n");
        // B-3b-3: cópia do staging -> base fixa e salto para o payload.
        // fonte = STAGING_PROG (o staging após a página do header); destino =
        // 0x100000 (mapeada pelo loop de PD acima); tamanho = setores do
        // header * 512 (a fonte já é o staging APÓS a página do header).
        sb.append("kof_bios_lm64_copy:\n");
        sb.append("    movl ").append(STAGING + 8).append(", %ecx\n");
        sb.append("    shll $9, %ecx\n");              // bytes do payload (setores*512)
        sb.append("    movl $").append(STAGING_PROG).append(", %esi\n");
        sb.append("    movl $0x100000, %edi\n");
        sb.append("    cld\n");
        sb.append("    rep movsb\n");
        sb.append("    movq $kof_payload_entry, %rax\n");
        sb.append("    jmp *%rax\n");                  // o main Kof roda bare
        // --- rotinas/dados do stage2 ---
        // volta a .code16: o bloco `.code64` acima é do long mode; estas rotinas
        // rodam em MODO REAL (o `movw $sym, %si` codificado em 64-bit começa com
        // 0x66, que em 16-bit vira "im32" e consome 2 bytes a mais -> desalinha e
        // dá #UD no caminho de falha NOMEADA).
        sb.append(".code16\n");
        sb.append("kof_bios_print:\n");                // print 16-bit (retorna)
        sb.append("    lodsb\n");
        sb.append("    testb %al, %al\n");
        sb.append("    jz kof_bios_print_ret\n");
        sb.append("    movb %al, %bl\n");
        sb.append("    movb $0x0E, %ah\n");
        sb.append("    int $0x10\n");                  // teletype do BIOS
        sb.append("    movw $0x3FD, %dx\n");           // LSR do COM1
        sb.append("kof_bios_txe:\n");
        sb.append("    inb %dx, %al\n");
        sb.append("    testb $0x20, %al\n");           // THR vazio
        sb.append("    jz kof_bios_txe\n");
        sb.append("    movw $0x3F8, %dx\n");
        sb.append("    movb %bl, %al\n");
        sb.append("    outb %al, %dx\n");              // espelho no serial (captura qemu)
        sb.append("    jmp kof_bios_print\n");
        sb.append("kof_bios_print_ret:\n");
        sb.append("    ret\n");
        sb.append("kof_bios_load_bad:\n");
        sb.append("    movw $kof_bios_bad, %si\n");
        sb.append("    call kof_bios_print\n");
        sb.append("    cli\n");
        sb.append("kof_bios_halt:\n");
        sb.append("    hlt\n");
        sb.append("    jmp kof_bios_halt\n");
        sb.append("kof_bios_dap:\n");                  // Disk Address Packet (EDD)
        sb.append("    .byte 0x10, 0\n");
        sb.append("    .word 1\n");                    // 1 setor por chamada
        sb.append("    .word 0\n");                    // offset do buffer (por iteração)
        sb.append("    .word ").append(STAGING >> 4).append("\n"); // segmento
        sb.append("    .quad ").append(HEADER_LBA).append("\n");   // LBA do header
        sb.append("kof_bios_i:\n");
        sb.append("    .long 0\n");
        sb.append("kof_bios_ok:\n");
        sb.append("    .asciz \"KO-BIOS OK\\r\\n\"\n");
        sb.append("kof_bios_lm64_msg:\n");
        sb.append("    .asciz \"KO-BIOS LM64 OK\\r\\n\"\n");
        sb.append(".code64\n");                        // restaura o modo do payload (64-bit)
        sb.append("    .org ").append(BOOT2_SECTORS * 512).append(", 0\n"); // fecha as fatias do stage2
    }

    // ------------------------------------------------------------------
    // LBA HEADER_LBA — header do payload (magia + contagem de setores).
    // ------------------------------------------------------------------
    private static void emitPayloadHeader(StringBuilder sb) {
        sb.append(".section .payload,\"a\"\n");
        sb.append(".ascii \"KOFPAYLD\"\n");
        sb.append("    .space 4, 0\n");                // contagem de setores (patch)
        sb.append("    .space 500, 0\n");
    }
}
