package dev.kof.compiler.runtime;

/**
 * F2b (D-DB-GAPS, 20/09): {@code kof_orm_find} row-object de LEITURA no
 * runtime Native x86-64 — {@code SELECT * FROM "t" WHERE "pk" = ?} com o key
 * bindado (box §284 / KofString / null, mesmo classificador do
 * {@code RuntimeOrm3}) e CONSTRUÇÃO do record no próprio runtime.
 *
 * <p>O typeId/vtable são constantes do PROGRAMA (não existem no slice), então
 * a construção passa pelo resolver {@code kof_orm_ctors} (emitido pelo
 * {@code NativeOrmCtors} do backend para cada entidade usada com {@code find},
 * com os valores reais de {@code clazz.typeId()}/{@code vtable}/{@code
 * totalSize}). Sem entrada para o className → throw honesto ORM001 (não
 * acontece por construção: todo {@code find<E>} emite a entrada de {@code E}).
 *
 * <p>Colunas casadas por NOME (como o host, que monta um Map por label e
 * binda o record por componente): para cada campo do schema, scan das colunas
 * com comparação byte a byte; campo sem coluna → throw {@code "sqlite: no
 * column <name>"} (R6 — o host explodiria na reflexão com null). Leitura por
 * typeCode: int via {@code column_int}+{@code movslq} (8B no slot), long via
 * {@code column_int64}, string via {@code make_string} (SQLITE_NULL → null),
 * bool por tipo dinâmico da coluna (§397, paridade com o binder
 * consertado do host): INTEGER/FLOAT !=0, TEXT literal "true", NULL
 * false; double via {@code column_double}+{@code movsd},
 * float widened→{@code cvtss2sd... cvtsd2ss}+{@code movss} 4B (slots de
 * float guardam os bits nos 4 bytes baixos, como o save/bind lêem).
 *
 * <p>Miss (step != SQLITE_ROW) → {@code null} (rax=0), como o host
 * {@code rows.isEmpty() ? null : rows.get(0)} — NÃO é throw.
 *
 * <p>F2d3b (DB-3, 21/09): o tipo do handle é checado na entrada —
 * {@code kof_db_type(id)==2} (mysql) restaura o frame e tail-chama
 * {@code .Lorm_find_my} (RuntimeOrmMysqlFind, wire text protocol), com
 * paridade byte JVM==Native medida. Só o sqlite segue o corpo abaixo.
 *
 * <p>Contrato de pilha (F1c): prólogo com {@code andq}, frame 168 (8 mod 16),
 * todo {@code call} de C sai com rsp ≡ 0; stmt vive em {@code r12}
 * (callee-saved, precedentes Orm3/Orm4).
 */
public final class RuntimeOrm5 {

    private RuntimeOrm5() {}

