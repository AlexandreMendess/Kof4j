package dev.kof.compiler.runtime;

/**
 * Emissão do ASM x86-64 de {@code valueOf(List/Set/Map)} (bug §107).
 *
 * <p>Antes o dispatch {@code valueOf} não achava vtable {@code toString} em
 * List/Map/Set (tipos de RUNTIME, sem vtable), não emitia nada e o ponteiro
 * cru caía em {@code kof_println_string} = lixo de ponteiro (R6). Aqui o
 * despachante passa, <b>em tempo de compilação</b>, um PONTEIRO para o nó
 * descritor do elemento ({@code NativePrintDescriptors}): a tag legada
 * 0=int/char/short/byte, 1=String, 2=Long, 3=Bool, 4=Double, 5=Float,
 * 6=desconhecido → {@code "?"} mais as extensões record/nested de 19/09
 * (8=objeto com toString na vtable, 9=List/Set aninhada, 10=Map aninhado).
 * SEM056 garante homogeneidade: um nó descreve todos os elementos.
 *
 * <p>Nenhum mutador compartilhado nem header de container é tocado (lição
 * §104b-ii): os helpers só LEEM o layout (List/Set: size@16, data@24; Map:
 * size@16, keys@24, vals@32). O acumulador e cada String temporária vivem no
 * <b>frame</b> (varredura conservadora da pilha no GC — {@code [rsp, rbp)}) e
 * nunca só em registrador, senão o {@code kof_alloc} de um concat subsequente
 * liberaria a String viva. Os ponteiros de descritor são {@code .rodata}
 * (não-GC). Elemento {@code null} de referência imprime {@code "null"}
 * (oracle JVM medido no ArrayList.toString do JDK).
 */
public final class RuntimeCollectionToString {

    private RuntimeCollectionToString() {}

    public static void emit(StringBuilder sb) {
        emitRodata(sb);
        emitElemToString(sb);
        emitListToString(sb);
        emitMapToString(sb);
        emitArrayToString(sb);
    }

    /**
     * Constantes usadas pelos helpers. O rótulo é definido em `.rodata` e a
     * seção volta para `.text` no mesmo bloco (idioma do `RuntimeStringBase`,
     * `.Lkof_null_str`) — assim as referências `leaq .Lc2s_*(%rip)` abaixo, já
     * dentro de `.text`, resolvem para a endereço na rodata.
     */
    static void emitRodata(StringBuilder sb) {
        sb.append("""
            .section .rodata
            .Lc2s_qstr: .ascii "?"
            .Lc2s_null: .ascii "null"
            .Lc2s_lbr:  .ascii "["
            .Lc2s_rbr:  .ascii "]"
            .Lc2s_lcur: .ascii "{"
            .Lc2s_rcur: .ascii "}"
            .Lc2s_comma: .ascii ", "
            .Lc2s_eq:   .ascii "="
            .section .text
            """);
    }

