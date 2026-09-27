[English](OBJECT_MODEL.md) | [Português](OBJECT_MODEL.pt_BR.md)

# OBJECT_MODEL.md — Modelo de Objetos do Kof

**Data:** 21 de agosto de 2026
**Status:** Definição — Fase F

---

## 1. Visão Geral

O object model do Kof define como objetos são representados na memória, tanto para JVM quanto para Native.

---

## 2. Layout de Objeto

### 2.1 Objeto Genérico

```
+-------------------+
| type_id (4 bytes) |  ← identificador do tipo
+-------------------+
| flags (4 bytes)   |  ← mark bits, GC flags
+-------------------+
| field_0           |  ← primeiro campo (tamanho varia)
+-------------------+
| field_1           |
+-------------------+
| ...               |
+-------------------+
| field_n           |  ← último campo
+-------------------+
```

- **Header size:** 8 bytes (type_id + flags)
- **Alignment:** 16 bytes total (header + fields padding)
- **Field order:** Ordem de declaração no código fonte

### 2.2 Flags

| Bit | Nome | Descrição |
|-----|------|-----------|
| 0 | MARKED | Usado por GC futuro |
| 1 | PINNED | Não pode ser movido |
| 2-31 | Reserved | Para uso futuro |

---

## 3. Tipos Primitivos

Tipos primitivos NÃO são objetos. São valores diretos na stack:

| Tipo | Tamanho | Representação |
|------|---------|---------------|
| bool | 4 bytes | 0 ou 1 |
| byte | 1 byte | sinalizado |
| short | 2 bytes | sinalizado |
| int | 4 bytes | sinalizado |
| long | 8 bytes | sinalizado |
| float | 4 bytes | IEEE 754 |
| double | 8 bytes | IEEE 754 |
| char | 4 bytes | codepoint UTF-32 |

**Nota:** Na stack, todos os valores são tratados como 64-bit slots para alinhamento.

---

## 4. Tipos de Referência

### 4.1 Record

Records são imutáveis e possuem fields definidos pelo usuario:

```kf
record Point(Int x, Int y)
```

**Layout:**
```
+-------------------+
| type_id           |  → Point
+-------------------+
| flags             |
+-------------------+
| x (8 bytes)       |  → offset 8
+-------------------+
| y (8 bytes)       |  → offset 16
+-------------------+
```

**Total:** 24 bytes (8 header + 2 × 8 fields)

### 4.2 Class

Classes são mutáveis e possuem fields + methods:

```kf
class User {
    String name
    Int age
}
```

**Layout:**
```
+-------------------+
| type_id           |  → User
+-------------------+
| flags             |
+-------------------+
| name (8 bytes)    |  → offset 8 (ponteiro para String)
+-------------------+
| age (8 bytes)     |  → offset 16
+-------------------+
```

**Total:** 24 bytes

### 4.3 String

Strings são imutáveis com representação UTF-8:

```
+-------------------+
| type_id           |  → String
+-------------------+
| flags             |
+-------------------+
| length (4 bytes)  |  → número de codepoints
+-------------------+
| padding (4 bytes) |  → alinhamento
+-------------------+
| bytes[]           |  → UTF-8 data + null terminator
+-------------------+
```

### 4.4 Array

Arrays possuem header + elementos contíguos:

```
+-------------------+
| type_id           |  → Array
+-------------------+
| flags             |
+-------------------+
| length (4 bytes)  |  → número de elementos
+-------------------+
| elem_size (4 bytes)| → tamanho de cada elemento
+-------------------+
| elements[]        |  → dados contíguos
+-------------------+
```

---

## 5. Type ID

Cada tipo Kof possui um type_id único atribuído em compile-time:

| type_id | Tipo |
|---------|------|
| 0 | Reserved (unknown/null) |
| 1 | String |
| 2 | Array (base) |
| 10+ | Tipos definidos pelo usuario |

**Contrato:**
- type_id é constante em tempo de execução
- type_id é único por compilation unit
- type_id 0 significa "tipo desconhecido" ou "null"

---

## 6. Representação Nativa

### 6.1 Header do Objeto (x86-64)

```c
struct KofObject {
    uint32_t type_id;   // 4 bytes
    uint32_t flags;     // 4 bytes
    // fields follow...
};
```

### 6.2 String (x86-64)

