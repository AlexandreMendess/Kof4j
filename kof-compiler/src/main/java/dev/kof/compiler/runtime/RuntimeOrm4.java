package dev.kof.compiler.runtime;

/**
 * Fatia ORM4 do runtime Native (x86-64): {@code kof_orm_save} (F2a — a
 * primeira fatia row-object: persiste um record da entidade).
 *
 * <p>Espelha {@code JvmOrmRuntime.kof_orm_save} nas 3 saídas medidas:
 * <ol>
 * <li><b>PK nula/0</b> → {@code INSERT INTO "t" ("f1", ...) VALUES (?, ...)}
 *     SEM a coluna PK + generated key via {@code SELECT last_insert_rowid()}
 *     (o que o {@code getGeneratedKeys} do sqlite-jdbc dá ao host) e devolve
 *     uma NOVA instância do record com a PK patchada ({@code kof_alloc} +
 *     {@code kof_init_object} com typeId/vtable da fonte + {@code rep movsq}
 *     dos slots 8B — layout ClassLayout 16+8i);</li>
 * <li><b>PK != 0</b> → {@code UPDATE "t" SET "f" = ?, ... WHERE "pk" = ?}
 *     (PK bindada por ÚLTIMO, como o host); {@code sqlite3_changes(conn) > 0}
 *     → devolve o MESMO ponteiro;</li>
 * <li><b>UPDATE 0 linhas</b> → INSERT de TODAS as colunas (upsert-like) e
 *     devolve o mesmo ponteiro.</li>
 * </ol>
 *
 * <p>Schema parseado por {@code kof_orm_parse_schema}
 * ({@code RuntimeOrmSchema}); binds por {@code .Lorm_bfld}
 * ({@code RuntimeOrmBind}). Decisão da PK: int/long == 0, double/float
 * truncado == 0 (NaN vira 0 como o {@code longValue()} do host; ±inf
 * diverge — documentado), String null; bool NUNCA vai ao INSERT (o host
 * trata Boolean como não-numérico → UPDATE/upsert).
 *
 * <p>Falha de prepare/step lança {@code "sqlite: " + sqlite3_errmsg} (o host
 * lança a SQLException — texto do driver diverge, semântica de throw é a
 * mesma; R6 — nunca retorno silencioso). MySQL segue
 * {@code .Lorm_mysql_pending} honesto via {@code .Lorm_conn}.
 *
 * <p>Reaproveita {@code RuntimeOrm1} ({@code .Lorm_bbegin/.Lorm_bp/.Lorm_bh/
 * .Lorm_bfin/.Lorm_conn/.Lorm_bad_conn/.Lorm_mysql_pending}), Orm2
 * ({@code .Lorm2_qq}), Schema e Bind. Contrato de pilha (F1c): {@code andq}
 * no prólogo, moldura 152 (≡8 antes do subq), todo {@code call} de C sai com
 * rsp ≡ 0; epílogo espelha delete_all (pops na região andada, rbp só no fim).
 */
public final class RuntimeOrm4 {

    private RuntimeOrm4() {}

