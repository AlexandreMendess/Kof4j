package dev.kof.compiler.runtime;

/**
 * F2d3b (D-DB-GAPS DB-3, 21/09): literal SQL do key do {@code orm.find} no
 * wire MySQL do Native x86-64 — extraido do {@code RuntimeOrmMysqlFind}
 * pelo gate 500 (responsabilidade: classificar o key e renderizar o
 * literal). KofString vira {@code kof_db_mysql_render} (aspas + escape),
 * box §284 vira {@code kof_long_to_string}/{@code kof_double_to_string}
 * (literal cru), null vira {@code NULL}; tipo fora do contrato -> throw
 * ORM001 (R6). Medido: o call-site nativo coage primitivo->OBJ, entao o
 * key chega KofString ou box — o classifier cobre os dois.
 *
 * <p>Tambem moram aqui os helpers de leitura de valor do resultset
 * ({@code .Lorm_rd_*}: atoi com sinal, bool §397 e copia NUL-ada p/
 * strtod), usados pelo row do {@code RuntimeOrmMysqlFind}. Labels
 * resolvidos no mesmo .s (slices emitidas juntas por NativeOrmEmit).
 */
public final class RuntimeOrmMysqlKeyLit {

    private RuntimeOrmMysqlKeyLit() {}

    public static void emit(StringBuilder sb) {
        sb.append("""
            # ===== key -> literal SQL p/ orm.find mysql (F2d3b) ==========

            # .Lorm_key_lit(rdi=key) -> rax KofString literal SQL
            .Lorm_key_lit:
                pushq %rbp
                movq %rsp, %rbp
                andq $-16, %rsp
                pushq %r12
                subq $8, %rsp
                movq %rdi, %r12
                testq %r12, %r12
                jz .Lorm_kl_null
                movq (%r12), %rax
                movabsq $@@MAGIC@@, %rcx
                cmpq %rcx, %rax
                jne .Lorm_kl_str
                movl 8(%r12), %eax                # tag do box §284
                cmpl $0, %eax
                je .Lorm_kl_int
                cmpl $1, %eax
                je .Lorm_kl_bool
                cmpl $2, %eax
                je .Lorm_kl_long
                cmpl $4, %eax
                je .Lorm_kl_dbl
                cmpl $5, %eax
                je .Lorm_kl_flt
                jmp .Lorm_kl_bad
            .Lorm_kl_int:
                movslq 16(%r12), %rdi
                call kof_long_to_string
                jmp .Lorm_kl_ret
            .Lorm_kl_long:
                movq 16(%r12), %rdi
                call kof_long_to_string
                jmp .Lorm_kl_ret
            .Lorm_kl_bool:
                movl 16(%r12), %edi
                testl %edi, %edi
                setne %dil
                movzbl %dil, %edi
                call kof_long_to_string
                jmp .Lorm_kl_ret
            .Lorm_kl_dbl:
                movq 16(%r12), %rax
                movq %rax, %xmm0
                call kof_double_to_string
                jmp .Lorm_kl_ret
            .Lorm_kl_flt:
                movl 16(%r12), %eax
                movd %eax, %xmm0
                cvtss2sd %xmm0, %xmm0
                call kof_double_to_string
                jmp .Lorm_kl_ret
            .Lorm_kl_str:
                cmpl $1, (%r12)                   # KofString
                jne .Lorm_kl_bad
                cmpl $0, 4(%r12)
                jne .Lorm_kl_bad
                cmpq $0, 8(%r12)
                jne .Lorm_kl_bad
                movq %r12, %rdi
                call kof_db_mysql_render
                jmp .Lorm_kl_ret
            .Lorm_kl_null:
                movl $8, %edi
                call .Lorm_bbegin
                leaq .Lorm_kl_nul(%rip), %rsi
                movl $4, %ecx
                call .Lorm_bp
                call .Lorm_bfin
                movq %rbx, %rax
            .Lorm_kl_ret:
                addq $8, %rsp
                popq %r12
                movq %rbp, %rsp
                popq %rbp
                ret
            .Lorm_kl_bad:
                leaq .Lorm_kl_badv(%rip), %rdi
                call kof_throw_string
                ud2

            # .Lorm_rd_atoi(rdi=ptr, esi=len) -> rax (sinal opcional)
            .Lorm_rd_atoi:
                xorl %eax, %eax
                xorl %r8d, %r8d
                testl %esi, %esi
                jle .Lorm_rd_ai_done
                cmpb $'-', (%rdi)
                jne .Lorm_rd_ai_loop
                movl $1, %r8d
                incq %rdi
                decl %esi
            .Lorm_rd_ai_loop:
                testl %esi, %esi
                jle .Lorm_rd_ai_sign
                movzbl (%rdi), %ecx
                cmpb $'0', %cl
                jb .Lorm_rd_ai_sign
                cmpb $'9', %cl
                ja .Lorm_rd_ai_sign
                imulq $10, %rax, %rax
                subl $'0', %ecx
                addq %rcx, %rax
                incq %rdi
                decl %esi
                jmp .Lorm_rd_ai_loop
            .Lorm_rd_ai_sign:
                testl %r8d, %r8d
                jz .Lorm_rd_ai_done
                negq %rax
            .Lorm_rd_ai_done:
                ret

            # .Lorm_rd_bool(rdi=ptr, esi=len) -> rax 0/1 (paridade §397:
            # numerico != 0; texto literal "true" case-insensitive)
            .Lorm_rd_bool:
                testl %esi, %esi
                jle .Lorm_rd_bl_false
                movzbl (%rdi), %eax
                cmpb $'-', %al
                je .Lorm_rd_bl_num
                cmpb $'0', %al
                jb .Lorm_rd_bl_true
                cmpb $'9', %al
                ja .Lorm_rd_bl_true
            .Lorm_rd_bl_num:
                call .Lorm_rd_atoi
                testq %rax, %rax
                setne %al
                movzbl %al, %eax
                ret
            .Lorm_rd_bl_true:
                cmpl $4, %esi
                jl .Lorm_rd_bl_false
                movzbl 0(%rdi), %eax
                orl $32, %eax
                cmpl $116, %eax                  # 't'
                jne .Lorm_rd_bl_false
                movzbl 1(%rdi), %eax
                orl $32, %eax
                cmpl $114, %eax                  # 'r'
                jne .Lorm_rd_bl_false
                movzbl 2(%rdi), %eax
                orl $32, %eax
                cmpl $117, %eax                  # 'u'
                jne .Lorm_rd_bl_false
                movzbl 3(%rdi), %eax
                orl $32, %eax
                cmpl $101, %eax                  # 'e'
                jne .Lorm_rd_bl_false
                movl $1, %eax
                ret
            .Lorm_rd_bl_false:
                xorl %eax, %eax
                ret

            # .Lorm_rd_copy_num(rdi=ptr, esi=len, rdx=dst) -> rax=dst + NUL
            # (teto 63 bytes; p/ o strtod do double/float no find mysql)
            .Lorm_rd_copy_num:
                movq %rdx, %r8
                xorl %ecx, %ecx
                cmpl $63, %esi
                jle .Lorm_rd_cn_loop
                movl $63, %esi
            .Lorm_rd_cn_loop:
                cmpl %esi, %ecx
                jge .Lorm_rd_cn_done
                movzbl (%rdi,%rcx), %eax
                movb %al, (%rdx,%rcx)
                incl %ecx
                jmp .Lorm_rd_cn_loop
            .Lorm_rd_cn_done:
                movb $0, (%rdx,%rcx)
                movq %r8, %rax
                ret

            # ---------------------- literais --------------------------------
            .Lorm_kl_nul:
                .ascii "NULL"
            .Lorm_kl_badv:
                .long 1
                .long 0
                .quad 0
                .long .Lorm_kl_badv_len
                .long 0
            .Lorm_kl_badv_body:
                .ascii "orm.find bind value: unsupported type on Native (ORM001)"
                .byte 0
                .set .Lorm_kl_badv_len, . - .Lorm_kl_badv_body - 1
            """);
    }
}
