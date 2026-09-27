[English](RUNTIME_ABI.md) | [Português](RUNTIME_ABI.pt_BR.md)

# RUNTIME_ABI.md — Contrato de Runtime do Kof

**Data:** 21 de agosto de 2026
**Status:** Definição — Fase F

---

## 1. Visão Geral

A Kof Runtime ABI define o contrato semântico entre o compilador Kof e as implementações de runtime (JVM e Native).

```
Kof Language
      ↓
  Kof IR
      ↓
Kof Runtime ABI
   ↙       ↘
JVM         Native
```

A ABI NÃO é uma especificação de bytecode ou assembly. É um contrato de comportamento que ambas as implementações devem satisfazer.

---

## 2. Princípios

1. **Independência de plataforma** — a ABI não assume endianness, word size, ou calling convention
2. **Minimalismo** — definir apenas o necessário para o subconjunto atual da linguagem
3. **Evolutividade** — novos recursos podem ser adicionados sem quebrar implementações existentes
4. **Dupla implementação** — cada recurso da ABI deve ter implementação JVM e Native
5. **Nenhuma dependência JVM** — a ABI NÃO referencia java.lang.Object, java.lang.String, etc.

---

## 3. Recursos da ABI

### 3.1 Alocação

| Operação | Descrição |
|----------|-----------|
| `kof_alloc(size)` | Aloca `size` bytes no heap, retorna ponteiro |
| `kof_free(ptr)` | Libera memória alocada por kof_alloc |

**Contrato:**
- `kof_alloc` retorna ponteiro alinhado em 16 bytes
- `kof_alloc` retorna NULL se memória insuficiente (tratado como runtime error)
- `kof_free` em ponteiro NULL é no-op
- `kof_free` em ponteiro já liberado é comportamento indefinido (future: GC resolve)

**JVM:** Delega para `new` bytecode / JVM allocator
**Native:** Implementação via `malloc`/`free` ou arena allocator

### 3.2 Object Model

Todo objeto Kof possui:

| Campo | Tamanho | Descrição |
|-------|---------|-----------|
| type_id | 4 bytes | Identificador do tipo (índice em type table) |
| flags | 4 bytes | Flags do objeto (mark bits, etc.) |
| fields... | variável | Dados dos campos na ordem de declaração |

**Contrato:**
- O tipo_id é determinado em compile-time pelo compilador
- O field layout é determinado em compile-time pelo ClassLayout
- O object header NÃO é acessível pelo código Kof
- O acesso a fields é via offset calculado em compile-time

**JVM:** O object header é gerenciado pela JVM (klass pointer + mark word)
**Native:** O object header é parte do kof-runtime nativo

### 3.3 Field Layout

Cada classe possui um layout de campos calculado em compile-time:

```
FieldLayout:
  - className: String
  - fields: List<FieldInfo>
    - name: String
    - type: Type
    - offset: int (bytes do início do objeto)
    - size: int (tamanho em bytes)
```

**Contrato:**
- Fields são ordenados na ordem de declaração
- Cada field tem offset e tamanho determinados pelo ClassLayout
- O compilador usa o ClassLayout para gerar código de acesso a fields
- O NativeBackend consome o ClassLayout (não calcula offsets inline)

### 3.4 Strings

Uma string Kof é representada como (KofString):

```
KofString:
  - type_id: 4 bytes (= 1)
  - flags: 4 bytes (= 0)
  - length: int (4 bytes, byte length UTF-8)
  - padding: 4 bytes
  - bytes: UTF-8 data (length bytes)
  - null terminator: 1 byte
```

**Contrato:**
- Strings são imutáveis
- Encoding é UTF-8
- Length é byte length (não codepoint count)
- String literals são criadas via kof_string_from_literal
- `println` e `print` aceitam KofString
- `+` em strings produz concatenação (futuro: via kof_string_concat)
- `==` em strings produz igualdade (futuro: via kof_string_equals)

**Runtime Functions (Native):**

| Função | Assinatura | Descrição |
|--------|-----------|-----------|
| `kof_string_from_literal` | (data_ptr, byte_length) → str_ptr | Cria KofString de literal estático |
| `kof_string_length` | (str_ptr) → int | Retorna byte length |
| `kof_string_concat` | (str1, str2) → str3 | Concatena duas strings |
| `kof_string_equals` | (str1, str2) → bool | Compara conteúdo byte a byte |
| `kof_print_string` | (str_ptr) | Imprime usando length armazenado |
| `kof_println_string` | (str_ptr) | Imprime + newline |
| `kof_memcpy` | (dest, src, n) | Copia n bytes |

**JVM:** Delega para java.lang.String
**Native:** Implementação via kof-runtime (NativeRuntime.java)