    /**
     * kof_elem_to_string(rdi = &slot, rsi = descritor) -> rax String* (NULL só
     * se a alocação falhar). Tag 0 lê os 4 bytes baixos do slot (int/char/
     * short/byte); 1 é o ponteiro String; demais primitivos seguem o
     * converter da RuntimeStringConv; 6 e qualquer tag fora = "?" (honesto);
     * 8 = vtable toString (off u16 no nó); 9/10 = recursão aninhada.
     */
    static void emitElemToString(StringBuilder sb) {
        sb.append("""
            .globl kof_elem_to_string
            .type kof_elem_to_string, @function
            kof_elem_to_string:
                pushq %rbx
                movzbl (%rsi), %eax
                cmpl $8, %eax
                je .Lce_rec
                cmpl $9, %eax
                je .Lce_list
                cmpl $10, %eax
                je .Lce_map
                cmpl $11, %eax
                je .Lce_arr
                cmpl $1, %eax
                je .Lce_str
                cmpl $2, %eax
                je .Lce_long
                cmpl $3, %eax
                je .Lce_bool
                cmpl $4, %eax
                je .Lce_double
                cmpl $5, %eax
                je .Lce_float
                cmpl $7, %eax
                je .Lce_boxed
                cmpl $0, %eax
                jne .Lce_q
            .Lce_int:
                movl (%rdi), %edi
                call kof_int_to_string
                popq %rbx
                ret
            .Lce_str:
                movq (%rdi), %rax
                popq %rbx
                ret
            .Lce_long:
                movq (%rdi), %rdi
                call kof_long_to_string
                popq %rbx
                ret
            .Lce_bool:
                movl (%rdi), %edi
                call kof_bool_to_string
                popq %rbx
                ret
            .Lce_double:
                movsd (%rdi), %xmm0
                call kof_double_to_string
                popq %rbx
                ret
            .Lce_float:
                movss (%rdi), %xmm0
                call kof_float_to_string
                popq %rbx
                ret
            .Lce_q:
                leaq .Lc2s_qstr(%rip), %rdi
                movl $1, %esi
                call kof_string_from_literal
                popq %rbx
                ret
            .Lce_null:
                leaq .Lc2s_null(%rip), %rdi
                movl $4, %esi
                call kof_string_from_literal
                popq %rbx
                ret
            # tag 7 (§284-map): caixa numerica de slot de Map — box_to_string
            # despacha por MAGIC+tag; nao-box passa cru (value já era ptr).
            .Lce_boxed:
                movq (%rdi), %rdi
                call kof_box_to_string
                popq %rbx
                ret
            # §107-nested (19/09): 8 = obj.toString() via vtable, MESMA forma
            # do ramo generico do dispatch (8(obj) = base da vtable; entrada =
            # *(base + off)); obj vivo fica enraizado no slot da pilha do
            # chamador (varredura conservadora) e em %rbx (callee-saved,
            # preservado pelo collect_now com o blanket-spill do G-6a).
            .Lce_rec:
                movq (%rdi), %rbx
                testq %rbx, %rbx
                jz .Lce_null
                movq 8(%rbx), %rax
                movzwl 1(%rsi), %ecx
                addq %rcx, %rax
                movq (%rax), %rax
                movq %rbx, %rdi
                call *%rax
                popq %rbx
                ret
            # 9 = List/Set aninhada: slot carrega o ponteiro do container; o
            # filho do no (rsi+1) descreve os elementos dele.
            # §388-B: 11 = array primitivo aninhado; no = [11, esz, child].
            # O slot carrega o PONTEIRO do array interno; o child descritor
            # fica em rsi+2 (esz fica no header do bloco — lido la).
            .Lce_arr:
                movq (%rdi), %rbx
                testq %rbx, %rbx
                jz .Lce_null
                addq $2, %rsi
                movq %rbx, %rdi
                call kof_array_to_string
                popq %rbx
                ret
            .Lce_list:
                movq (%rdi), %rbx
                testq %rbx, %rbx
                jz .Lce_null
                incq %rsi
                movq %rbx, %rdi
                call kof_list_to_string
                popq %rbx
                ret
            # 10 = Map aninhada: no = [10, valOffLo, valOffHi, key..., val...];
            # valOff mede do inicio do no ate o no do valor (3 + len(key)).
            .Lce_map:
                movq (%rdi), %rbx
                testq %rbx, %rbx
                jz .Lce_null
                movq %rsi, %rcx
                movzwl 1(%rsi), %eax
                addq %rcx, %rax
                leaq 3(%rcx), %rsi
                movq %rax, %rdx
                movq %rbx, %rdi
                call kof_map_to_string
                popq %rbx
                ret
            """);
    }

