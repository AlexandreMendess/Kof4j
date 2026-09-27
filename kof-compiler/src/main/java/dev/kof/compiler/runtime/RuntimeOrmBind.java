package dev.kof.compiler.runtime;

/**
 * Helpers de row-object das fatias ORM do runtime Native (x86-64) —
 * reutilizados por {@code kof_orm_save} ({@code RuntimeOrm4}) e, depois,
 * por find/all/where (F2b/F2c). Presupõem o builder de KofString de
 * {@code RuntimeOrm1} ({@code rbx}=buf, {@code r14}=cursor,
 * {@code r15d}=len) e a tabela de campos de {@code RuntimeOrmSchema}.
 *
 * <ul>
 * <li>{@code .Lorm_qraw(rsi=ptr, ecx=len)} — appends {@code "name"}: a
 *     quova crua de um nome de coluna (o {@code .Lorm2_qq} do Orm2 recebe
 *     KofString*; este recebe ptr/len, a forma da tabela parseada).</li>
 * <li>{@code .Lorm_pcstr(rsi=char*)} — append de C-string NUL-terminated
 *     (mensagens de erro do sqlite).</li>
 * <li>{@code .Lorm_bfld(rdi=stmt, esi=idx, rdx=slot*, ecx=typeCode)} —
 *     bind de UM slot do record pelo typeCode do schema: int via
 *     {@code movslq} (paridade de sinal com o Integer do JDBC), long/bool
 *     via int64, double widened (float via {@code movd+cvtss2sd}), KofString
 *     via {@code bind_text} TRANSIENT, null → {@code bind_null}. Preserva
 *     rbx/r12/r13; clobbera os registradores de arg (C ABI).</li>
 * </ul>
 */
public final class RuntimeOrmBind {

    private RuntimeOrmBind() {}

    public static void emit(StringBuilder sb) {
        sb.append("""
            # ---- .Lorm_qraw(rsi=ptr, ecx=len): append "name" --------------
            .Lorm_qraw:
                movl $34, %r8d
                call .Lorm_bh
                call .Lorm_bp
                movl $34, %r8d
                call .Lorm_bh
                ret
            # ---- .Lorm_pcstr(rsi=char*): append C-string NUL-terminated ---
            .Lorm_pcstr:
                movzbl (%rsi), %r8d
                testl %r8d, %r8d
                jz .Lorm_pcstr_done
                call .Lorm_bh
                incq %rsi
                jmp .Lorm_pcstr
            .Lorm_pcstr_done:
                ret
            # ---- .Lorm_bfld(rdi=stmt, esi=idx, rdx=slot*, ecx=typeCode) ---
            .Lorm_bfld:
                pushq %rbx
                pushq %r12
                pushq %r13
                movq %rdi, %rbx
                movl %esi, %r12d
                cmpl $2, %ecx
                je .Lorm_bfld_str
                cmpl $4, %ecx
                je .Lorm_bfld_dbl
                cmpl $5, %ecx
                je .Lorm_bfld_flt
                cmpl $1, %ecx
                je .Lorm_bfld_quad
                cmpl $3, %ecx
                je .Lorm_bfld_quad
                movslq (%rdx), %rdx          # int: sinal como o Integer JDBC
                jmp .Lorm_bfld_int
            .Lorm_bfld_quad:
                movq (%rdx), %rdx            # long/bool
            .Lorm_bfld_int:
                movq %rbx, %rdi
                movl %r12d, %esi
                call sqlite3_bind_int64
                jmp .Lorm_bfld_ret
            .Lorm_bfld_dbl:
                movq (%rdx), %rax
                movq %rax, %xmm0
                movq %rbx, %rdi
                movl %r12d, %esi
                call sqlite3_bind_double
                jmp .Lorm_bfld_ret
            .Lorm_bfld_flt:
                movd (%rdx), %xmm0
                cvtss2sd %xmm0, %xmm0
                movq %rbx, %rdi
                movl %r12d, %esi
                call sqlite3_bind_double
                jmp .Lorm_bfld_ret
            .Lorm_bfld_str:
                movq (%rdx), %r13            # KofString*
                testq %r13, %r13
                jz .Lorm_bfld_nul
                leaq 24(%r13), %rdx          # body
                movl 16(%r13), %ecx
                movslq %ecx, %rcx            # len
                movq $-1, %r8                # SQLITE_TRANSIENT
                movq %rbx, %rdi
                movl %r12d, %esi
                call sqlite3_bind_text
                jmp .Lorm_bfld_ret
            .Lorm_bfld_nul:
                movq %rbx, %rdi
                movl %r12d, %esi
                call sqlite3_bind_null
            .Lorm_bfld_ret:
                popq %r13
                popq %r12
                popq %rbx
                ret
            """);
    }
}
