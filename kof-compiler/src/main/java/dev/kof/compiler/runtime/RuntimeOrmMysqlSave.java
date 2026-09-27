package dev.kof.compiler.runtime;

/**
 * F2d5 (D-DB-GAPS DB-3, 22/09): face {@code save} row-object no wire MySQL
 * do runtime Native x86-64 — {@code kof_orm_save} com
 * {@code kof_db_type(id)==2} despacha para {@code .Lorm_save_my}, que
 * espelha as 3 saidas medidas do host ({@code JvmOrmRuntime.kof_orm_save},
 * dialeto mysql nao-mongo):
 * <ol>
 * <li><b>PK nula/0</b> (int/long == 0, double/float truncado == 0, String
 *     null) → {@code INSERT} SEM a coluna PK (o banco gera o id), le
 *     {@code SELECT LAST_INSERT_ID()} ({@code RuntimeOrmMysqlLastId}) e
 *     devolve uma NOVA instancia do record com a PK patchada
 *     ({@code kof_alloc} + {@code kof_init_object} com typeId/vtable da
 *     fonte + {@code rep movsq} dos slots 8B — layout ClassLayout 16+8i,
 *     como o Orm4 sqlite);</li>
 * <li><b>PK != 0</b> → {@code UPDATE `t` SET `f` = <lit>, ... WHERE `pk` =
 *     <lit>}; afetadas &gt; 0 (FOUND rows, CLIENT_FOUND_ROWS) → devolve o
 *     MESMO ponteiro;</li>
 * <li><b>UPDATE 0 linhas</b> → INSERT de TODAS as colunas (upsert-like) e
 *     devolve o mesmo ponteiro.</li>
 * </ol>
 *
 * <p>SQL com backtick do dialeto, valores por {@code .Lorm_sa_lit}
 * (typeCode) e exec COM_QUERY com throw por {@code .Lorm_sa_exec}
 * (F2d4d). Bool na PK: UPDATE sempre (host). Tipo fora do contrato → ORM001
 * pelo lit. GC-safe: obj no slot de stack; a instancia nova nasce de
 * {@code kof_alloc} e e inicializada sem allocacao intermediaria (como o
 * Orm4).
 *
 * <p>Contrato de pilha (F1c): moldura 136 (≡8 mod 16), todo {@code call}
 * de C sai com rsp ≡ 0.
 */
public final class RuntimeOrmMysqlSave {

    private RuntimeOrmMysqlSave() {}