    /**
     * kof_list_to_string / kof_set_to_string (rdi = container, rsi = nó
     * descritor do elem) -> rax String* "[e1, e2, ...]". Set É um List no
     * runtime (kof_set_new = kof_list_new, mesma forma 100), então os dois são
     * o MESMO código: um galho `.globl` a mais aponta para a mesma etiqueta.
     */
    static void emitListToString(StringBuilder sb) {
        sb.append("""
            .globl kof_list_to_string
            .type kof_list_to_string, @function
            .globl kof_set_to_string
            .type kof_set_to_string, @function
            kof_list_to_string:
            kof_set_to_string:
                pushq %rbp
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                movq %rsp, %rbp             # ancora o frame: locais abaixo de
                subq $64, %rsp              # rbp; os pushq de CALL caem ABAIXO
                                            # dos locais (não os pisa)
                movq %rdi, -32(%rbp)        # container (raiz p/ GC)
                movq %rsi, -40(%rbp)        # descritor do elem (.rodata)
                movl 16(%rdi), %r12d        # size
                movq 24(%rdi), %r13         # data
                xorl %ebx, %ebx             # i
                leaq .Lc2s_lbr(%rip), %rdi
                movl $1, %esi
                call kof_string_from_literal
                movq %rax, -8(%rbp)         # acc = '['
                testl %r12d, %r12d
                jz .Lcl_fin
            .Lcl_loop:
                movslq %ebx, %rax
                movq (%r13,%rax,8), %rax
                movq %rax, -24(%rbp)        # slot do elem (raiz p/ tag 1 e p/
                                            # as recursivo-record 8/9/10)
                testl %ebx, %ebx
                jnz .Lcl_sep2
                leaq .Lc2s_lbr(%rip), %rdi
                xorl %esi, %esi
                jmp .Lcl_sepcall
            .Lcl_sep2:
                leaq .Lc2s_comma(%rip), %rdi
                movl $2, %esi
            .Lcl_sepcall:
                call kof_string_from_literal
                movq %rax, -16(%rbp)        # sep (raiz p/ alloc do elem)
                leaq -24(%rbp), %rdi
                movq -40(%rbp), %rsi
                call kof_elem_to_string
                movq %rax, %rsi
                movq -16(%rbp), %rdi
                call kof_string_concat      # sep + elem
                movq %rax, %rsi
                movq -8(%rbp), %rdi
                call kof_string_concat      # acc + (sep + elem)
                movq %rax, -8(%rbp)         # acc (raiz)
                incl %ebx
                cmpl %r12d, %ebx
                jl .Lcl_loop
            .Lcl_fin:
                leaq .Lc2s_rbr(%rip), %rdi
                movl $1, %esi
                call kof_string_from_literal
                movq %rax, %rsi
                movq -8(%rbp), %rdi
                call kof_string_concat
                movq %rbp, %rsp
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                popq %rbp
                ret
            """);
    }

    /**
     * kof_map_to_string (rdi = map, rsi = nó da chave, rdx = nó do valor)
     * -> rax String* "{k=v, k=v}". Ordem de ARMAZENAMENTO (inserção) — o
     * runtime usa vetores lineares, não buckets de hash do HashMap do JVM
     * (divergência de arquitetura registrada no §107, não lixo). A String
     * "chave=" fica enraizada no frame enquanto o valor é convertido.
     */
    static void emitMapToString(StringBuilder sb) {
        sb.append("""
            .globl kof_map_to_string
            .type kof_map_to_string, @function
            kof_map_to_string:
                pushq %rbp
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                movq %rsp, %rbp
                subq $80, %rsp
                movq %rdi, -48(%rbp)        # container (raiz)
                movq %rsi, -56(%rbp)        # descritor da chave (.rodata)
                movq %rdx, -64(%rbp)        # descritor do valor (.rodata)
                movl 16(%rdi), %r12d        # size
                movq 24(%rdi), %r13         # keys
                movq 32(%rdi), %r14         # vals
                xorl %ebx, %ebx             # i
                leaq .Lc2s_lcur(%rip), %rdi
                movl $1, %esi
                call kof_string_from_literal
                movq %rax, -8(%rbp)         # acc = '{'
                testl %r12d, %r12d
                jz .Lcm_fin
            .Lcm_loop:
                movslq %ebx, %rax
                movq (%r13,%rax,8), %rcx
                movq %rcx, -24(%rbp)        # slot chave (raiz)
                movq (%r14,%rax,8), %rcx
                movq %rcx, -32(%rbp)        # slot valor (raiz)
                testl %ebx, %ebx
                jnz .Lcm_sep2
                leaq .Lc2s_lcur(%rip), %rdi
                xorl %esi, %esi
                jmp .Lcm_sepcall
            .Lcm_sep2:
                leaq .Lc2s_comma(%rip), %rdi
                movl $2, %esi
            .Lcm_sepcall:
                call kof_string_from_literal
                movq %rax, -16(%rbp)        # sep (raiz)
                leaq -24(%rbp), %rdi
                movq -56(%rbp), %rsi
                call kof_elem_to_string
                movq %rax, -40(%rbp)        # keyStr (raiz)
                leaq .Lc2s_eq(%rip), %rdi
                movl $1, %esi
                call kof_string_from_literal
                movq %rax, %rsi
                movq -40(%rbp), %rdi
                call kof_string_concat      # keyStr + '='
                movq %rax, -40(%rbp)        # 'chave=' (raiz p/ alloc do valor)
                leaq -32(%rbp), %rdi
                movq -64(%rbp), %rsi
                call kof_elem_to_string
                movq %rax, %rsi
                movq -40(%rbp), %rdi
                call kof_string_concat      # 'chave=valor'
                movq %rax, %rsi
                movq -16(%rbp), %rdi
                call kof_string_concat      # sep + 'chave=valor'
                movq %rax, %rsi
                movq -8(%rbp), %rdi
                call kof_string_concat      # acc + sep + 'chave=valor'
                movq %rax, -8(%rbp)         # acc (raiz)
                incl %ebx
                cmpl %r12d, %ebx
                jl .Lcm_loop
            .Lcm_fin:
                leaq .Lc2s_rcur(%rip), %rdi
                movl $1, %esi
                call kof_string_from_literal
                movq %rax, %rsi
                movq -8(%rbp), %rdi
                call kof_string_concat
                movq %rbp, %rsp
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                popq %rbp
                ret
            """);
    }

