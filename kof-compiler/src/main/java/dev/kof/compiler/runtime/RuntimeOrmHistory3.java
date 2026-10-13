package dev.kof.compiler.runtime;

/**
 * kof.orm history no Native x86-64 — Fase 5 fatia 5c do entity-history-plan
 * (D-ENTITY-HISTORY; ordem da mantenedora 13/10 "ponta a ponta"):
 * {@code kof_orm_save_recorded} — o save gravando a revisão
 * (espelha o host JVM {@code JvmOrmHistoryRuntime.kof_orm_save_recorded}):
 * a detecção da operação (EXISTS pré-DML: generated → INSERT, existente →
 * UPDATE), o save via o {@code kof_orm_save} existente (o pk generated
 * volta no objeto), o payload JSON por campo (os nomes do ftab, os valores
 * por typeCode — os escapes `"` e `\\` nas strings), a rev
 * COALESCE(MAX)+1, o INSERT de 8 valores (at = kof_time_now; o actor
 * honesto (null,'system')). SQLite only: MySQL cai no {@code .Lorm_conn}
 * honesto (ORM001).
 *
 * <p>O frame ALINHADO 88 (a lição do ciclo 65: 40 de pushes + 88 = 128 ≡ 0
 * mod 16 — os calls C com a pilha desalinhada crasham nos ops SSE do
 * sqlite). LAYOUT: 0 id | 8 obj | 16 table | 24 schema | 32 conn | 40 stmt
 * | 48 ftab | 56 nFields | 64 pkIndex | 72 pkEntry | 80 pks | 88 rev |
 * 96 ok | 104 opIsUpdate | 112 i | 120 rax-tmp | 128 comma | 136 pkSlot |
 * 144 pkType | 152 valSlot.
 */
public final class RuntimeOrmHistory3 {

    private RuntimeOrmHistory3() {}