### 3.5 Arrays

Um array Kof é representado como:

```
ArrayObject:
  - header (type_id, flags)
  - length: int (4 bytes)
  - element_size: int (4 bytes)
  - elements: bytes (length * element_size)
```

**Contrato:**
- `array.length` retorna o número de elementos
- `array[i]` acessa o elemento no offset `header_size + i * element_size`
- Acesso fora dos limites gera runtime error
- Arrays de tipos primitivos armazenam valores diretamente
- Arrays de tipos de referência armazenam ponteiros

**JVM:** Delega para arrays nativos da JVM
**Native:** Implementação via kof-runtime

### 3.6 Method Dispatch

| Tipo | Descrição | Mecanismo |
|------|-----------|-----------|
| FUNCTION | Função top-level | Chamada direta (link-time) |
| STATIC | Método estático | Chamada direta (link-time) |
| INSTANCE | Método de instância | Chamada direta (futuro: virtual) |
| CONSTRUCTOR | Construtor | Chamada direta |

**Contrato:**
- FUNCTION e STATIC são resolvidos em compile-time
- INSTANCE usa virtual dispatch via vtable (método em `method_table_ptr`)
- INTERFACE usa a mesma vtable (dispatch por interface, Fase F.5)

**JVM:** JVM handle diretamente via vtable
**Native:** Chamada direta via `call ClassName_methodName`

### 3.7 Constructors

**Contrato:**
1. Alocação do objeto (`kof_alloc`)
2. Inicialização do header (type_id, flags)
3. Chamada do construtor (`<init>`)
4. O construtor recebe `this` como primeiro argumento
5. O construtor pode chamar `super.<init>()`

**JVM:** `NEW` + `DUP` + `INVOKESPECIAL <init>`
**Native:** `kof_alloc` + init header + `call ClassName_<init>`

### 3.8 Erros de Runtime

| Erro | Descrição | Comportamento |
|------|-----------|---------------|
| `kof_null_error()` | Acesso a ponteiro NULL | Termina com mensagem |
| `kof_bounds_error(i, len)` | Index out of bounds | Termina com mensagem |
| `kof_panic(message)` | Erro genérico | Termina com mensagem |
| `kof_alloc_error()` | Falha de alocação | Termina com mensagem |

**Contrato:**
- Erros de runtime são fatais (não há recovery nesta fase)
- Cada erro produz uma mensagem descritiva
- O processo é terminado com código de saída != 0

**JVM:** Pode usar exceções Java futuramente
**Native:** Syscall exit com mensagem de erro

---

### 3.9 ABI de Erasure de Primitivo / Caixa de Nullable

`Nullable(primitivo)` é um domínio de valor real em todos os targets —
`Absent | Present(T)`. A ausência **não** é sentinela roubada: `Absent` é um
valor distinto de `Present(0)`, `Present(false)` e `Present(0.0)`.

| Target | Representação física de `T?` |
|---|---|
| JVM | referência de wrapper (`Integer`/`Boolean`/…) ou `null` |
| Script | valor do host ou `null` |
| JS | valor dinâmico ou `null` |
| Native | `RuntimeErasureBox*` ou ponteiro `0` |

A fronteira `T → T?` é a chamada compartilhada `kof_box`
(`CompilerEmissionHelpers.emitErasureBox`), nunca `Wrapper.valueOf` direto.
`Script`/`JS` baixam `kof_box` para a identidade, então o valor continua
primitivo do host lá.

**Layout da caixa (Native):** 24 bytes, preenchidos pelo runtime x86-64
(`RuntimeErasureBox.java`); o RISC-V espelha em `NativeRiscvAsmRtB49.java`, e
o AArch64 herda pelo tradutor cross.

```text
+0   MAGIC   0x4B4F46425F425801
+8   tag
+16  value
```

| tag | payload |
|---|---|
| 0 | Int / Char / Short / Byte |
| 2 | Long |
| 3 | Bool |
| 4 | Double |
| 5 | Float |

**Operações**

| Função | Contrato |
|---|---|
| `kof_box_int` / `_long` / `_bool` / `_float` / `_double` | constrói a caixa a partir do primitivo cru |
| `kof_unbox_<t>` (strict) | aceita só caixa válida da tag esperada. Caixa malformada ou tag trocada é **erro de runtime honesto** — nunca `tipo errado → default` |
| `kof_unbox_<t>_soft` | caixa → abre; cru → passa cru; `null` → erro de uso |
| `kof_box_equals(l, r)` | igualdade **por valor** lifted: `null == null` → `1`; `null` vs presente → `0`; caixa vs caixa → payload (Int/Long cross-tag comparam igual) |
| `kof_box_to_string` | entende as tags 0, 2, 3, 4, 5 |