    public static void emit(StringBuilder sb) {
        sb.append("""
            # ---------------------------------------------------------------
            # F2d5: kof_orm_save mysql -> .Lorm_save_my(id*,obj*,table*,
            #   schema*) -> rax=obj* (nova instancia no INSERT gerado)
            #   slots: 0 id | 8 obj | 16 table | 24 schema | 32 ftab |
            #          40 nFields | 48 pkIndex | 56 pkType | 64 pkVal |
            #          72 skipPk | 80 first | 88 i | 96 entry |
            #          104 retNew | 112 genId
            # ---------------------------------------------------------------
            .Lorm_save_my:
                pushq %rbp
                movq %rsp, %rbp
                andq $-16, %rsp
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                subq $136, %rsp
                movq %rdi, 0(%rsp)
                movq %rsi, 8(%rsp)
                movq %rdx, 16(%rsp)
                movq %rcx, 24(%rsp)
                movq 24(%rsp), %rdi
                call kof_orm_parse_schema
                movq %rax, 32(%rsp)          # ftab
                movq %rcx, 40(%rsp)          # nFields
                movq %r8, 48(%rsp)           # pkIndex
            # ---- pkType + pkVal + decisao (espelho do Orm4) --------------
                movq 48(%rsp), %rax
                shlq $5, %rax
                addq 32(%rsp), %rax
                movq %rax, 96(%rsp)          # pkEntry
                movl 12(%rax), %ecx
                movl %ecx, 56(%rsp)          # pkType
                movq 48(%rsp), %rax
                movq 8(%rsp), %rdx
                leaq 16(%rdx,%rax,8), %rdx   # slot da pk
                movl 56(%rsp), %ecx
                cmpl $2, %ecx
                je .Lsm_pk_str
                cmpl $3, %ecx
                je .Lsm_pk_bool
                cmpl $5, %ecx
                je .Lsm_pk_flt
                cmpl $4, %ecx
                je .Lsm_pk_dbl
                cmpl $1, %ecx
                je .Lsm_pk_lng
                movslq (%rdx), %rax          # int
                jmp .Lsm_pk_have
            .Lsm_pk_lng:
                movq (%rdx), %rax
                jmp .Lsm_pk_have
            .Lsm_pk_dbl:
                movsd (%rdx), %xmm0
                cvttsd2si %xmm0, %rax
                jmp .Lsm_pk_sat
            .Lsm_pk_flt:
                movss (%rdx), %xmm0
                cvttss2si %xmm0, %rax
            .Lsm_pk_sat:
                movabsq $-9223372036854775808, %rcx
                cmpq %rcx, %rax
                jne .Lsm_pk_have
                xorl %eax, %eax              # NaN -> 0 (host longValue)
            .Lsm_pk_have:
                movq %rax, 64(%rsp)
                testq %rax, %rax
                jz .Lsm_insert_gen
                jmp .Lsm_update
            .Lsm_pk_str:
                movq (%rdx), %rax            # null -> INSERT gerado
                movq %rax, 64(%rsp)
                testq %rax, %rax
                jz .Lsm_insert_gen
                jmp .Lsm_update
            .Lsm_pk_bool:
                movq (%rdx), %rax            # bool: UPDATE sempre (host)
                movq %rax, 64(%rsp)
                jmp .Lsm_update
            .Lsm_insert_gen:
                movq $1, 72(%rsp)            # skipPk
                movq $1, 104(%rsp)           # retNew
                jmp .Lsm_insert
            # ---- estimativa do builder (table*2 + 512 + nFields*48 +
            #      sum(nameLen*2)): .Lsm_est(rdi=table, rsi=ftab,
            #      rdx=nFields) -> rax (helper: por registrador, os slots
            #      do chamador deslocam apos o call) -----------------------
            .Lsm_est:
                movq $512, %rax
                movl 16(%rdi), %r8d
                addq %r8, %rax
                addq %r8, %rax
                movq %rdx, %rcx
                imulq $48, %rcx, %rcx
                addq %rcx, %rax
                movq $0, %rcx
            .Lsm_est_loop:
                cmpq %rdx, %rcx
                jge .Lsm_est_done
                movl 8(%rsi), %r8d
                addq %r8, %rax
                addq %r8, %rax
                addq $32, %rsi
                incq %rcx
                jmp .Lsm_est_loop
            .Lsm_est_done:
                ret
            # ---- UPDATE --------------------------------------------------
            .Lsm_update:
                movq $0, 72(%rsp)            # skipPk
                movq $0, 104(%rsp)           # retNew (upsert devolve obj)
                movq 16(%rsp), %rdi
                movq 32(%rsp), %rsi
                movq 40(%rsp), %rdx
                call .Lsm_est
                movl %eax, %edi
                call .Lorm_bbegin
                leaq .Lsm_upd(%rip), %rsi
                movl $7, %ecx
                call .Lorm_bp
                movq 16(%rsp), %rax
                leaq 24(%rax), %rsi
                movl 16(%rax), %ecx
                call .Lorm_sa_qp             # `table`
                leaq .Lsm_set(%rip), %rsi
                movl $5, %ecx
                call .Lorm_bp
                movq $0, 88(%rsp)
                movq $0, 80(%rsp)
            .Lsm_upd_col:
                movq 88(%rsp), %rax
                cmpq 40(%rsp), %rax
                jge .Lsm_upd_col_end
                cmpq 48(%rsp), %rax
                je .Lsm_upd_col_next
                cmpq $0, 80(%rsp)
                jne .Lsm_upd_col_c2
                movq $1, 80(%rsp)
                jmp .Lsm_upd_col_q
            .Lsm_upd_col_c2:
                leaq .Lsm_cs(%rip), %rsi
                movl $2, %ecx
                call .Lorm_bp
            .Lsm_upd_col_q:
                movq 88(%rsp), %rax
                shlq $5, %rax
                addq 32(%rsp), %rax
                movq %rax, 96(%rsp)
                movq %rax, %rdi
                call .Lorm_sa_qn
                leaq .Lsm_eq(%rip), %rsi
                movl $3, %ecx
                call .Lorm_bp
                movq 96(%rsp), %rdi
                movq 8(%rsp), %rsi
                movq 88(%rsp), %rax
                leaq 16(%rsi,%rax,8), %rsi
                call .Lorm_sa_lit
            .Lsm_upd_col_next:
                incq 88(%rsp)
                jmp .Lsm_upd_col
            .Lsm_upd_col_end:
                leaq .Lsm_where(%rip), %rsi
                movl $7, %ecx
                call .Lorm_bp
                movq 48(%rsp), %rax
                shlq $5, %rax
                addq 32(%rsp), %rax
                movq %rax, 96(%rsp)
                movq %rax, %rdi
                call .Lorm_sa_qn
                leaq .Lsm_eq(%rip), %rsi
                movl $3, %ecx
                call .Lorm_bp
                movq 96(%rsp), %rdi
                movq 8(%rsp), %rsi
                movq 48(%rsp), %rax
                leaq 16(%rsi,%rax,8), %rsi
                call .Lorm_sa_lit
                call .Lorm_bfin
                movq %rbx, %rsi
                movq 0(%rsp), %rdi
                call .Lorm_sa_exec
                testl %eax, %eax
                jle .Lsm_upd_miss
                jmp .Lsm_ret_obj
            .Lsm_upd_miss:
                movq $0, 72(%rsp)            # INSERT de TODAS as colunas
                movq $0, 104(%rsp)
                jmp .Lsm_insert
            # ---- INSERT (skipPk=1: sem a PK) / upsert (skipPk=0) ---------
            .Lsm_insert:
                movq 16(%rsp), %rdi
                movq 32(%rsp), %rsi
                movq 40(%rsp), %rdx
                call .Lsm_est
                movl %eax, %edi
                call .Lorm_bbegin
                leaq .Lsm_ins(%rip), %rsi
                movl $12, %ecx
                call .Lorm_bp
                movq 16(%rsp), %rax
                leaq 24(%rax), %rsi
                movl 16(%rax), %ecx
                call .Lorm_sa_qp
                leaq .Lsm_opn(%rip), %rsi
                movl $2, %ecx
                call .Lorm_bp
                movq $0, 88(%rsp)
                movq $0, 80(%rsp)
            .Lsm_ins_col:
                movq 88(%rsp), %rax
                cmpq 40(%rsp), %rax
                jge .Lsm_ins_col_end
                cmpq $0, 72(%rsp)
                je .Lsm_ins_col_go
                cmpq 48(%rsp), %rax
                je .Lsm_ins_col_next
            .Lsm_ins_col_go:
                cmpq $0, 80(%rsp)
                jne .Lsm_ins_col_c2
                movq $1, 80(%rsp)
                jmp .Lsm_ins_col_q
            .Lsm_ins_col_c2:
                leaq .Lsm_cs(%rip), %rsi
                movl $2, %ecx
                call .Lorm_bp
            .Lsm_ins_col_q:
                movq 88(%rsp), %rax
                shlq $5, %rax
                addq 32(%rsp), %rax
                movq %rax, %rdi
                call .Lorm_sa_qn
            .Lsm_ins_col_next:
                incq 88(%rsp)
                jmp .Lsm_ins_col
            .Lsm_ins_col_end:
                leaq .Lsm_val(%rip), %rsi
                movl $10, %ecx
                call .Lorm_bp
                movq $0, 88(%rsp)
                movq $0, 80(%rsp)
            .Lsm_ins_mk:
                movq 88(%rsp), %rax
                cmpq 40(%rsp), %rax
                jge .Lsm_ins_mk_end
                cmpq $0, 72(%rsp)
                je .Lsm_ins_mk_go
                cmpq 48(%rsp), %rax
                je .Lsm_ins_mk_next
            .Lsm_ins_mk_go:
                cmpq $0, 80(%rsp)
                jne .Lsm_ins_mk_c2
                movq $1, 80(%rsp)
                jmp .Lsm_ins_mk_lit
            .Lsm_ins_mk_c2:
                leaq .Lsm_cs(%rip), %rsi
                movl $2, %ecx
                call .Lorm_bp
            .Lsm_ins_mk_lit:
                movq 88(%rsp), %rax
                shlq $5, %rax
                movq 32(%rsp), %rdi
                addq %rax, %rdi
                movq 8(%rsp), %rsi
                movq 88(%rsp), %rax
                leaq 16(%rsi,%rax,8), %rsi
                call .Lorm_sa_lit
            .Lsm_ins_mk_next:
                incq 88(%rsp)
                jmp .Lsm_ins_mk
            .Lsm_ins_mk_end:
                leaq .Lsm_cp(%rip), %rsi
                movl $1, %ecx
                call .Lorm_bp
                call .Lorm_bfin
                movq %rbx, %rsi
                movq 0(%rsp), %rdi
                call .Lorm_sa_exec
                cmpq $0, 104(%rsp)
                je .Lsm_ret_obj
            # ---- pk gerada: LAST_INSERT_ID + nova instancia --------------
                movq 0(%rsp), %rdi
                call .Lorm_sm_lastid
                movq %rax, 112(%rsp)
                movq 40(%rsp), %rax
                shlq $3, %rax
                addq $16, %rax
                addq $15, %rax
                andq $-16, %rax
                movq %rax, %rdi
                call kof_alloc
                movq %rax, %r8               # dst
                movq 8(%rsp), %rdx           # src
                movq %r8, %rdi
                movl 0(%rdx), %esi
                movq 8(%rdx), %rdx           # vtable
                call kof_init_object
                movq 8(%rsp), %rsi
                leaq 16(%rsi), %rsi
                leaq 16(%r8), %rdi
                movq 40(%rsp), %rcx
                rep movsq                    # slots 8B na ordem do schema
                movq 48(%rsp), %rax
                shlq $3, %rax
                addq $16, %rax
                movq 112(%rsp), %rcx
                movq %rcx, (%r8,%rax)        # patch da pk
                movq %r8, %rax
                jmp .Lsm_ret
            .Lsm_ret_obj:
                movq 8(%rsp), %rax
            .Lsm_ret:
                addq $136, %rsp
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                movq %rbp, %rsp
                popq %rbp
                ret

            # ---------------------- literais --------------------------------
            .Lsm_upd:
                .ascii "UPDATE "
            .Lsm_set:
                .ascii " SET "
            .Lsm_where:
                .ascii " WHERE "
            .Lsm_eq:
                .ascii " = "
            .Lsm_cs:
                .ascii ", "
            .Lsm_ins:
                .ascii "INSERT INTO "
            .Lsm_opn:
                .ascii " ("
            .Lsm_val:
                .ascii ") VALUES ("
            .Lsm_cp:
                .ascii ")"
            """);
    }
}
