package dev.kof.compiler.runtime;

/**
 * Fatia ORM3 do runtime Native (x86-64): {@code kof_orm_count_where} (F3a —
 * a face {@code count_where} e SQL-puro com UM bind, o menor corte vertical
 * possivel do row-object).
 *
 * <p>Reaproveita os helpers de {@code RuntimeOrm1} (builder
 * {@code .Lorm_bbegin/.Lorm_bp/.Lorm_bh/.Lorm_bfin}, {@code .Lorm_conn}) e de
 * {@code RuntimeOrm2} ({@code .Lorm2_qq}) — emitidos no MESMO .s quando
 * {@code usesOrm}. O SQL sai exatamente como no host
 * ({@code JvmOrmRuntime.kof_orm_count_where}):
 * {@code SELECT COUNT(*) FROM "t" WHERE "f" = ?} — o valor NUNCA entra
 * concatenado (injection-proof como o PreparedStatement do host), chega
 * como o box de erasure §284 ({@code [magic][tag][value]} 24B) ou KofString
 * e e despachado pelo tag: int (0, com {@code movslq} — paridade de sinal
 * com o {@code Integer} do JDBC), long (2), bool (3), double (4), float (5,
 * widened p/ REAL como o {@code setFloat} do driver); sem MAGIC e tag
 * KofString → {@code bind_text} transient; null → {@code bind_null}; outro
 * shape lanca {@code .Lorm3_badv} honesto (R6 — nunca bind de lixo).
 *
 * <p>Falha de prepare = 0 (mesma postura medida do {@code kof_orm_count}
 * F1b); step sem ROW = 0 como {@code rs.next()==false} no host.
 *
 * <p>Contrato de pilha (F1c): {@code andq} no prologo, moldura 72 (8 mod
 * 16), todo {@code call} de C sai com {@code rsp % 16 == 0}; epilogio
 * espelha delete_all.
 */
public final class RuntimeOrm3 {

    private RuntimeOrm3() {}