    public static void emit(StringBuilder sb) {
        sb.append("""
            # ---------------------------------------------------------------
            # kof_orm_find(id*, key*, table*, schema*, className*) -> record*|0
            #   slots: 0 id | 8 key | 16 table | 24 schema | 32 className |
            #   40 conn | 48 ftab | 56 nFields | 64 pkIndex | 72 pkType |
            #   80 stmt | 88 dst | 96 i | 104 nCols | 112 entry |
            #   120 nameLen | 128 colj | 136 vtab | 144 typeId | 152 totalSize
            # ---------------------------------------------------------------
                .globl kof_orm_find
                .type kof_orm_find, @function
            kof_orm_find:
                pushq %rbp
                movq %rsp, %rbp
                andq $-16, %rsp
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                subq $168, %rsp
                movq %rdi, (%rsp)
                movq %rsi, 8(%rsp)
                movq %rdx, 16(%rsp)
                movq %rcx, 24(%rsp)
                movq %r8, 32(%rsp)
                movq (%rsp), %rdi
                call kof_db_type
                cmpl $2, %eax
                je .Lorm5_my_dispatch
                movq (%rsp), %rdi
                call .Lorm_conn
                movq %rax, 40(%rsp)
                movq 24(%rsp), %rdi
                call kof_orm_parse_schema
                movq %rax, 48(%rsp)          # ftab
                movq %rcx, 56(%rsp)          # nFields
                movq %r8, 64(%rsp)           # pkIndex
                movq 64(%rsp), %rcx
                shlq $5, %rcx
                addq 48(%rsp), %rcx
                movl 12(%rcx), %ecx
                movl %ecx, 72(%rsp)          # pkType
            # ---- SQL: SELECT * FROM "t" WHERE "pk" = ? --------------------
                movq 16(%rsp), %rax
                movl 16(%rax), %eax          # tblLen
                movq 64(%rsp), %rdx
                shlq $5, %rdx
                addq 48(%rsp), %rdx
                movl 8(%rdx), %ecx           # pkLen
                leal (%rax,%rcx,2), %edi
                addl $48, %edi
                call .Lorm_bbegin
                leaq .Lorm5_s1(%rip), %rsi
                movl $14, %ecx
                call .Lorm_bp
                movq 16(%rsp), %rdi
                call .Lorm2_qq
                leaq .Lorm5_s2(%rip), %rsi
                movl $7, %ecx
                call .Lorm_bp
                movq 64(%rsp), %rax
                shlq $5, %rax
                addq 48(%rsp), %rax
                movq 0(%rax), %rsi
                movl 8(%rax), %ecx
                call .Lorm_qraw
                leaq .Lorm5_s3(%rip), %rsi
                movl $4, %ecx
                call .Lorm_bp
                call .Lorm_bfin
            # ---- prepare --------------------------------------------------
                movq 40(%rsp), %rdi
                leaq 24(%rbx), %rsi
                movq $-1, %rdx
                leaq 80(%rsp), %rcx
                xorl %r8d, %r8d
                call sqlite3_prepare_v2
                movq 80(%rsp), %r12
                testq %r12, %r12
                jz .Lorm5_prep_fail
            # ---- bind pk do key (box §284 / KofString / null) -------------
                movq %r12, %rdi
                movl $1, %esi
                movq 8(%rsp), %r13
                testq %r13, %r13
                jz .Lorm5f_bn
                movq (%r13), %rax
                movabsq $@@MAGIC@@, %rcx
                cmpq %rcx, %rax
                jne .Lorm5f_strchk
                movl 8(%r13), %eax           # tag: 0=int 1=bool 2=long
                cmpl $0, %eax
                je .Lorm5f_bint
                cmpl $1, %eax
                je .Lorm5f_bquad
                cmpl $2, %eax
                je .Lorm5f_bquad
                cmpl $4, %eax
                je .Lorm5f_bdbl
                cmpl $5, %eax
                je .Lorm5f_bflt
                jmp .Lorm5f_bad
            .Lorm5f_bint:
                movl 16(%r13), %eax
                movslq %eax, %rdx
                jmp .Lorm5f_bq
            .Lorm5f_bquad:
                movq 16(%r13), %rdx
            .Lorm5f_bq:
                movq %r12, %rdi
                movl $1, %esi
                call sqlite3_bind_int64
                jmp .Lorm5_step
            .Lorm5f_bdbl:
                movq 16(%r13), %rax
                movq %rax, %xmm0
                movq %r12, %rdi
                movl $1, %esi
                call sqlite3_bind_double
                jmp .Lorm5_step
            .Lorm5f_bflt:
                movl 16(%r13), %eax
                movd %eax, %xmm0
                cvtss2sd %xmm0, %xmm0
                movq %r12, %rdi
                movl $1, %esi
                call sqlite3_bind_double
                jmp .Lorm5_step
            .Lorm5f_strchk:
                cmpl $1, (%r13)              # KofString (o call-site nativo
                                             #   coerce primitivo->OBJ via
                                             #   kof_long_to_string — o key
                                             #   CHEGA como KofString, medido;
                                             #   bind TEXT + affinity do sqlite
                                             #   casa pk INTEGER)
                jne .Lorm5f_bad
                cmpl $0, 4(%r13)
                jne .Lorm5f_bad
                movq 8(%r13), %rax
                testq %rax, %rax
                jnz .Lorm5f_bad
                leaq 24(%r13), %rdx
                movl 16(%r13), %ecx
                movslq %ecx, %rcx
                movq $-1, %r8
                movq %r12, %rdi
                movl $1, %esi
                call sqlite3_bind_text
                jmp .Lorm5_step
            .Lorm5f_bn:
                movq %r12, %rdi
                movl $1, %esi
                call sqlite3_bind_null
            # ---- step: 100=ROW; senao miss -> null (host) ------------------
            .Lorm5_step:
                movq %r12, %rdi
                call sqlite3_step
                cmpl $100, %eax
                jne .Lorm5_miss
            # ---- resolve ctor (className -> vtab/typeId/totalSize) ---------
                movq 32(%rsp), %rax
                leaq 24(%rax), %rdi          # body do className (INLINE no
                                             #   +24 do KofString — movq aqui
                                             #   carregava o TEXTO como ponteiro:
                                             #   SEGV medido no 1o movzbl)
                movl 16(%rax), %esi          # len
                call kof_orm_ctors
                testq %rax, %rax
                jz .Lorm5_noface
                movq %rax, 136(%rsp)         # vtab
                movq %rdx, 144(%rsp)         # typeId
                movq %rcx, 152(%rsp)         # totalSize
                movq 152(%rsp), %rdi
                call kof_alloc
                movq %rax, 88(%rsp)          # dst
                movq 88(%rsp), %rdi
                movl 144(%rsp), %esi
                movq 136(%rsp), %rdx
                call kof_init_object
            # ---- loop de campos: casar coluna por NOME, ler por typeCode ---
                movq %r12, %rdi
                call sqlite3_column_count
                movl %eax, 104(%rsp)
                movq $0, 96(%rsp)
            .Lorm5_fld:
                movq 96(%rsp), %rax
                cmpq 56(%rsp), %rax
                jge .Lorm5_done
                movq 96(%rsp), %rax
                shlq $5, %rax
                addq 48(%rsp), %rax
                movq %rax, 112(%rsp)         # entry
                movl 8(%rax), %eax
                movl %eax, 120(%rsp)         # nameLen
                movq $0, 128(%rsp)           # colj
            .Lorm5_col:
                movl 128(%rsp), %eax
                cmpl 104(%rsp), %eax
                jge .Lorm5_nomatch
                movq %r12, %rdi
                movl 128(%rsp), %esi
                call sqlite3_column_name
                movq %rax, %r13              # colname (C string)
                movq 112(%rsp), %rsi
                movq 0(%rsi), %rsi           # field name
                movl 120(%rsp), %edx
                xorl %ecx, %ecx
            .Lorm5_cmp:
                cmpl %edx, %ecx
                jge .Lorm5_cmp_end
                movzbl (%r13,%rcx), %r8d
                movzbl (%rsi,%rcx), %r9d
                cmpl %r9d, %r8d
                jne .Lorm5_nextcol
                incl %ecx
                jmp .Lorm5_cmp
            .Lorm5_cmp_end:
                cmpb $0, (%r13,%rcx)
                jne .Lorm5_nextcol
                jmp .Lorm5_read              # casou: colj atual
            .Lorm5_nextcol:
                incq 128(%rsp)
                jmp .Lorm5_col
            .Lorm5_read:
                movq 112(%rsp), %rax
                movl 12(%rax), %ecx          # typeCode
                movq 88(%rsp), %rdx
                movq 96(%rsp), %rax
                shlq $3, %rax
                addq $16, %rax
                addq %rdx, %rax              # slot do campo i
                movq %rax, %r14              # slot (callee-saved)
                movq 128(%rsp), %r15         # colj casada
                cmpl $2, %ecx
                je .Lorm5_rstr
                cmpl $3, %ecx
                je .Lorm5_rbool
                cmpl $4, %ecx
                je .Lorm5_rdbl
                cmpl $5, %ecx
                je .Lorm5_rflt
                cmpl $1, %ecx
                je .Lorm5_rlng
                movq %r12, %rdi
                movl %r15d, %esi
                call sqlite3_column_int
                movslq %eax, %rax
                movq %rax, (%r14)
                jmp .Lorm5_fldnext
            .Lorm5_rlng:
                movq %r12, %rdi
                movl %r15d, %esi
                call sqlite3_column_int64
                movq %rax, (%r14)
                jmp .Lorm5_fldnext
            .Lorm5_rdbl:
                movq %r12, %rdi
                movl %r15d, %esi
                call sqlite3_column_double
                movsd %xmm0, (%r14)          # F2b (23/09): o retorno vem em
                                             #   xmm0 (movq %rax lia lixo = 0.0)
                jmp .Lorm5_fldnext
            .Lorm5_rflt:
                movq %r12, %rdi
                movl %r15d, %esi
                call sqlite3_column_double
                cvtsd2ss %xmm0, %xmm0
                movss %xmm0, (%r14)
                jmp .Lorm5_fldnext
            .Lorm5_rstr:
                movq %r12, %rdi
                movl %r15d, %esi
                call sqlite3_column_type
                cmpl $5, %eax                # SQLITE_NULL -> null
                jne .Lorm5_rstr1
                movq $0, (%r14)
                jmp .Lorm5_fldnext
            .Lorm5_rstr1:
                movq %r12, %rdi
                movl %r15d, %esi
                call sqlite3_column_text
                movq %rax, %r13              # ptr
                movq %r13, %rdi
                call kof_io_strlen
                movq %rax, %rsi
                movq %r13, %rdi
                call kof_io_make_string
                movq %rax, (%r14)
                jmp .Lorm5_fldnext
            .Lorm5_rbool:
                # §397: paridade EXATA com o binder do host (kof_json_bind):
                # NULL->false, INTEGER/FLOAT->numero!=0, TEXT->"true"
                # literal. O pre-F2b copiava o oracle antigo (1->false); o
                # host foi consertado - a asm segue o host consertado.
                movq %r12, %rdi
                movl %r15d, %esi
                call sqlite3_column_type
                cmpl $5, %eax
                je .Lorm5_rboolnull
                cmpl $1, %eax
                je .Lorm5_rboolint
                cmpl $2, %eax
                je .Lorm5_rbooldbl
                jmp .Lorm5_rbool1            # TEXT/BLOB: literal "true"
            .Lorm5_rboolnull:
                movq $0, (%r14)              # null -> false (String.valueOf)
                jmp .Lorm5_fldnext
            .Lorm5_rboolint:
                movq %r12, %rdi
                movl %r15d, %esi
                call sqlite3_column_int64
                testq %rax, %rax
                setne %al
                movzbl %al, %eax
                movq %rax, (%r14)
                jmp .Lorm5_fldnext
            .Lorm5_rbooldbl:
                movq %r12, %rdi
                movl %r15d, %esi
                call sqlite3_column_double
                cvtsd2si %xmm0, %rax         # truncate identico ao intValue() do binder
                testq %rax, %rax
                setne %al
                movzbl %al, %eax
                movq %rax, (%r14)
                jmp .Lorm5_fldnext
            .Lorm5_rbool1:
                movq %r12, %rdi
                movl %r15d, %esi
                call sqlite3_column_text
                testq %rax, %rax
                jnz .Lorm5_rbool2
                movq $0, (%r14)
                jmp .Lorm5_fldnext
            .Lorm5_rbool2:
                movzbl (%rax), %r13d
                orl $32, %r13d
                cmpl $116, %r13d             # 't'
                jne .Lorm5_rboolf
                movzbl 1(%rax), %r13d
                orl $32, %r13d
                cmpl $114, %r13d             # 'r'
                jne .Lorm5_rboolf
                movzbl 2(%rax), %r13d
                orl $32, %r13d
                cmpl $117, %r13d             # 'u'
                jne .Lorm5_rboolf
                movzbl 3(%rax), %r13d
                orl $32, %r13d
                cmpl $101, %r13d             # 'e'
                jne .Lorm5_rboolf
                movq $1, (%r14)
                jmp .Lorm5_fldnext
            .Lorm5_rboolf:
                movq $0, (%r14)              # texto != "true" -> false (igual ao binder)
            .Lorm5_fldnext:
                incq 96(%rsp)
                jmp .Lorm5_fld
            .Lorm5_done:
                movq %r12, %rdi
                call sqlite3_finalize
                movq 88(%rsp), %rax
                jmp .Lorm5_ret
            .Lorm5_miss:
                movq %r12, %rdi
                call sqlite3_finalize
                xorl %eax, %eax
                jmp .Lorm5_ret
            # ---- fail: throw "sqlite: " + errmsg --------------------------
            .Lorm5_prep_fail:
                movl $512, %edi
                call .Lorm_bbegin
                leaq .Lorm5_pre(%rip), %rsi
                movl $8, %ecx
                call .Lorm_bp
                movq 40(%rsp), %rdi
                call sqlite3_errmsg
                testq %rax, %rax
                jz .Lorm5_pfin
                movq %rax, %rsi
                call .Lorm_pcstr
            .Lorm5_pfin:
                call .Lorm_bfin
                movq %rbx, %rdi
                call kof_throw_string
                ud2
            .Lorm5_nomatch:
                movl $256, %edi
                call .Lorm_bbegin
                leaq .Lorm5_nc(%rip), %rsi
                movl $18, %ecx
                call .Lorm_bp
                movq 112(%rsp), %rax
                movq 0(%rax), %rsi
                movl 8(%rax), %ecx
                call .Lorm_qraw
                call .Lorm_bfin
                movq %rbx, %rdi
                call kof_throw_string
                ud2
            .Lorm5_noface:
                leaq .Lorm5_face(%rip), %rdi
                call kof_throw_string
                ud2
            .Lorm5f_bad:
                movq %r12, %rdi
                call sqlite3_finalize
                leaq .Lorm5_badv(%rip), %rdi
                call kof_throw_string
                ud2
            .Lorm5_ret:
                addq $168, %rsp
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                movq %rbp, %rsp
                popq %rbp
                ret

            # F2d3b: mysql -> restaura o frame e tail-chama .Lorm_find_my
            # (args originais nos slots; a asm do mysql vive no
            # RuntimeOrmMysqlFind, emitido logo depois no mesmo .s).
            .Lorm5_my_dispatch:
                movq (%rsp), %rdi
                movq 8(%rsp), %rsi
                movq 16(%rsp), %rdx
                movq 24(%rsp), %rcx
                movq 32(%rsp), %r8
                addq $168, %rsp
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                movq %rbp, %rsp
                popq %rbp
                jmp .Lorm_find_my

            # ---------------------- literais --------------------------------
            .Lorm5_s1:
                .ascii "SELECT * FROM "
            .Lorm5_s2:
                .ascii " WHERE "
            .Lorm5_s3:
                .ascii " = ?"
            .Lorm5_pre:
                .ascii "sqlite: "
            .Lorm5_nc:
                .ascii "sqlite: no column "
            .Lorm5_face:
                .long 1
                .long 0
                .quad 0
                .long .Lorm5_face_len
                .long 0
            .Lorm5_face_body:
                .ascii "orm.find: entity class not registered (ORM001)"
                .byte 0
                .set .Lorm5_face_len, . - .Lorm5_face_body - 1
            .Lorm5_badv:
                .long 1
                .long 0
                .quad 0
                .long .Lorm5_badv_len
                .long 0
            .Lorm5_badv_body:
                .ascii "orm.find bind value: unsupported type on Native (ORM001)"
                .byte 0
                .set .Lorm5_badv_len, . - .Lorm5_badv_body - 1
            """);
    }
}
