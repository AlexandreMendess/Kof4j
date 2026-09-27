package dev.kof.compiler.runtime;

/**
 * Parser do schema literal do ORM para as fatias row-object do runtime
 * Native (x86-64): {@code kof_orm_parse_schema} — consumido por
 * {@code kof_orm_save} ({@code RuntimeOrm4}) e, depois, por find/all/where
 * (F2b/F2c). O literal é o compilado por {@code KofOrm.schemaString}:
 * campos separados por {@code ','}, partes por {@code ':'} —
 * {@code name:dbType[:generated][:unique]}.
 *
 * <p>Saída: uma tabela de campos com entrada de 32B por campo
 * {@code [+0 namePtr | +8 nameLen | +12 typeCode | +16 flags]} alocada via
 * {@code kof_alloc} (names copiados para um buffer adjacente), onde
 * {@code typeCode} é {@code 0=int, 1=long, 2=string, 3=bool, 4=double,
 * 5=float} (1º byte do dbType; default string como o {@code dbType} do
 * host) e {@code flags} tem bit0 = {@code :generated}, bit1 = {@code :unique}.
 *
 * <p>Retorno em registradores: {@code rax=ftab, rdx=nbuf, rcx=nFields,
 * r8=pkIndex} (o PRIMEIRO campo com bit0, senão 0 — espelha
 * {@code kof_orm_pkIndex} do host). Ordem = ordem de declaração = ordem dos
 * slots do record ({@code ClassLayout} 16+8i).
 *
 * <p>Contrato de pilha (F1c): {@code andq} no prólogo, todo {@code call} de
 * C sai com rsp ≡ 0. Registradores de cursor (r12/r13) não sobrevivem à
 * função — quem chama guarda o que precisa.
 */
public final class RuntimeOrmSchema {

    private RuntimeOrmSchema() {}

