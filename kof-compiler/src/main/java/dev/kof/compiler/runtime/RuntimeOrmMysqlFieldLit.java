package dev.kof.compiler.runtime;

/**
 * F2d4d (D-DB-GAPS DB-3, 21/09): literal SQL de UM campo do schema para o
 * wire MySQL do Native x86-64 (extraido do {@code RuntimeOrmMysqlSaveAll}
 * pelo gate 500 — responsabilidade: valor -> literal + quova de nome).
 * {@code .Lorm_sa_qp/.Lorm_sa_qn} appendam {@code `nome`} (backtick,
 * dialeto do host); {@code .Lorm_sa_lit} converte o slot cru do record pelo
 * typeCode: int {@code movslq}, long/bool {@code kof_long_to_string} (bool
 * vira 0/1 como o bind do host), double/float {@code kof_double_to_string}
 * (float com {@code cvtss2sd}), KofString {@code kof_db_mysql_render}
 * (aspas + escape) e KofString null -> {@code NULL}; tipo fora do contrato
 * -> throw ORM001 (R6). Preserva o builder de {@code RuntimeOrm1}
 * (rbx/r14/r15).
 */
public final class RuntimeOrmMysqlFieldLit {

    private RuntimeOrmMysqlFieldLit() {}

    public static void emit(StringBuilder sb) {
        sb.append("""
            # `.Lorm_sa_qp(rsi=ptr, ecx=len): append `ptr`` ---------------
            .Lorm_sa_qp:
                pushq %r12
                pushq %r13
                movq %rsi, %r12
                movl %ecx, %r13d
                movl $96, %r8d
                call .Lorm_bh
                movq %r12, %rsi
                movl %r13d, %ecx
                call .Lorm_bp
                movl $96, %r8d
                call .Lorm_bh
                popq %r13
                popq %r12
                ret
            # `.Lorm_sa_qn(rdi=entry): append `name`` ----------------------
            .Lorm_sa_qn:
                movq 0(%rdi), %rsi
                movl 8(%rdi), %ecx
                jmp .Lorm_sa_qp

            # `.Lorm_sa_lit(rdi=entry, rsi=slot*): literal SQL do campo ----
            .Lorm_sa_lit:
                pushq %r12
                pushq %r13
                subq $8, %rsp
                movq %rdi, %r12
                movq %rsi, %r13
                movl 12(%r12), %eax
                cmpl $2, %eax
                je .Lsa_lit_str
                cmpl $3, %eax
                je .Lsa_lit_bool
                cmpl $4, %eax
                je .Lsa_lit_dbl
                cmpl $5, %eax
                je .Lsa_lit_flt
                cmpl $1, %eax
                je .Lsa_lit_lng
                cmpl $0, %eax
                je .Lsa_lit_int
                leaq .Lsa_badv(%rip), %rdi
                call kof_throw_string
                ud2
            .Lsa_lit_int:
                movslq (%r13), %rdi
                call kof_long_to_string
                jmp .Lsa_lit_raw
            .Lsa_lit_lng:
                movq (%r13), %rdi
                call kof_long_to_string
                jmp .Lsa_lit_raw
            .Lsa_lit_bool:
                movq (%r13), %rax
                testq %rax, %rax
                setne %al
                movzbl %al, %edi
                call kof_long_to_string
                jmp .Lsa_lit_raw
            .Lsa_lit_dbl:
                movq (%r13), %rax
                movq %rax, %xmm0
                call kof_double_to_string
                jmp .Lsa_lit_raw
            .Lsa_lit_flt:
                movd (%r13), %xmm0
                cvtss2sd %xmm0, %xmm0
                call kof_double_to_string
                jmp .Lsa_lit_raw
            .Lsa_lit_str:
                movq (%r13), %rdi
                testq %rdi, %rdi
                jz .Lsa_lit_null
                call kof_db_mysql_render
                jmp .Lsa_lit_raw
            .Lsa_lit_null:
                leaq .Lsa_null(%rip), %rsi
                movl $4, %ecx
                call .Lorm_bp
                jmp .Lsa_lit_done
            .Lsa_lit_raw:
                leaq 24(%rax), %rsi
                movl 16(%rax), %ecx
                call .Lorm_bp
            .Lsa_lit_done:
                addq $8, %rsp
                popq %r13
                popq %r12
                ret

            .Lsa_badv:
                .long 1
                .long 0
                .quad 0
                .long .Lsa_badv_len
                .long 0
            .Lsa_badv_body:
                .ascii "orm.saveAll: unsupported field type on Native (ORM001)"
                .byte 0
                .set .Lsa_badv_len, . - .Lsa_badv_body - 1
            """);
    }
}
