package dev.kof.compiler.runtime;

/**
 * F2d4d (D-DB-GAPS DB-3, 21/09): face {@code saveAll} da escrita row-object
 * no wire MySQL do runtime Native x86-64 — {@code kof_orm_save_all} com
 * {@code kof_db_type(id)==2} despacha para {@code .Lorm_saveall_my}, que
 * espelha {@code JvmOrmRuntime.kof_orm_save_all} (loop de
 * {@code kof_orm_save} por item, retorno do save DESCARTADO — a List de
 * entrada continua com os objetos originais) e devolve {@code true} sempre
 * que o loop termina. O save de UM item ({@code .Lorm_sai_my}) segue as 3
 * saidas medidas do host (dialeto mysql nao-mongo): pk nula/0 -> INSERT sem
 * a coluna PK (id gerada); pk != 0 -> UPDATE; UPDATE 0 linhas -> INSERT de
 * TODAS as colunas (upsert-like). Valores como literal por typeCode via
 * {@code RuntimeOrmMysqlFieldLit}; execucao com throw via
 * {@code RuntimeOrmMysqlExec}. GC-safe (itens alcancaveis pela List);
 * contrato de pilha F1c.
 */
public final class RuntimeOrmMysqlSaveAll {

    private RuntimeOrmMysqlSaveAll() {}