    /**
     * §388-B — kof_array_to_string(rdi = array cru, rsi = descritor do
     * COMPONENTE): bloco [tag@0][len@16][esz@20][data@24] (mesma fonte do
     * elementTypeSize do alloc). Espelha kof_list_to_string no formato
     * ([a, b]) e no mecanismo (kof_string_from_literal/kof_string_concat);
     * a diferença é o carregamento do elemento: packed por ESZ (1/4/8) num
     * slot de 8 bytes na pilha — o kof_elem_to_string lê só os bytes que o
     * tag pede, e aninhado (tag 11) recursa. Golden = oracle JVM/Script/JS
     * ([65, 66]) — a célula antes imprimia o elem[0] como char ("A").
     */
    static void emitArrayToString(StringBuilder sb) {
        sb.append("""
            .globl kof_array_to_string
            .type kof_array_to_string, @function
            kof_array_to_string:
                pushq %rbp
                pushq %rbx
                pushq %r12
                pushq %r13
                pushq %r14
                pushq %r15
                movq %rsp, %rbp
                subq $64, %rsp
                movq %rdi, -32(%rbp)        # array (raiz p/ GC)
                movq %rsi, -40(%rbp)        # descritor do componente
                movl 16(%rdi), %r12d        # len
                movl 20(%rdi), %r14d        # esz
                leaq 24(%rdi), %r13         # data
                xorl %ebx, %ebx             # i
                leaq .Lc2s_lbr(%rip), %rdi
                movl $1, %esi
                call kof_string_from_literal
                movq %rax, -8(%rbp)         # acc = '['
                testl %r12d, %r12d
                jz .Lca_fin
            .Lca_loop:
                movslq %ebx, %rax
                imulq %r14, %rax
                leaq (%r13,%rax), %r15      # &elem[i]
                movq $0, -24(%rbp)          # slot de 8B (raiz p/ ponteiros)
                cmpl $4, %r14d
                je .Lca_ld4
                cmpl $1, %r14d
                je .Lca_ld1
                cmpl $2, %r14d
                je .Lca_ld2
                movq (%r15), %rax           # esz 8 (long/double/String/array)
                movq %rax, -24(%rbp)
                jmp .Lca_sep
            .Lca_ld4:
                movl (%r15), %eax           # int/char/float-as-bits/bool
                movq %rax, -24(%rbp)
                jmp .Lca_sep
            .Lca_ld2:
                movzwl (%r15), %eax         # short
                movq %rax, -24(%rbp)
                jmp .Lca_sep
            .Lca_ld1:
                movzbl (%r15), %eax         # byte/bool de 1 byte
                movq %rax, -24(%rbp)
            .Lca_sep:
                testl %ebx, %ebx
                jnz .Lca_sep2
                leaq .Lc2s_lbr(%rip), %rdi
                xorl %esi, %esi
                jmp .Lca_sepcall
            .Lca_sep2:
                leaq .Lc2s_comma(%rip), %rdi
                movl $2, %esi
            .Lca_sepcall:
                call kof_string_from_literal
                movq %rax, -16(%rbp)        # sep (raiz p/ alloc do elem)
                leaq -24(%rbp), %rdi
                movq -40(%rbp), %rsi
                call kof_elem_to_string
                movq %rax, %rsi
                movq -16(%rbp), %rdi
                call kof_string_concat      # sep + elem
                movq %rax, %rsi
                movq -8(%rbp), %rdi
                call kof_string_concat      # acc + (sep + elem)
                movq %rax, -8(%rbp)
                incl %ebx
                cmpl %r12d, %ebx
                jl .Lca_loop
            .Lca_fin:
                leaq .Lc2s_rbr(%rip), %rdi
                movl $1, %esi
                call kof_string_from_literal
                movq %rax, %rsi
                movq -8(%rbp), %rdi
                call kof_string_concat
                movq %rbp, %rsp
                popq %r15
                popq %r14
                popq %r13
                popq %r12
                popq %rbx
                popq %rbp
                ret
            """);
    }

}