    public static void emit(StringBuilder sb) {
        String b = """
            # kof_orm_save_recorded(id*, obj*, table*, schema*) -> obj*
            #   slots: 0 id | 8 obj | 16 table | 24 schema | 32 conn |
            #   40 stmt | 48 ftab | 56 nFields | 64 pkIndex | 72 pkEntry |
            #   80 pks | 88 rev | 96 ok | 104 opIsUpdate | 112 i | 120 tmp |
            #   128 comma | 136 pkSlot | 144 pkType | 152 valSlot
            # ---------------------------------------------------------------
                @@SYMBOL@@
            kof_orm_save_recorded:
                pushq %rbp
                movq %rsp, %rbp
                andq $-16, %rsp
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                subq $160, %rsp
                movq %rdi, (%rsp)
                movq %rsi, 8(%rsp)
                movq %rdx, 16(%rsp)
                movq %rcx, 24(%rsp)
            @@CONN@@
                movq 24(%rsp), %rdi
                call kof_orm_parse_schema
                movq %rax, 48(%rsp)          # ftab
                movq %rcx, 56(%rsp)          # nFields
                movq %r8, 64(%rsp)           # pkIndex
                # ---- pkVal pré-DML + a detecção da operação ----------------
                movq 64(%rsp), %rax
                movq %rax, 72(%rsp)          # pkEntry
                movq 8(%rsp), %rdx
                movq 72(%rsp), %rax
                movl 12(%rax), %ecx          # pkType
                movq %rcx, 144(%rsp)
                movq 72(%rsp), %rax
                shlq $5, %rax
                addq 48(%rsp), %rax
                movl 12(%rax), %ecx          # pkType (do ftab)
                movq %rcx, 144(%rsp)
                movq 8(%rsp), %rdx
                movq 72(%rsp), %rax
                movl 12(%rax), %ecx
                movslq %ecx, %rcx
                leaq 16(%rdx,%rcx,8), %rdx
                movq %rdx, 136(%rsp)         # pkSlot
                movq 136(%rsp), %rdx
                movq 144(%rsp), %rcx
                cmpl $0, %ecx
                je .Lormh3_pki
                movq (%rdx), %rax
                jmp .Lormh3_pkst
            .Lormh3_pki:
                movslq (%rdx), %rax
            .Lormh3_pkst:
                movq %rax, 136(%rsp)
                # ---- EXISTS: SELECT EXISTS(SELECT 1 FROM "t" WHERE pk = ?) --
                movq 16(%rsp), %rax
                movl 16(%rax), %edi
                addl $180, %edi
                call .Lorm_bbegin
                leaq .Lormh3_ex1(%rip), %rsi
                movl $.Lormh3_ex1_len, %ecx
                call .Lorm_bp
                movl $34, %r8d
                call .Lorm_bh
                movq 16(%rsp), %rdi
                movl 16(%rdi), %r12d
                leaq 24(%rdi), %rsi
                movl %r12d, %ecx
                call .Lorm_bp
                movl $34, %r8d
                call .Lorm_bh
                # ---- "pkname" (a entrada da PK no ftab) ---------------------
                movl $34, %r8d
                call .Lorm_bh
                movq 72(%rsp), %rax
                movq 0(%rax), %rsi
                movl 8(%rax), %ecx
                call .Lorm_bp
                movl $34, %r8d
                call .Lorm_bh
                leaq .Lormh3_ex2(%rip), %rsi
                movl $.Lormh3_ex2_len, %ecx
                call .Lorm_bp
                call .Lorm_bfin
                movq 32(%rsp), %rdi
                leaq 24(%rbx), %rsi
                movq $-1, %rdx
                leaq 40(%rsp), %rcx
                xorl %r8d, %r8d
                call sqlite3_prepare_v2
                movq 40(%rsp), %r12
                testq %r12, %r12
                jz .Lormh3_exskip
                movq %r12, %rdi
                movl $1, %esi
                movq 136(%rsp), %rdx
                call sqlite3_bind_int64
                movq %r12, %rdi
                call sqlite3_step
                movq %r12, %rdi
                movl $0, %esi
                call sqlite3_column_int64
                movq %rax, 104(%rsp)         # 1 = UPDATE (a linha existe)
                movq %r12, %rdi
                call sqlite3_finalize
                jmp .Lormh3_gosave
            .Lormh3_exskip:
                movq $0, 104(%rsp)
            .Lormh3_gosave:
                # ---- o DML: o kof_orm_save existente (o pk generated volta) -
                movq (%rsp), %rdi
                movq 8(%rsp), %rsi
                movq 16(%rsp), %rdx
                movq 24(%rsp), %rcx
                call kof_orm_save
                movq %rax, 8(%rsp)
                movq %rax, 96(%rsp)          # o retorno = o próprio obj
                # ---- o payload JSON: "{" por campo "name":val "}" -----------
                movl $256, %edi
                call kof_alloc
                movq %rax, %rbx
                movq %rax, %r14
                xorl %r15d, %r15d
                movb $123, (%r14)            # '{'
                incq %r14
                incl %r15d
                movq $0, 112(%rsp)           # i = 0
                movq $1, 128(%rsp)           # first = true
            .Lormh3_floop:
                movq 112(%rsp), %rax
                cmpq 56(%rsp), %rax
                jge .Lormh3_fend
                cmpq $0, 128(%rsp)
                jne .Lormh3_nocomma
                movb $44, (%r14)             # ','
                incq %r14
                incl %r15d
                movq $0, 128(%rsp)
            .Lormh3_nocomma:
                movb $34, (%r14)             # '"'
                incq %r14
                incl %r15d
                movq 112(%rsp), %rax
                shlq $5, %rax
                addq 48(%rsp), %rax
                movq 0(%rax), %rsi           # namePtr
                movl 8(%rax), %ecx           # nameLen
                call .Lorm_bp
                movb $34, (%r14)             # '"'
                incq %r14
                incl %r15d
                movb $58, (%r14)             # ':'
                incq %r14
                incl %r15d
                # ---- o valor por typeCode -----------------------------------
                movq 112(%rsp), %r8
                shlq $5, %r8
                addq 48(%rsp), %r8
                movl 12(%r8), %r12d          # typeCode
                movq 8(%rsp), %rdx
                movq 112(%rsp), %r8
                leaq 16(%rdx,%r8,8), %rdx
                movq %rdx, 152(%rsp)         # valSlot
                cmpl $0, %r12d
                je .Lormh3_vint
                cmpl $1, %r12d
                je .Lormh3_vlong
                cmpl $3, %r12d
                je .Lormh3_vbool
                cmpl $4, %r12d
                je .Lormh3_vdbl
                cmpl $5, %r12d
                je .Lormh3_vflt
                jmp .Lormh3_vstr
            .Lormh3_vint:
                movq 152(%rsp), %rdx
                movslq (%rdx), %rdi
                call kof_int_to_string
                jmp .Lormh3_vapp
            .Lormh3_vlong:
                movq 152(%rsp), %rdx
                movq (%rdx), %rdi
                call kof_long_to_string
                jmp .Lormh3_vapp
            .Lormh3_vbool:
                movq 152(%rsp), %rdx
                cmpq $0, (%rdx)
                je .Lormh3_vbf
                leaq .Lormh3_true(%rip), %rsi
                movl $4, %ecx
                call .Lorm_bp
                jmp .Lormh3_fnext
            .Lormh3_vbf:
                leaq .Lormh3_false(%rip), %rsi
                movl $5, %ecx
                call .Lorm_bp
                jmp .Lormh3_fnext
            .Lormh3_vdbl:
                movq 152(%rsp), %rdx
                movq (%rdx), %xmm0
                call kof_double_to_string
                jmp .Lormh3_vapp
            .Lormh3_vflt:
                movq 152(%rsp), %rdx
                movd (%rdx), %xmm0
                cvtss2sd %xmm0, %xmm0
                call kof_double_to_string
                jmp .Lormh3_vapp
            .Lormh3_vstr:
                movq 152(%rsp), %rdx
                movq (%rdx), %r13            # KofString* (NULL → null)
                testq %r13, %r13
                jnz .Lormh3_vsbody
                movb $110, (%r14)            # 'n'
                movb $117, 1(%r14)           # 'u'
                movb $108, 2(%r14)           # 'l'
                movb $108, 3(%r14)           # 'l'
                addq $4, %r14
                addl $4, %r15d
                jmp .Lormh3_fnext
            .Lormh3_vsbody:
                movb $34, (%r14)             # '"'
                incq %r14
                incl %r15d
                movq 24(%r13), %rsi          # body
                movl 16(%r13), %ecx          # len
            .Lormh3_vesc:
                testl %ecx, %ecx
                jz .Lormh3_vsend
                movzbl (%rsi), %edx
                cmpl $92, %edx               # '\\'
                je .Lormh3_vesc1
                cmpl $34, %edx               # '"'
                je .Lormh3_vesc2
                movb %dl, (%r14)
                incq %r14
                incl %r15d
                incq %rsi
                decl %ecx
                jmp .Lormh3_vesc
            .Lormh3_vesc1:
                movb $92, (%r14)             # '\\'
                movb %dl, 1(%r14)
                addq $2, %r14
                addl $2, %r15d
                incq %rsi
                decl %ecx
                jmp .Lormh3_vesc
            .Lormh3_vesc2:
                movb $92, (%r14)             # '\\'
                movb %dl, 1(%r14)
                addq $2, %r14
                addl $2, %r15d
                incq %rsi
                decl %ecx
                jmp .Lormh3_vesc
            .Lormh3_vsend:
                movb $34, (%r14)             # '"'
                incq %r14
                incl %r15d
                jmp .Lormh3_fnext
            .Lormh3_vapp:
                movl 16(%rax), %ecx          # KofString len
                leaq 24(%rax), %rsi          # body
                call .Lorm_bp
            .Lormh3_fnext:
                incq 112(%rsp)
                jmp .Lormh3_floop
            .Lormh3_fend:
                movb $125, (%r14)            # '}'
                incq %r14
                incl %r15d
                movb $0, (%r14)
                movl %r15d, 16(%rbx)
                movq %rbx, 104(%rsp)         # o payload → o slot do op
                                              # (104 reusado: o payload agora)
                # ---- rev = SELECT COALESCE(MAX(revision),0)+1 ... ----------
                movq 16(%rsp), %rax
                movl 16(%rax), %edi
                addl $220, %edi
                call .Lorm_bbegin
                leaq .Lormh3_revpre(%rip), %rsi
                movl $.Lormh3_revpre_len, %ecx
                call .Lorm_bp
                movl $34, %r8d
                call .Lorm_bh
                movq 16(%rsp), %rdi
                movl 16(%rdi), %r12d
                leaq 24(%rdi), %rsi
                movl %r12d, %ecx
                call .Lorm_bp
                leaq .Lormh3_suf(%rip), %rsi
                movl $.Lormh3_suf_len, %ecx
                call .Lorm_bp
                movl $34, %r8d
                call .Lorm_bh
                leaq .Lormh3_revwhere(%rip), %rsi
                movl $.Lormh3_revwhere_len, %ecx
                call .Lorm_bp
                call .Lorm_bfin
                movq 32(%rsp), %rdi
                leaq 24(%rbx), %rsi
                movq $-1, %rdx
                leaq 40(%rsp), %rcx
                xorl %r8d, %r8d
                call sqlite3_prepare_v2
                movq 40(%rsp), %r12
                testq %r12, %r12
                jz .Lormh3_done0
                movq %r12, %rdi
                movl $1, %esi
                movq 8(%rsp), %rdx
                movq 72(%rsp), %rax
                movl 12(%rax), %ecx
                movq 8(%rsp), %rdx
                movq 72(%rsp), %rax
                movl 12(%rax), %ecx
                movslq %ecx, %rcx
                movq 8(%rsp), %rdx
                movq 72(%rsp), %rax
                shlq $5, %rax
                addq 48(%rsp), %rax
                movq 72(%rsp), %r8
                movq 0(%r8), %rsi
                movl 8(%r8), %ecx
                movq $-1, %r8
                movq %r12, %rdi
                movl $1, %esi
                call sqlite3_bind_text
                movq %r12, %rdi
                call sqlite3_step
                movq %r12, %rdi
                xorl %esi, %esi
                call sqlite3_column_int64
                movq %rax, 88(%rsp)          # rev
                movq %r12, %rdi
                call sqlite3_finalize
                # ---- INSERT da linha (8 binds) ------------------------------
                movq 16(%rsp), %rax
                movl 16(%rax), %edi
                addl $340, %edi
                call .Lorm_bbegin
                leaq .Lormh3_inspre(%rip), %rsi
                movl $.Lormh3_inspre_len, %ecx
                call .Lorm_bp
                movl $34, %r8d
                call .Lorm_bh
                movq 16(%rsp), %rdi
                movl 16(%rdi), %r12d
                leaq 24(%rdi), %rsi
                movl %r12d, %ecx
                call .Lorm_bp
                leaq .Lormh3_suf(%rip), %rsi
                movl $.Lormh3_suf_len, %ecx
                call .Lorm_bp
                movl $34, %r8d
                call .Lorm_bh
                leaq .Lormh3_inssuf(%rip), %rsi
                movl $.Lormh3_inssuf_len, %ecx
                call .Lorm_bp
                call .Lorm_bfin
                movq 32(%rsp), %rdi
                leaq 24(%rbx), %rsi
                movq $-1, %rdx
                leaq 40(%rsp), %rcx
                xorl %r8d, %r8d
                call sqlite3_prepare_v2
                movq 40(%rsp), %r12
                testq %r12, %r12
                jz .Lormh3_done0
                movq %r12, %rdi
                movl $1, %esi
                movq 88(%rsp), %rdx
                call sqlite3_bind_int64
                movq %r12, %rdi
                movl $2, %esi
                movq 8(%rsp), %rdx
                movq 72(%rsp), %rax
                shlq $5, %rax
                addq 48(%rsp), %rax
                movq 0(%rax), %rdi
                call kof_box_to_string
                movq %rax, %rdx
                movq %r12, %rdi
                movl $2, %esi
                movq %rdx, %rdx
                leaq 24(%rdx), %rdx
                movl 16(%rdx), %ecx
                movslq %ecx, %rcx
                movq $-1, %r8
                call sqlite3_bind_text
                movq %r12, %rdi
                movl $3, %esi
                cmpq $0, 104(%rsp)
                je .Lormh3_opins
                leaq .Lormh3_upd(%rip), %rdx
                movl $.Lormh3_upd_len, %ecx
                call sqlite3_bind_text
                jmp .Lormh3_opdone
            .Lormh3_opins:
                leaq .Lormh3_ins(%rip), %rdx
                movl $.Lormh3_ins_len, %ecx
                call sqlite3_bind_text
            .Lormh3_opdone:
                call kof_time_now
                movq %r12, %rdi
                movl $4, %esi
                movq %rax, %rdx
                call sqlite3_bind_int64
                movq %r12, %rdi
                movl $5, %esi
                movq 104(%rsp), %rdx
                leaq 24(%rdx), %rdx
                movl 16(%rdx), %ecx
                movslq %ecx, %rcx
                movq $-1, %r8
                call sqlite3_bind_text
                movq %r12, %rdi
                movl $6, %esi
                call sqlite3_bind_null
                movq %r12, %rdi
                movl $7, %esi
                leaq .Lormh3_system(%rip), %rdx
                movl $.Lormh3_system_len, %ecx
                movq $-1, %r8
                call sqlite3_bind_text
                movq %r12, %rdi
                movl $8, %esi
                call sqlite3_bind_null
                movq %r12, %rdi
                call sqlite3_step
                movq %r12, %rdi
                call sqlite3_finalize
            .Lormh3_done:
                movq 8(%rsp), %rax
                addq $160, %rsp
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                movq %rbp, %rsp
                popq %rbp
                ret
            .Lormh3_done0:
                jmp .Lormh3_done

            .Lormh3_ex1:
                .ascii "SELECT EXISTS(SELECT 1 FROM "
                .set .Lormh3_ex1_len, . - .Lormh3_ex1
            .Lormh3_ex2:
                .ascii " WHERE "
                .set .Lormh3_ex2_len, . - .Lormh3_ex2
            .Lormh3_true:
                .ascii "true"
                .set .Lormh3_true_len, . - .Lormh3_true
            .Lormh3_false:
                .ascii "false"
                .set .Lormh3_false_len, . - .Lormh3_false
            .Lormh3_revpre:
                .ascii "SELECT COALESCE(MAX(revision), 0) + 1 FROM "
                .set .Lormh3_revpre_len, . - .Lormh3_revpre
            .Lormh3_suf:
                .ascii "_history"
                .set .Lormh3_suf_len, . - .Lormh3_suf
            .Lormh3_revwhere:
                .ascii " WHERE entity_pk = ?"
                .set .Lormh3_revwhere_len, . - .Lormh3_revwhere
            .Lormh3_inspre:
                .ascii "INSERT INTO "
                .set .Lormh3_inspre_len, . - .Lormh3_inspre
            .Lormh3_inssuf:
                .ascii " (revision, entity_pk, operation, at, payload, actor, actor_kind, correlation_id) VALUES (?, ?, ?, ?, ?, ?, ?, ?)"
                .set .Lormh3_inssuf_len, . - .Lormh3_inssuf
            .Lormh3_upd:
                .ascii "UPDATE"
                .set .Lormh3_upd_len, . - .Lormh3_upd
            .Lormh3_ins:
                .ascii "INSERT"
                .set .Lormh3_ins_len, . - .Lormh3_ins
            .Lormh3_system:
                .ascii "system"
                .set .Lormh3_system_len, . - .Lormh3_system
            """;
        sb.append(b
                .replace("@@SYMBOL@@", ".globl kof_orm_save_recorded\n            .type kof_orm_save_recorded, @function")
                .replace("@@CONN@@",
                        "                movq (%rsp), %rdi\n                call .Lorm_conn\n                movq %rax, 32(%rsp)             # conn no slot"));
    }
}