    public static void emit(StringBuilder sb) {
        sb.append("""
            # ---------------------------------------------------------------
            # kof_orm_count_where(id*, field*, value*, table*, schema*) -> Long
            #   slots: 0 id | 8 field | 16 value | 24 table | 32 schema |
            #   40 conn | 48 stmt | 56 (alinhamento)
            # ---------------------------------------------------------------
                .globl kof_orm_count_where
                .type kof_orm_count_where, @function
            kof_orm_count_where:
                pushq %rbp
                movq %rsp, %rbp
                andq $-16, %rsp
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                subq $72, %rsp
                movq %rdi, (%rsp)
                movq %rsi, 8(%rsp)
                movq %rdx, 16(%rsp)
                movq %rcx, 24(%rsp)
                movq %r8, 32(%rsp)
                movq (%rsp), %rdi
                call kof_db_type
                cmpl $2, %eax
                je .Lorm3cw_my_dispatch
                movq (%rsp), %rdi
                call .Lorm_conn
                movq %rax, 40(%rsp)
            # ---- SQL: SELECT COUNT(*) FROM "t" WHERE "f" = ? -------------
                movq 24(%rsp), %rax
                movl 16(%rax), %edi             # tblLen
                movq 8(%rsp), %rax
                addl 16(%rax), %edi             # + fldLen
                addl $88, %edi
                call .Lorm_bbegin
                leaq .Lorm3cw_s1(%rip), %rsi
                movl $21, %ecx
                call .Lorm_bp
                movq 24(%rsp), %rdi
                call .Lorm2_qq
                leaq .Lorm3cw_s2(%rip), %rsi
                movl $7, %ecx
                call .Lorm_bp
                movq 8(%rsp), %rdi
                call .Lorm2_qq
                leaq .Lorm3cw_s3(%rip), %rsi
                movl $4, %ecx
                call .Lorm_bp
                call .Lorm_bfin
            # ---- prepare --------------------------------------------------
                movq 40(%rsp), %rdi
                leaq 24(%rbx), %rsi
                movq $-1, %rdx
                leaq 48(%rsp), %rcx
                xorl %r8d, %r8d
                call sqlite3_prepare_v2
                movq 48(%rsp), %r12             # stmt (0 = falhou como o
                testq %r12, %r12                #   count F1b: devolve 0)
                jz .Lorm3cw_zero
            # ---- bind ? (value box §284 / KofString / null) --------------
                movq %r12, %rdi
                movl $1, %esi
                movq 16(%rsp), %r13             # box
                testq %r13, %r13
                jz .Lorm3cw_bn
                movq (%r13), %rax
                movabsq $@@MAGIC@@, %rcx
                cmpq %rcx, %rax
                jne .Lorm3cw_strchk
                movl 8(%r13), %eax              # tag
                cmpl $0, %eax
                je .Lorm3cw_bint
                cmpl $2, %eax
                je .Lorm3cw_bquad
                cmpl $3, %eax
                je .Lorm3cw_bquad
                cmpl $4, %eax
                je .Lorm3cw_bdbl
                cmpl $5, %eax
                je .Lorm3cw_bflt
                jmp .Lorm3cw_bad
            .Lorm3cw_bint:
                movl 16(%r13), %eax
                movslq %eax, %rdx
                movq %r12, %rdi
                movl $1, %esi
                call sqlite3_bind_int64
                jmp .Lorm3cw_go
            .Lorm3cw_bquad:
                movq 16(%r13), %rdx
                movq %r12, %rdi
                movl $1, %esi
                call sqlite3_bind_int64
                jmp .Lorm3cw_go
            .Lorm3cw_bdbl:
                movq 16(%r13), %rax
                movq %rax, %xmm0
                movq %r12, %rdi
                movl $1, %esi
                call sqlite3_bind_double
                jmp .Lorm3cw_go
            .Lorm3cw_bflt:
                movl 16(%r13), %eax
                movd %eax, %xmm0
                cvtss2sd %xmm0, %xmm0
                movq %r12, %rdi
                movl $1, %esi
                call sqlite3_bind_double
                jmp .Lorm3cw_go
            .Lorm3cw_strchk:
                cmpl $1, (%r13)                 # KofString tag (1,0,0)?
                jne .Lorm3cw_bad
                cmpl $0, 4(%r13)
                jne .Lorm3cw_bad
                movq 8(%r13), %rax
                testq %rax, %rax
                jnz .Lorm3cw_bad
                leaq 24(%r13), %rdx
                movl 16(%r13), %ecx
                movslq %ecx, %rcx
                movq $-1, %r8                   # SQLITE_TRANSIENT
                movq %r12, %rdi
                movl $1, %esi
                call sqlite3_bind_text
                jmp .Lorm3cw_go
            .Lorm3cw_bn:
                movq %r12, %rdi
                movl $1, %esi
                call sqlite3_bind_null
                jmp .Lorm3cw_go
            .Lorm3cw_bad:
                movq %r12, %rdi
                call sqlite3_finalize
                leaq .Lorm3_badv(%rip), %rdi
                call kof_throw_string
                ud2
            # ---- step + column_int64(0) -----------------------------------
            .Lorm3cw_go:
                movq %r12, %rdi
                call sqlite3_step
                cmpl $100, %eax                 # SQLITE_ROW (a API: 100=ROW,
                                                #   101=DONE — invertido do
                                                #   chutado na 1a versao)
                jne .Lorm3cw_fin0
                movq %r12, %rdi
                xorl %esi, %esi
                call sqlite3_column_int64
                movq %rax, %r13                 # guarda o count — finalize
                                                #   pisa %rax (bug medido
                                                #   20/09: devolvia 0 sempre)
                movq %r12, %rdi
                call sqlite3_finalize
                movq %r13, %rax
                jmp .Lorm3cw_ret
            .Lorm3cw_fin0:
                movq %r12, %rdi
                call sqlite3_finalize
            .Lorm3cw_zero:
                xorl %eax, %eax
            .Lorm3cw_ret:
                addq $72, %rsp
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                movq %rbp, %rsp
                popq %rbp
                ret

            # F2d6: mysql -> restaura o frame e tail-chama .Lorm_cw_my
            # (args originais nos slots; a asm mysql vive no
            # RuntimeOrmMysqlCountWhere, emitido junto no mesmo .s).
            .Lorm3cw_my_dispatch:
                movq 0(%rsp), %rdi
                movq 8(%rsp), %rsi
                movq 16(%rsp), %rdx
                movq 24(%rsp), %rcx
                movq 32(%rsp), %r8
                addq $72, %rsp
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                movq %rbp, %rsp
                popq %rbp
                jmp .Lorm_cw_my

            # ---------------------- literais --------------------------------
            .Lorm3cw_s1:
                .ascii "SELECT COUNT(*) FROM "
            .Lorm3cw_s2:
                .ascii " WHERE "
            .Lorm3cw_s3:
                .ascii " = ?"
            .Lorm3_badv:
                .long 1
                .long 0
                .quad 0
                .long .Lorm3_badv_len
                .long 0
            .Lorm3_badv_body:
                .ascii "orm.count bind value: unsupported type on Native (ORM001)"
                .byte 0
                .set .Lorm3_badv_len, . - .Lorm3_badv_body - 1
            """);
    }
}