```c
struct KofString {
    uint32_t type_id;   // 4 bytes (= 1)
    uint32_t flags;     // 4 bytes
    int32_t length;     // 4 bytes
    uint32_t _padding;  // 4 bytes (alinhamento)
    char bytes[];       // UTF-8 data + \0
};
```

### 6.3 Array (x86-64)

```c
struct KofArray {
    uint32_t type_id;   // 4 bytes (= 2)
    uint32_t flags;     // 4 bytes
    int32_t length;     // 4 bytes
    int32_t elem_size;  // 4 bytes
    uint8_t elements[]; // dados contíguos
};
```

---

## 7. Acesso a Fields

### 7.1 Compile-time

O compilador calcula o offset de cada field usando o ClassLayout:

```
offset = HEADER_SIZE + sum(sizes of preceding fields)
```

### 7.2 Native Code

```asm
# Load field "x" from object in %rax
movq 8(%rax), %rbx    # offset 8 = header(8) + 0

# Store field "y" to object in %rax
movq %rcx, 16(%rax)   # offset 16 = header(8) + 8
```

### 7.3 JVM Code

JVM usa `GETFIELD`/`PUTFIELD` com descriptor calculado:
```
GETFIELD Point.x I    # int x
GETFIELD Point.name Ljava/lang/String;  # String name
```

---

> **Atualizado (0.0.5):** herança (F.3), virtual dispatch via vtable (F.4)
> e dispatch de interfaces (F.5) estão implementados em JVM e Native.
> O header real é de 16 bytes: type_id(4) + flags(4) + method_table_ptr(8).
>
> **Atualizado (0.2.6-beta, 31/08):** os objetos são alocados na free-list
> `kof_free_head` (reuso `mmap`); o allocator é **thread-safe** (futex) por
> causa do `spawn` em pthreads. GC mark-sweep implementado 03/09 (manual; ver
> MEMORY_MODEL.md §9) — auto-collect sob exaustão ✅ pousou 19/09 (D1-A, §260 FECHADO;
> `a904317e`).

## 8. Herança (Histórico — implementada em F.3)

Quando implementada:

```
+-------------------+
| type_id           |  → Dog
+-------------------+
| flags             |
+-------------------+
| Animal fields...  |  → campos da superclasse
+-------------------+
| Dog fields...     |  → campos da subclasse
+-------------------+
```

**Regas:**
- Campos da superclasse vêm antes dos campos da subclasse
- O type_id identifica o tipo real do objeto
- Method dispatch usa vtable (futuro)

---

## 9. Virtual Dispatch (Histórico — implementada em F.4)

Quando implementado, cada classe possui uma vtable:

```
VTable:
  - entries: Array<FunctionPointer>
    - [0] = method_0
    - [1] = method_1
    - ...
```

**O objeto não possui ponteiro para vtable no header.** A vtable é consultada pelo compilador em tempo de compilação para determinar o offset correto.

**Alternativa futura:** Se virtual dispatch for necessário, adicionar `vtable_ptr` ao header:
```
+-------------------+
| type_id           |
+-------------------+
| flags             |
+-------------------+
| vtable_ptr        |  → ponteiro para vtable
+-------------------+
| fields...         |
+-------------------+
```

---

## 10. GC Future

> **Implementado 03/09:** mark-sweep existe (`kof_gc_mark`/`kof_gc_sweep`,
> `kof_gc_collect_now` manual). **Auto-collect sob exaustão ✅ pousou 19/09**
> (D1-A, §260 FECHADO: blanket-spill dos 15 GPRs no `kof_gc_collect_now` +
> gate `kof_spawn_count==0` + flag one-shot; `a904317e`).
> O bit de marca abaixo é rastreado no prefixo do bloco do allocator.

O object model DEVE suportar GC:

- **Mark bits** nos flags para mark-and-sweep
- **Pinned objects** para objects que não podem ser movidos
- **Forwarding pointer** pode ser adicionado ao header

NÃO implementar GC nesta fase. Apenas garantir que o layout permite.

---

## 11. Comparação com JVM

| Aspecto | JVM | Kof Native |
|---------|-----|------------|
| Object header | klass ptr + mark word (16 bytes) | type_id + flags (8 bytes) |
| Field layout | Determinado pela JVM | Determinado pelo compilador |
| Method dispatch | vtable em cada classe | vtable (implementado) |
| String | java.lang.String (mutável internamente) | KofString (imutável) |
| Array | Tipos nativos da JVM | KofArray (universal) |
| GC | Generational, concurrent | Nenhum (futuro) |