    public static void emit(StringBuilder sb) {
        sb.append("""
            # ---------------------------------------------------------------
            # kof_orm_save(id*, obj*, table*, schema*) -> obj* (rax)
            #   slots: 0 id | 8 obj | 16 table | 24 schema | 32 conn |
            #   40 ftab | 56 nFields | 64 pkIndex | 72 pkType | 80 pkVal |
            #   88 rowid | 96 stmt | 104 paramIdx | 112 i | 128 first |
            #   136 insFlag | 144 changes
            # ---------------------------------------------------------------
                .globl kof_orm_save
                .type kof_orm_save, @function
            kof_orm_save:
                pushq %rbp
                movq %rsp, %rbp
                andq $-16, %rsp
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                subq $152, %rsp
                movq %rdi, (%rsp)
                movq %rsi, 8(%rsp)
                movq %rdx, 16(%rsp)
                movq %rcx, 24(%rsp)
                movq (%rsp), %rdi
                call kof_db_type
                cmpl $2, %eax
                je .Lorm4_my_dispatch
                movq (%rsp), %rdi
                call .Lorm_conn
                movq %rax, 32(%rsp)
                movq 24(%rsp), %rdi
                call kof_orm_parse_schema
                movq %rax, 40(%rsp)          # ftab
                movq %rcx, 56(%rsp)          # nFields
                movq %r8, 64(%rsp)           # pkIndex
            # ---- pkType + pkVal + decisao do path ------------------------
                movq 64(%rsp), %rcx
                shlq $5, %rcx
                addq 40(%rsp), %rcx
                movl 12(%rcx), %ecx
                movl %ecx, 72(%rsp)          # pkType
                movq 64(%rsp), %rax
                movq 8(%rsp), %rdx
                leaq 16(%rdx,%rax,8), %rdx   # slot da pk
                movl 72(%rsp), %ecx
                cmpl $2, %ecx
                je .Lorm4_pk_str
                cmpl $3, %ecx
                je .Lorm4_pk_bool
                cmpl $5, %ecx
                je .Lorm4_pk_flt
                cmpl $4, %ecx
                je .Lorm4_pk_dbl
                cmpl $1, %ecx
                je .Lorm4_pk_lng
                movslq (%rdx), %rax          # int
                jmp .Lorm4_pk_have
            .Lorm4_pk_lng:
                movq (%rdx), %rax
                jmp .Lorm4_pk_have
            .Lorm4_pk_dbl:
                movsd (%rdx), %xmm0
                cvttsd2si %xmm0, %rax
                jmp .Lorm4_pk_sat
            .Lorm4_pk_flt:
                movss (%rdx), %xmm0
                cvttss2si %xmm0, %rax
            .Lorm4_pk_sat:
                movabsq $-9223372036854775808, %rcx
                cmpq %rcx, %rax
                jne .Lorm4_pk_have
                xorl %eax, %eax              # NaN -> 0 (host longValue);
                                             #   ±inf diverge (documentado)
            .Lorm4_pk_have:
                movq %rax, 80(%rsp)
                testq %rax, %rax
                jz .Lorm4_path_insert
                jmp .Lorm4_path_update
            .Lorm4_pk_str:
                movq (%rdx), %rax            # null -> INSERT
                jmp .Lorm4_pk_have
            .Lorm4_pk_bool:
                movq (%rdx), %rax            # bool: UPDATE sempre (host)
                movq %rax, 80(%rsp)
                jmp .Lorm4_path_update

            # ---- path INSERT (C: pk skip + nova instancia) / upsert (E) ---
            .Lorm4_path_insert:
                movq $0, 136(%rsp)           # devolve NOVA instancia
                jmp .Lorm4_ins_go
            .Lorm4_path_upsert:              # UPDATE 0 linhas: INSERT de tudo
                movq $-1, 64(%rsp)           # nao pular a pk
                movq $1, 136(%rsp)           # devolve a MESMA instancia
            .Lorm4_ins_go:
                movq 16(%rsp), %rax
                movl 16(%rax), %eax          # tblLen
                movq 24(%rsp), %rcx
                movl 16(%rcx), %ecx          # schLen
                leal (%rax,%rcx,2), %edi
                addl $256, %edi
                call .Lorm_bbegin
                leaq .Lorm4_i1(%rip), %rsi
                movl $12, %ecx
                call .Lorm_bp
                movq 16(%rsp), %rdi
                call .Lorm2_qq               # "table"
                leaq .Lorm4_i2(%rip), %rsi
                movl $2, %ecx
                call .Lorm_bp
                movq $0, 128(%rsp)           # first
                movq $0, 112(%rsp)           # i
            .Lorm4_ins_col:
                movq 112(%rsp), %rax
                cmpq 56(%rsp), %rax
                jge .Lorm4_ins_col_end
                cmpq 64(%rsp), %rax
                je .Lorm4_ins_col_skip
                cmpq $0, 128(%rsp)
                jne .Lorm4_ins_col_c2
                movq $1, 128(%rsp)
                jmp .Lorm4_ins_col_q
            .Lorm4_ins_col_c2:
                leaq .Lorm4_cs(%rip), %rsi
                movl $2, %ecx
                call .Lorm_bp
            .Lorm4_ins_col_q:
                movq 112(%rsp), %rax
                shlq $5, %rax
                addq 40(%rsp), %rax
                movq 0(%rax), %rsi
                movl 8(%rax), %ecx
                call .Lorm_qraw
            .Lorm4_ins_col_skip:
                incq 112(%rsp)
                jmp .Lorm4_ins_col
            .Lorm4_ins_col_end:
                leaq .Lorm4_i3(%rip), %rsi
                movl $10, %ecx
                call .Lorm_bp
                movq $0, 128(%rsp)
                movq $0, 112(%rsp)
            .Lorm4_ins_mk:
                movq 112(%rsp), %rax
                cmpq 56(%rsp), %rax
                jge .Lorm4_ins_mk_end
                cmpq 64(%rsp), %rax
                je .Lorm4_ins_mk_skip
                cmpq $0, 128(%rsp)
                jne .Lorm4_ins_mk_c2
                movq $1, 128(%rsp)
                leaq .Lorm4_q1(%rip), %rsi
                movl $1, %ecx
                call .Lorm_bp
                jmp .Lorm4_ins_mk_skip
            .Lorm4_ins_mk_c2:
                leaq .Lorm4_cq(%rip), %rsi
                movl $3, %ecx
                call .Lorm_bp
            .Lorm4_ins_mk_skip:
                incq 112(%rsp)
                jmp .Lorm4_ins_mk
            .Lorm4_ins_mk_end:
                movl $41, %r8d               # ')'
                call .Lorm_bh
                call .Lorm_bfin
                movq 32(%rsp), %rdi
                leaq 24(%rbx), %rsi
                movq $-1, %rdx
                leaq 96(%rsp), %rcx
                xorl %r8d, %r8d
                call sqlite3_prepare_v2
                movq 96(%rsp), %r12
                testq %r12, %r12
                jz .Lorm4_prep_fail
                movq $1, 104(%rsp)
                movq $0, 112(%rsp)
            .Lorm4_ins_bind:
                movq 112(%rsp), %rax
                cmpq 56(%rsp), %rax
                jge .Lorm4_ins_bind_end
                cmpq 64(%rsp), %rax
                je .Lorm4_ins_bind_skip
                movq 112(%rsp), %rax
                shlq $5, %rax
                addq 40(%rsp), %rax
                movl 12(%rax), %ecx          # typeCode
                movq 112(%rsp), %r8
                movq 8(%rsp), %rdx
                leaq 16(%rdx,%r8,8), %rdx    # slot do campo
                movq 96(%rsp), %rdi
                movl 104(%rsp), %esi
                call .Lorm_bfld
                incq 104(%rsp)
            .Lorm4_ins_bind_skip:
                incq 112(%rsp)
                jmp .Lorm4_ins_bind
            .Lorm4_ins_bind_end:
                movq 96(%rsp), %rdi
                call sqlite3_step
                cmpl $101, %eax              # SQLITE_DONE
                jne .Lorm4_sql_fail
                movq 96(%rsp), %rdi
                call sqlite3_finalize
                cmpq $0, 136(%rsp)
                jne .Lorm4_ret_obj           # upsert: mesma instancia
                # generated key = last_insert_rowid
                movq 32(%rsp), %rdi
                leaq .Lorm4_r1(%rip), %rsi
                movq $-1, %rdx
                leaq 96(%rsp), %rcx
                xorl %r8d, %r8d
                call sqlite3_prepare_v2
                movq 96(%rsp), %r12
                testq %r12, %r12
                jz .Lorm4_rowid0
                movq %r12, %rdi
                call sqlite3_step
                cmpl $100, %eax              # SQLITE_ROW
                jne .Lorm4_rowid_fin
                movq %r12, %rdi
                xorl %esi, %esi
                call sqlite3_column_int64
                movq %rax, 88(%rsp)
            .Lorm4_rowid_fin:
                movq %r12, %rdi
                call sqlite3_finalize
                jmp .Lorm4_rowid_have
            .Lorm4_rowid0:
                movq $0, 88(%rsp)            # keys.next()==false no host
            .Lorm4_rowid_have:
                # nova instancia: alloc + header da fonte + slots + pk patch
                movq 56(%rsp), %rax
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
                movq 56(%rsp), %rcx
                rep movsq                    # slots 8B na ordem do schema
                movq 64(%rsp), %rax
                shlq $3, %rax
                addq $16, %rax
                movq 88(%rsp), %rcx
                movq %rcx, (%r8,%rax)        # patch da pk
                movq %r8, %rax
                jmp .Lorm4_ret
            .Lorm4_ret_obj:
                movq 8(%rsp), %rax
                jmp .Lorm4_ret

            # ---- path UPDATE (D) ------------------------------------------
            .Lorm4_path_update:
                movq 16(%rsp), %rax
                movl 16(%rax), %eax
                movq 24(%rsp), %rcx
                movl 16(%rcx), %ecx
                leal (%rax,%rcx,2), %edi
                addl $256, %edi
                call .Lorm_bbegin
                leaq .Lorm4_u1(%rip), %rsi
                movl $7, %ecx
                call .Lorm_bp
                movq 16(%rsp), %rdi
                call .Lorm2_qq
                leaq .Lorm4_u2(%rip), %rsi
                movl $5, %ecx
                call .Lorm_bp
                movq $0, 128(%rsp)
                movq $0, 112(%rsp)
            .Lorm4_upd_col:
                movq 112(%rsp), %rax
                cmpq 56(%rsp), %rax
                jge .Lorm4_upd_col_end
                cmpq 64(%rsp), %rax
                je .Lorm4_upd_col_skip
                cmpq $0, 128(%rsp)
                jne .Lorm4_upd_col_c2
                movq $1, 128(%rsp)
                jmp .Lorm4_upd_col_q
            .Lorm4_upd_col_c2:
                leaq .Lorm4_cs(%rip), %rsi
                movl $2, %ecx
                call .Lorm_bp
            .Lorm4_upd_col_q:
                movq 112(%rsp), %rax
                shlq $5, %rax
                addq 40(%rsp), %rax
                movq 0(%rax), %rsi
                movl 8(%rax), %ecx
                call .Lorm_qraw
                leaq .Lorm4_eq(%rip), %rsi
                movl $4, %ecx
                call .Lorm_bp
            .Lorm4_upd_col_skip:
                incq 112(%rsp)
                jmp .Lorm4_upd_col
            .Lorm4_upd_col_end:
                leaq .Lorm4_u3(%rip), %rsi
                movl $7, %ecx
                call .Lorm_bp
                movq 64(%rsp), %rax          # "pk" = ?
                shlq $5, %rax
                addq 40(%rsp), %rax
                movq 0(%rax), %rsi
                movl 8(%rax), %ecx
                call .Lorm_qraw
                leaq .Lorm4_eq(%rip), %rsi
                movl $4, %ecx
                call .Lorm_bp
                call .Lorm_bfin
                movq 32(%rsp), %rdi
                leaq 24(%rbx), %rsi
                movq $-1, %rdx
                leaq 96(%rsp), %rcx
                xorl %r8d, %r8d
                call sqlite3_prepare_v2
                movq 96(%rsp), %r12
                testq %r12, %r12
                jz .Lorm4_prep_fail
                movq $1, 104(%rsp)
                movq $0, 112(%rsp)
            .Lorm4_upd_bind:
                movq 112(%rsp), %rax
                cmpq 56(%rsp), %rax
                jge .Lorm4_upd_bind_pk
                cmpq 64(%rsp), %rax
                je .Lorm4_upd_bind_skip
                movq 112(%rsp), %rax
                shlq $5, %rax
                addq 40(%rsp), %rax
                movl 12(%rax), %ecx
                movq 112(%rsp), %r8
                movq 8(%rsp), %rdx
                leaq 16(%rdx,%r8,8), %rdx
                movq 96(%rsp), %rdi
                movl 104(%rsp), %esi
                call .Lorm_bfld
                incq 104(%rsp)
            .Lorm4_upd_bind_skip:
                incq 112(%rsp)
                jmp .Lorm4_upd_bind
            .Lorm4_upd_bind_pk:              # pk por ULTIMO, como o host
                movq 64(%rsp), %rax
                shlq $5, %rax
                addq 40(%rsp), %rax
                movl 12(%rax), %ecx
                movq 64(%rsp), %rax
                shlq $3, %rax
                addq $16, %rax
                addq 8(%rsp), %rax
                movq %rax, %rdx
                movq 96(%rsp), %rdi
                movl 104(%rsp), %esi
                call .Lorm_bfld
                movq 96(%rsp), %rdi
                call sqlite3_step
                cmpl $101, %eax
                jne .Lorm4_sql_fail
                movq 32(%rsp), %rdi
                call sqlite3_changes
                movq %rax, 144(%rsp)
                movq 96(%rsp), %rdi
                call sqlite3_finalize
                cmpq $0, 144(%rsp)
                jg .Lorm4_ret_obj            # hit: MESMA instancia
                jmp .Lorm4_path_upsert       # miss: INSERT-all (upsert)

            # ---- fail: throw "sqlite: " + errmsg --------------------------
            .Lorm4_sql_fail:
                movq %r12, %rdi
                call sqlite3_finalize
            .Lorm4_prep_fail:
                movl $512, %edi
                call .Lorm_bbegin
                leaq .Lorm4_pre(%rip), %rsi
                movl $8, %ecx
                call .Lorm_bp
                movq 32(%rsp), %rdi
                call sqlite3_errmsg
                testq %rax, %rax
                jz .Lorm4_pfin
                movq %rax, %rsi
                call .Lorm_pcstr
            .Lorm4_pfin:
                call .Lorm_bfin
                movq %rbx, %rdi
                call kof_throw_string
                ud2

            .Lorm4_ret:
                addq $152, %rsp
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                movq %rbp, %rsp
                popq %rbp
                ret

            # ---------------------- literais --------------------------------
            .Lorm4_i1:
                .ascii "INSERT INTO "
            .Lorm4_i2:
                .ascii " ("
            .Lorm4_i3:
                .ascii ") VALUES ("
            .Lorm4_u1:
                .ascii "UPDATE "
            .Lorm4_u2:
                .ascii " SET "
            .Lorm4_u3:
                .ascii " WHERE "
            .Lorm4_eq:
                .ascii " = ?"
            .Lorm4_cs:
                .ascii ", "
            .Lorm4_cq:
                .ascii ", ?"
            .Lorm4_q1:
                .ascii "?"
            .Lorm4_r1:
                .asciz "SELECT last_insert_rowid()"
            .Lorm4_pre:
                .ascii "sqlite: "

            # F2d5: mysql -> restaura o frame e tail-chama .Lorm_save_my
            # (args originais nos slots; a asm mysql vive no
            # RuntimeOrmMysqlSave, emitido junto no mesmo .s).
            .Lorm4_my_dispatch:
                movq 0(%rsp), %rdi
                movq 8(%rsp), %rsi
                movq 16(%rsp), %rdx
                movq 24(%rsp), %rcx
                addq $152, %rsp
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                movq %rbp, %rsp
                popq %rbp
                jmp .Lorm_save_my
            """);
    }
}