`Char?` não tem tag própria — a tag 0 é compartilhada com Int/Short/Byte, então
`kof_box_to_string` sozinho não distingue `Int(65)` de `Char('A')`. Isso não
exige um segundo ABI: o lowering ainda conhece o tipo estático, e a face `Char`
desempacota em largura int e formata como caractere.

**JVM:** `kof_box`/`kof_unbox` são o par `valueOf`/`xxxValue` do wrapper e a
caixa é o próprio wrapper do JDK — o layout acima é só do Native.

---

## 4. Calling Convention (Native)

O NativeBackend usa System V AMD64 ABI:

| Registrador | Uso |
|-------------|-----|
| %rdi | 1º argumento (this em métodos de instância) |
| %rsi | 2º argumento |
| %rdx | 3º argumento |
| %rcx | 4º argumento |
| %r8 | 5º argumento |
| %r9 | 6º argumento |
| %rax | Valor de retorno |

**Contrato:**
- `this` é passado como primeiro argumento (%rdi)
- Valores de retorno em %rax
- Caller-save: %rax, %rcx, %rdx, %rsi, %rdi, %r8, %r9, %r10, %r11
- Callee-save: %rbx, %rbp, %r12, %r13, %r14, %r15

---

## 5. Type Table

O compilador gera uma type table que mapeia type_id para metadata:

```
TypeTable:
  - types: Array<TypeEntry>
    - name: String (nome interno do tipo)
    - size: int (tamanho total do objeto em bytes)
    - field_count: int
    - fields: Array<FieldEntry>
```

**Contrato:**
- type_id 0 é reservado para "unknown"
- type_id é único por tipo
- A type table é gerada pelo compilador e embutida no binário

**JVM:** Não necessária (JVM possui reflection)
**Native:** Embutida na seção `.data` do assembly

---

## 6. Decisões Arquiteturais

### 6.1 Heap vs Stack
- **Decisão:** Objetos são alocados no heap via `kof_alloc`
- **Motivo:** Permite referências, herança, GC mark-sweep (implementado 03/09, manual)
- **Exceção:** Valores primitivos locais permanecem na stack

### 6.2 UTF-8 vs UTF-16
- **Decisão:** Strings são UTF-8
- **Motivo:** Compatibilidade com C/POSIX, menor uso de memória
- **Trade-off:** Operações de índice por codepoint são O(n)

### 6.3 Imutabilidade de Strings
- **Decisão:** Strings são imutáveis
- **Motivo:** Segurança, hash consistency, internamento
- **Trade-off:** Concatenação requer nova alocação

### 6.4 Direct Dispatch (por agora)
- **Decisão:** Método dispatch é direto (não virtual)
- **Motivo:** Simplicidade, o subconjunto atual não precisa de virtual dispatch
- **Futuro:** O object model permite adicionar vtable depois

### 6.5 Error Handling Fatal
- **Decisão:** Erros de runtime são fatais
- **Motivo:** Simplicidade, não há try/catch na linguagem ainda
- **Futuro:** Exceções podem ser adicionadas com a mesma ABI

---

## 7. Fronteira entre Compiler e Runtime

| Responsabilidade | Compiler | Runtime |
|-----------------|----------|---------|
| Tamanho do objeto | Calcula via ClassLayout | Usa o tamanho |
| Offset dos fields | Calcula via FieldLayout | Usa o offset |
| Alocação | Gera chamada kof_alloc | Executa kof_alloc |
| Inicialização | Gera chamada <init> | Executa construtor |
| Acesso a field | Gera código com offset | — |
| Chamada de método | Gera call com nome mangleado | — |
| Erro de runtime | — | Gera mensagem e exit |

---

> **Atualizado (0.0.5):** virtual dispatch (vtable), exceptions reais
> (JVM table + Native frame chain), generics por erasure e `spawn` (JVM)
> foram implementados. Ainda fora: GC (Native), reflection, serialization.
>
> **Atualizado (0.2.6-beta, 31/08):** `spawn`/`await` no Native foi
> implementado (CONC001 fechado — `pthread_create` + trampoline +
> `pthread_join` + allocator thread-safe com futex). GC automático sob exaustão
> ✅ pousou 19/09 (D1-A, §260 FECHADO; mark-sweep implementado 03/09 + `kof_gc_collect_now`
> manual; free-list `kof_free_head` reusa `mmap`).

## 8. NÃO incluído nesta ABI

- Garbage collection automática (Native — free-list `kof_free_head` com
  reuso `mmap`; memória devolvida ao SO no `munmap` fallback/exit)
- Reflection
- Serialization