    public static void emit(StringBuilder sb) {
        sb.append("""
            # ---------------------------------------------------------------
            # kof_orm_parse_schema(schema*) -> rax=ftab, rdx=nbuf,
            #                                 rcx=nFields, r8=pkIndex
            #   slots: 0 schema | 8 fi | 16 nbp | 24 ftab | 32 nbuf
            #   parse: r12=cursor, r13=fim, r9=entry, r11=flags
            # ---------------------------------------------------------------
                .globl kof_orm_parse_schema
                .type kof_orm_parse_schema, @function
            kof_orm_parse_schema:
                pushq %rbp
                movq %rsp, %rbp
                andq $-16, %rsp
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                subq $56, %rsp
                movq %rdi, (%rsp)
                movl 16(%rdi), %eax          # schLen
                movl %eax, %edi
                imull $32, %edi, %edi        # ftab: schLen entradas
                addl %eax, %edi              # nbuf: schLen bytes
                addl $64, %edi               # folga
                movl %edi, %edi
                call kof_alloc
                movq %rax, 24(%rsp)          # ftab
                movq (%rsp), %rcx
                movl 16(%rcx), %ecx
                imull $32, %ecx, %ecx
                addq %rcx, %rax
                movq %rax, 32(%rsp)          # nbuf
                movq %rax, 16(%rsp)          # nbp
                movq $0, 8(%rsp)             # fi
                movq (%rsp), %rcx            # schema*
                leaq 24(%rcx), %r12          # body
                movl 16(%rcx), %ecx          # len
                leaq (%r12,%rcx), %r13       # fim = body + len
            .Lormps_fld:
                movq %r12, %rbx              # inicio do nome
            .Lormps_nscan:
                cmpq %r13, %r12
                jge .Lormps_nend
                movzbl (%r12), %eax
                cmpl $58, %eax               # ':'
                je .Lormps_nend
                cmpl $44, %eax               # ','
                je .Lormps_nend
                incq %r12
                jmp .Lormps_nscan
            .Lormps_nend:
                movq %r12, %rax
                subq %rbx, %rax              # nameLen
                movq 24(%rsp), %r9
                movq 8(%rsp), %r10
                shlq $5, %r10
                addq %r10, %r9               # entry
                movq 16(%rsp), %rcx          # nbp
                movq %rcx, 0(%r9)            # namePtr
                movl %eax, 8(%r9)            # nameLen
                movq %rbx, %rsi
                xorl %edx, %edx
            .Lormps_ncopy:
                cmpl %eax, %edx
                jge .Lormps_ncopied
                movzbl (%rsi,%rdx), %r8d
                movb %r8b, (%rcx,%rdx)
                incl %edx
                jmp .Lormps_ncopy
            .Lormps_ncopied:
                addq %rax, %rcx
                movq %rcx, 16(%rsp)          # nbp += nameLen
                xorl %r11d, %r11d            # flags
                movl $2, %r8d                # typeCode default string
                cmpq %r13, %r12
                jge .Lormps_fldend
                movzbl (%r12), %eax
                cmpl $58, %eax               # ':' abre o segmento de tipo
                jne .Lormps_fldend
                incq %r12
                movzbl (%r12), %eax
                cmpl $105, %eax              # i -> int(0)
                jne .Lormps_t1
                xorl %r8d, %r8d
                jmp .Lormps_tscan
            .Lormps_t1:
                cmpl $108, %eax              # l -> long(1)
                jne .Lormps_t2
                movl $1, %r8d
                jmp .Lormps_tscan
            .Lormps_t2:
                cmpl $115, %eax              # s -> string(2, default)
                je .Lormps_tscan
                cmpl $98, %eax               # b -> bool(3)
                jne .Lormps_t3
                movl $3, %r8d
                jmp .Lormps_tscan
            .Lormps_t3:
                cmpl $100, %eax              # d -> double(4)
                jne .Lormps_t4
                movl $4, %r8d
                jmp .Lormps_tscan
            .Lormps_t4:
                cmpl $102, %eax              # f -> float(5)
                jne .Lormps_tscan
                movl $5, %r8d
            .Lormps_tscan:                   # ate ':' (flags) | ',' | fim
                cmpq %r13, %r12
                jge .Lormps_fldend
                movzbl (%r12), %eax
                cmpl $58, %eax
                je .Lormps_flg
                cmpl $44, %eax
                je .Lormps_fldend
                incq %r12
                jmp .Lormps_tscan
            .Lormps_flg:
                cmpq %r13, %r12
                jge .Lormps_fldend
                movzbl (%r12), %eax
                cmpl $58, %eax
                jne .Lormps_fldend
                incq %r12
                movzbl (%r12), %eax
                cmpl $103, %eax              # g -> generated
                jne .Lormps_flu
                orl $1, %r11d
                jmp .Lormps_seg
            .Lormps_flu:
                cmpl $117, %eax              # u -> unique
                jne .Lormps_seg
                orl $2, %r11d
            .Lormps_seg:
                cmpq %r13, %r12
                jge .Lormps_fldend
                movzbl (%r12), %eax
                cmpl $44, %eax
                je .Lormps_fldend
                incq %r12
                jmp .Lormps_seg
            .Lormps_fldend:
                movl %r8d, 12(%r9)
                movl %r11d, 16(%r9)
                cmpq %r13, %r12
                jge .Lormps_done
                movzbl (%r12), %eax
                cmpl $44, %eax
                jne .Lormps_done
                incq %r12
                incq 8(%rsp)
                jmp .Lormps_fld
            .Lormps_done:
                movq 8(%rsp), %rax
                incq %rax
                movq %rax, %rcx              # rcx = nFields
                movq 24(%rsp), %rax          # rax = ftab
                movq 32(%rsp), %rdx          # rdx = nbuf
                movq %rax, %r11              # ftab
                movq %rcx, %r10              # nFields
                xorl %r8d, %r8d              # r8 = pkIndex (default 0)
                xorl %r9d, %r9d              # i
            .Lormps_pk:
                cmpq %r10, %r9
                jge .Lormps_pk_done
                movq %r9, %rcx
                shlq $5, %rcx
                addq %r11, %rcx
                testl $1, 16(%rcx)
                jz .Lormps_pk_next
                movq %r9, %r8                # primeiro :generated
                jmp .Lormps_pk_done
            .Lormps_pk_next:
                incq %r9
                jmp .Lormps_pk
            .Lormps_pk_done:
                movq %r10, %rcx              # rcx = nFields (retorno)
                addq $56, %rsp
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                movq %rbp, %rsp
                popq %rbp
                ret
            """);
    }
}
