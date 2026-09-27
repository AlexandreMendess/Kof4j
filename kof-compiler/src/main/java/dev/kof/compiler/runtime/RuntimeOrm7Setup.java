package dev.kof.compiler.runtime;

/**
 * F2c2 (D-DB-GAPS, 21/09) - parte de ENTRADA/QUERY de {@code kof_orm_where} /
 * {@code kof_orm_where_op}: globals, prologo, dispatch mysql, whitelist do
 * operador, SQL e bind do value. Split por responsabilidade do gate <=500
 * (TIER 13.5, 23/09) - o loop de linhas/leitura vive em
 * {@link RuntimeOrm7Fetch}. O asm e concatenado INTEIRO na ordem
 * Setup->Fetch por {@link RuntimeOrm7#emit}: as labels {@code .Lorm7_*} cruzam
 * os dois blocos e o assembler resolve no {@code .s} completo (saida emitida
 * byte-identica antes/depois do split).
 */
final class RuntimeOrm7Setup {

    private RuntimeOrm7Setup() {}

    static final String WHERE_ASM_SETUP = """
            # ---------------------------------------------------------------
            # F2c2 (D-DB-GAPS, 21/09): faces `where` da leitura row-object no
            # Native x86-64 - UMA semantica em dois globls:
            #   kof_orm_where(id,field,value,table,schema,className)   -> `=`
            #   kof_orm_where_op(id,field,op,value,table,schema,className)
            #     (arg7=className na STACK: 8(%rsp) na entry, lido antes do
            #      andq; S7f do caller ja empilha arg7 no topo - SysV)
            # corpo compartilhado (.Lorm7_body): whitelist do op (mesmas
            # strings do host: > < >= <= != LIKE ; "==" -> "="; senao throw
            # "ORM operator not allowed: " + op - medido), SQL
            # `SELECT * FROM "t" WHERE "f" <op> ?` com bind do value (mesmo
            # classificador do key no Orm5: box 284 / KofString (coerce do
            # call-site, medido) / null), e o MESMO loop de campos do
            # RuntimeOrm6 (397 incluso) acumulado em kof_list_new/kof_list_add.
            # slots do frame: 0 id | 8 field | 16 value | 24 op | 32 table |
            #   40 schema | 48 className | 56 conn | 64 ftab | 72 nFields |
            #   80 stmt | 88 list | 96 dst | 104 nCols | 112 i | 120 entry |
            #   128 nameLen (ate o SQL: opPtr) | 136 colj (ate o SQL: opLen) |
            #   144 vtab | 152 typeId | 160 totalSize
            # ---------------------------------------------------------------
                .globl kof_orm_where
                .type kof_orm_where, @function
            kof_orm_where:
                pushq %rbp
                movq %rsp, %rbp
                andq $-16, %rsp
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                subq $168, %rsp
                movq %rdi, 0(%rsp)
                movq %rsi, 8(%rsp)
                movq %rdx, 16(%rsp)
                movq $0, 24(%rsp)
                movq %rcx, 32(%rsp)
                movq %r8, 40(%rsp)
                movq %r9, 48(%rsp)
                jmp .Lorm7_body
                .globl kof_orm_where_op
                .type kof_orm_where_op, @function
            kof_orm_where_op:
                movq 8(%rsp), %r10           # className (arg7) ANTES do andq
                pushq %rbp
                movq %rsp, %rbp
                andq $-16, %rsp
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                subq $168, %rsp
                movq %rdi, 0(%rsp)
                movq %rsi, 8(%rsp)
                movq %rcx, 16(%rsp)
                movq %rdx, 24(%rsp)
                movq %r8, 32(%rsp)
                movq %r9, 40(%rsp)
                movq %r10, 48(%rsp)
            .Lorm7_body:
            # F2d4b: mysql -> restaura o frame e tail-chama .Lorm_where_my
            # (o dispatch mora aqui: as DUAS faces passam pelo corpo)
                movq (%rsp), %rdi
                call kof_db_type
                cmpl $2, %eax
                je .Lorm7_my_dispatch
            # ---- whitelist do op (host: antes do SQL; "==" -> "=") ---------
                movq 24(%rsp), %rcx
                testq %rcx, %rcx
                jz .Lorm7_opdef
                movl 16(%rcx), %eax
                cmpl $1, %eax
                jne .Lorm7_op2
                movzbl 24(%rcx), %edx
                cmpl $62, %edx
                je .Lorm7_opok
                cmpl $60, %edx
                je .Lorm7_opok
                jmp .Lorm7_opbad
            .Lorm7_op2:
                cmpl $2, %eax
                jne .Lorm7_op4
                movzbl 24(%rcx), %edx
                movzbl 25(%rcx), %r8d
                cmpl $61, %edx
                je .Lorm7_opeq
                cmpl $62, %edx
                jne .Lorm7_op2b
                cmpl $61, %r8d
                je .Lorm7_opok
                jmp .Lorm7_opbad
            .Lorm7_op2b:
                cmpl $60, %edx
                jne .Lorm7_op2c
                cmpl $61, %r8d
                je .Lorm7_opok
                jmp .Lorm7_opbad
            .Lorm7_op2c:
                cmpl $33, %edx
                jne .Lorm7_opbad
                cmpl $61, %r8d
                je .Lorm7_opok
                jmp .Lorm7_opbad
            .Lorm7_op4:
                cmpl $4, %eax
                jne .Lorm7_opbad
                movzbl 24(%rcx), %edx
                movzbl 25(%rcx), %r8d
                movzbl 26(%rcx), %r9d
                movzbl 27(%rcx), %r11d
                cmpl $76, %edx
                jne .Lorm7_opbad
                cmpl $73, %r8d
                jne .Lorm7_opbad
                cmpl $75, %r9d
                jne .Lorm7_opbad
                cmpl $69, %r11d
                jne .Lorm7_opbad
            .Lorm7_opok:
                movl 16(%rcx), %eax
                movl %eax, 136(%rsp)
                leaq 24(%rcx), %rax
                movq %rax, 128(%rsp)
                jmp .Lorm7_bodystart
            .Lorm7_opeq:
            .Lorm7_opdef:
                leaq .Lorm7_eqop(%rip), %rax
                movq %rax, 128(%rsp)
                movl $1, 136(%rsp)
            .Lorm7_bodystart:
                movq (%rsp), %rdi
                call .Lorm_conn
                movq %rax, 56(%rsp)
                movq 40(%rsp), %rdi
                call kof_orm_parse_schema
                movq %rax, 64(%rsp)          # ftab
                movq %rcx, 72(%rsp)          # nFields
                movq 48(%rsp), %rax
                leaq 24(%rax), %rdi
                movl 16(%rax), %esi
                call kof_orm_ctors
                testq %rax, %rax
                jz .Lorm7_noface
                movq %rax, 144(%rsp)         # vtab
                movq %rdx, 152(%rsp)         # typeId
                movq %rcx, 160(%rsp)         # totalSize
                call kof_list_new
                movq %rax, 88(%rsp)          # lista destino
            # ---- SQL: SELECT * FROM "t" WHERE "f" <op> ? --------------------
                movq 32(%rsp), %rax
                movl 16(%rax), %edx          # tblLen
                movq 8(%rsp), %rax
                addl 16(%rax), %edx          # + fieldLen
                addl 136(%rsp), %edx         # + opLen
                addl $45, %edx
                movl %edx, %edi
                call .Lorm_bbegin
                leaq .Lorm7_s1(%rip), %rsi
                movl $14, %ecx
                call .Lorm_bp
                movq 32(%rsp), %rdi
                call .Lorm2_qq
                leaq .Lorm7_q(%rip), %rsi
                movl $7, %ecx
                call .Lorm_bp
                movq 8(%rsp), %rdi
                call .Lorm2_qq
                leaq .Lorm7_sp(%rip), %rsi
                movl $1, %ecx
                call .Lorm_bp
                movq 128(%rsp), %rsi
                movl 136(%rsp), %ecx
                call .Lorm_bp
                leaq .Lorm7_qm(%rip), %rsi
                movl $2, %ecx
                call .Lorm_bp
                call .Lorm_bfin
            # ---- prepare + bind value (param 1) ------------------------------
                movq 56(%rsp), %rdi
                leaq 24(%rbx), %rsi
                movq $-1, %rdx
                leaq 80(%rsp), %rcx
                xorl %r8d, %r8d
                call sqlite3_prepare_v2
                movq 80(%rsp), %r12
                testq %r12, %r12
                jz .Lorm7_prep_fail
                movq %r12, %rdi
                movl $1, %esi
                movq 16(%rsp), %r13
                testq %r13, %r13
                jz .Lorm7_bnull
                movq (%r13), %rax
                movabsq $@@MAGIC@@, %rcx
                cmpq %rcx, %rax
                jne .Lorm7_strchk
                movl 8(%r13), %eax
                cmpl $0, %eax
                je .Lorm7_bint
                cmpl $1, %eax
                je .Lorm7_bquad
                cmpl $2, %eax
                je .Lorm7_bquad
                cmpl $4, %eax
                je .Lorm7_bdbl
                cmpl $5, %eax
                je .Lorm7_bflt
                jmp .Lorm7_bnull
            .Lorm7_bint:
                movl 16(%r13), %eax
                movslq %eax, %rdx
                jmp .Lorm7_bq
            .Lorm7_bquad:
                movq 16(%r13), %rdx
            .Lorm7_bq:
                call sqlite3_bind_int64
                jmp .Lorm7_bstep
            .Lorm7_bdbl:
                movq 16(%r13), %rax
                movq %rax, %xmm0
                call sqlite3_bind_double
                jmp .Lorm7_bstep
            .Lorm7_bflt:
                movl 16(%r13), %eax
                movd %eax, %xmm0
                cvtss2sd %xmm0, %xmm0
                call sqlite3_bind_double
                jmp .Lorm7_bstep
            .Lorm7_strchk:
                cmpl $1, (%r13)
                jne .Lorm7_bnull
                cmpl $0, 4(%r13)
                jne .Lorm7_bnull
                movq 8(%r13), %rax
                testq %rax, %rax
                jnz .Lorm7_bnull
                leaq 24(%r13), %rdx
                movl 16(%r13), %ecx
                movslq %ecx, %rcx
                movq $-1, %r8
                call sqlite3_bind_text
                jmp .Lorm7_bstep
            .Lorm7_bnull:
                call sqlite3_bind_null
            .Lorm7_bstep:
        """;
}