    public static void emit(StringBuilder sb) {
        sb.append("""
            # ---------------------------------------------------------------
            # F2d4d (D-DB-GAPS, 21/09): kof_orm_save_all mysql ->
            #   .Lorm_saveall_my(id*,items*,table*,schema*) -> rax=1
            #   slots: 0 id | 8 items | 16 table | 24 schema | 32 ftab |
            #          40 nFields | 48 pkIndex | 56 i | 64 n
            # ---------------------------------------------------------------
            .Lorm_saveall_my:
                pushq %rbp
                movq %rsp, %rbp
                andq $-16, %rsp
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                subq $88, %rsp
                movq %rdi, 0(%rsp)
                movq %rsi, 8(%rsp)
                movq %rdx, 16(%rsp)
                movq %rcx, 24(%rsp)
                movq 24(%rsp), %rdi
                call kof_orm_parse_schema
                movq %rax, 32(%rsp)          # ftab
                movq %rcx, 40(%rsp)          # nFields
                movq %r8, 48(%rsp)           # pkIndex
                movq $0, 56(%rsp)            # i
                movq 8(%rsp), %rdi
                call kof_list_size
                movq %rax, 64(%rsp)          # n
            .Lorm_sa_loop:
                movq 56(%rsp), %rax
                cmpq 64(%rsp), %rax
                jge .Lorm_sa_done
                movq 8(%rsp), %rdi
                movl 56(%rsp), %esi
                call kof_list_get
                movq 0(%rsp), %rdi
                movq %rax, %rsi
                movq 16(%rsp), %rdx
                movq 32(%rsp), %rcx
                movq 40(%rsp), %r8
                movq 48(%rsp), %r9
                call .Lorm_sai_my
                incq 56(%rsp)
                jmp .Lorm_sa_loop
            .Lorm_sa_done:
                movl $1, %eax
                addq $88, %rsp
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                movq %rbp, %rsp
                popq %rbp
                ret

            # ---------------------------------------------------------------
            # .Lorm_sai_my(rdi=id*, rsi=item*, rdx=table*, rcx=ftab,
            #   r8=nFields, r9=pkIndex) -> salva UM item no wire MySQL
            #   slots: 0 id | 8 item | 16 table | 24 ftab | 32 nFields |
            #          40 pkIndex | 48 pkType | 56 pkVal | 64 skipPk |
            #          72 i | 80 first | 88 entry | 96 pkEntry | 112 est
            # ---------------------------------------------------------------
            .Lorm_sai_my:
                pushq %rbp
                movq %rsp, %rbp
                andq $-16, %rsp
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                subq $120, %rsp
                movq %rdi, 0(%rsp)
                movq %rsi, 8(%rsp)
                movq %rdx, 16(%rsp)
                movq %rcx, 24(%rsp)
                movq %r8, 32(%rsp)
                movq %r9, 40(%rsp)
            # ---- pkType + pkVal + decisao do path (espelho do Orm4) ------
                movq 40(%rsp), %rax
                shlq $5, %rax
                addq 24(%rsp), %rax
                movq %rax, 96(%rsp)          # pkEntry
                movl 12(%rax), %ecx
                movl %ecx, 48(%rsp)          # pkType
                movq 40(%rsp), %rax
                movq 8(%rsp), %rdx
                leaq 16(%rdx,%rax,8), %rdx   # slot da pk
                movl 48(%rsp), %ecx
                cmpl $2, %ecx
                je .Lsa_pk_str
                cmpl $3, %ecx
                je .Lsa_pk_bool
                cmpl $5, %ecx
                je .Lsa_pk_flt
                cmpl $4, %ecx
                je .Lsa_pk_dbl
                cmpl $1, %ecx
                je .Lsa_pk_lng
                movslq (%rdx), %rax          # int
                jmp .Lsa_pk_have
            .Lsa_pk_lng:
                movq (%rdx), %rax
                jmp .Lsa_pk_have
            .Lsa_pk_dbl:
                movsd (%rdx), %xmm0
                cvttsd2si %xmm0, %rax
                jmp .Lsa_pk_sat
            .Lsa_pk_flt:
                movss (%rdx), %xmm0
                cvttss2si %xmm0, %rax
            .Lsa_pk_sat:
                movabsq $-9223372036854775808, %rcx
                cmpq %rcx, %rax
                jne .Lsa_pk_have
                xorl %eax, %eax              # NaN -> 0 (host longValue)
            .Lsa_pk_have:
                movq %rax, 56(%rsp)
                testq %rax, %rax
                jz .Lsa_insert_gen          # pk 0 -> INSERT sem a pk
                jmp .Lsa_update
            .Lsa_pk_str:
                movq (%rdx), %rax            # null -> INSERT sem a pk
                movq %rax, 56(%rsp)
                testq %rax, %rax
                jz .Lsa_insert_gen
                jmp .Lsa_update
            .Lsa_pk_bool:
                movq (%rdx), %rax            # bool: UPDATE sempre (host)
                movq %rax, 56(%rsp)
                jmp .Lsa_update
            .Lsa_insert_gen:
                movq $1, 64(%rsp)            # skipPk
                jmp .Lsa_insert
            # ---- UPDATE: estimativa do buffer (table*2 + 512 +
            #      nFields*48 + sum(nameLen*2)) ----------------------------
            .Lsa_update:
                movq $0, 64(%rsp)            # skipPk=0 (ainda: UPDATE)
            # ---- tamanho do builder --------------------------------------
                movq $512, %rax
                movq 16(%rsp), %rdx
                movl 16(%rdx), %edx
                addq %rdx, %rax
                addq %rdx, %rax              # tableLen*2
                movq 32(%rsp), %rcx
                imulq $48, %rcx, %rcx
                addq %rcx, %rax
                movq $0, %rcx
                movq 24(%rsp), %rdx          # ftab
            .Lsa_est_upd:
                cmpq 32(%rsp), %rcx
                jge .Lsa_est_upd_done
                movl 8(%rdx), %r8d
                addq %r8, %rax
                addq %r8, %rax
                addq $32, %rdx
                incq %rcx
                jmp .Lsa_est_upd
            .Lsa_est_upd_done:
                movq %rax, 112(%rsp)
                movl 112(%rsp), %edi
                call .Lorm_bbegin
                leaq .Lsa_upd(%rip), %rsi
                movl $7, %ecx
                call .Lorm_bp
                movq 16(%rsp), %rax
                leaq 24(%rax), %rsi
                movl 16(%rax), %ecx
                call .Lorm_sa_qp             # `table`
                leaq .Lsa_set(%rip), %rsi
                movl $5, %ecx
                call .Lorm_bp
                movq $0, 72(%rsp)
                movq $0, 80(%rsp)
            .Lsa_upd_col:
                movq 72(%rsp), %rax
                cmpq 32(%rsp), %rax
                jge .Lsa_upd_col_end
                cmpq 40(%rsp), %rax
                je .Lsa_upd_col_next
                cmpq $0, 80(%rsp)
                jne .Lsa_upd_col_c2
                movq $1, 80(%rsp)
                jmp .Lsa_upd_col_q
            .Lsa_upd_col_c2:
                leaq .Lsa_cs(%rip), %rsi
                movl $2, %ecx
                call .Lorm_bp
            .Lsa_upd_col_q:
                movq 72(%rsp), %rax
                shlq $5, %rax
                addq 24(%rsp), %rax
                movq %rax, 88(%rsp)
                movq %rax, %rdi
                call .Lorm_sa_qn
                leaq .Lsa_eq(%rip), %rsi
                movl $3, %ecx
                call .Lorm_bp
                movq 88(%rsp), %rdi
                movq 8(%rsp), %rsi
                movq 72(%rsp), %rax
                leaq 16(%rsi,%rax,8), %rsi
                call .Lorm_sa_lit
            .Lsa_upd_col_next:
                incq 72(%rsp)
                jmp .Lsa_upd_col
            .Lsa_upd_col_end:
                leaq .Lsa_where(%rip), %rsi
                movl $7, %ecx
                call .Lorm_bp
                movq 96(%rsp), %rdi
                call .Lorm_sa_qn
                leaq .Lsa_eq(%rip), %rsi
                movl $3, %ecx
                call .Lorm_bp
                movq 96(%rsp), %rdi
                movq 8(%rsp), %rsi
                movq 40(%rsp), %rax
                leaq 16(%rsi,%rax,8), %rsi
                call .Lorm_sa_lit
                call .Lorm_bfin
                movq %rbx, %rsi
                movq 0(%rsp), %rdi
                call .Lorm_sa_exec
                testl %eax, %eax
                jle .Lsa_upd_miss
                jmp .Lsa_ret
            .Lsa_upd_miss:
                movq $0, 64(%rsp)            # INSERT de TODAS as colunas
                jmp .Lsa_insert
            # ---- INSERT (skipPk=1: sem a coluna PK) / upsert (skipPk=0) --
            .Lsa_insert:
                movq $512, %rax
                movq 16(%rsp), %rdx
                movl 16(%rdx), %edx
                addq %rdx, %rax
                addq %rdx, %rax
                movq 32(%rsp), %rcx
                imulq $48, %rcx, %rcx
                addq %rcx, %rax
                movq $0, %rcx
                movq 24(%rsp), %rdx
            .Lsa_est_ins:
                cmpq 32(%rsp), %rcx
                jge .Lsa_est_ins_done
                movl 8(%rdx), %r8d
                addq %r8, %rax
                addq %r8, %rax
                addq $32, %rdx
                incq %rcx
                jmp .Lsa_est_ins
            .Lsa_est_ins_done:
                movq %rax, 112(%rsp)
                movl 112(%rsp), %edi
                call .Lorm_bbegin
                leaq .Lsa_ins(%rip), %rsi
                movl $12, %ecx
                call .Lorm_bp
                movq 16(%rsp), %rax
                leaq 24(%rax), %rsi
                movl 16(%rax), %ecx
                call .Lorm_sa_qp             # `table`
                leaq .Lsa_opn(%rip), %rsi
                movl $2, %ecx
                call .Lorm_bp
                movq $0, 72(%rsp)
                movq $0, 80(%rsp)
            .Lsa_ins_col:
                movq 72(%rsp), %rax
                cmpq 32(%rsp), %rax
                jge .Lsa_ins_col_end
                cmpq $0, 64(%rsp)
                je .Lsa_ins_col_go
                cmpq 40(%rsp), %rax
                je .Lsa_ins_col_next
            .Lsa_ins_col_go:
                cmpq $0, 80(%rsp)
                jne .Lsa_ins_col_c2
                movq $1, 80(%rsp)
                jmp .Lsa_ins_col_q
            .Lsa_ins_col_c2:
                leaq .Lsa_cs(%rip), %rsi
                movl $2, %ecx
                call .Lorm_bp
            .Lsa_ins_col_q:
                movq 72(%rsp), %rax
                shlq $5, %rax
                addq 24(%rsp), %rax
                movq %rax, %rdi
                call .Lorm_sa_qn
            .Lsa_ins_col_next:
                incq 72(%rsp)
                jmp .Lsa_ins_col
            .Lsa_ins_col_end:
                leaq .Lsa_val(%rip), %rsi
                movl $10, %ecx
                call .Lorm_bp
                movq $0, 72(%rsp)
                movq $0, 80(%rsp)
            .Lsa_ins_mk:
                movq 72(%rsp), %rax
                cmpq 32(%rsp), %rax
                jge .Lsa_ins_mk_end
                cmpq $0, 64(%rsp)
                je .Lsa_ins_mk_go
                cmpq 40(%rsp), %rax
                je .Lsa_ins_mk_next
            .Lsa_ins_mk_go:
                cmpq $0, 80(%rsp)
                jne .Lsa_ins_mk_c2
                movq $1, 80(%rsp)
                jmp .Lsa_ins_mk_lit
            .Lsa_ins_mk_c2:
                leaq .Lsa_cs(%rip), %rsi
                movl $2, %ecx
                call .Lorm_bp
            .Lsa_ins_mk_lit:
                movq 72(%rsp), %rax
                shlq $5, %rax
                movq 24(%rsp), %rdi
                addq %rax, %rdi
                movq 8(%rsp), %rsi
                movq 72(%rsp), %rax
                leaq 16(%rsi,%rax,8), %rsi
                call .Lorm_sa_lit
            .Lsa_ins_mk_next:
                incq 72(%rsp)
                jmp .Lsa_ins_mk
            .Lsa_ins_mk_end:
                leaq .Lsa_cp(%rip), %rsi
                movl $1, %ecx
                call .Lorm_bp
                call .Lorm_bfin
                movq %rbx, %rsi
                movq 0(%rsp), %rdi
                call .Lorm_sa_exec
            .Lsa_ret:
                addq $120, %rsp
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                movq %rbp, %rsp
                popq %rbp
                ret

            # ---------------------- literais --------------------------------
            .Lsa_upd:
                .ascii "UPDATE "
            .Lsa_set:
                .ascii " SET "
            .Lsa_where:
                .ascii " WHERE "
            .Lsa_eq:
                .ascii " = "
            .Lsa_cs:
                .ascii ", "
            .Lsa_ins:
                .ascii "INSERT INTO "
            .Lsa_opn:
                .ascii " ("
            .Lsa_val:
                .ascii ") VALUES ("
            .Lsa_cp:
                .ascii ")"
            .Lsa_null:
                .ascii "NULL"
            """);
    }
}
