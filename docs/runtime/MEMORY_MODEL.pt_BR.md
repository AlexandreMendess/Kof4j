[English](MEMORY_MODEL.md) | [Português](MEMORY_MODEL.pt_BR.md)

# MEMORY_MODEL.md — Modelo de Memória do Kof

**Data:** 2 de setembro de 2026
**Status:** Implementado — Fase F.7 + evolução 0.0.5 (allocator com header) + 0.4.0-beta (free-list `kof_free_head` 27/08; mark-sweep implementado 03/09)

---

## 1. Visão Geral

O modelo de memória do Kof usa um **allocator com header de bloco** sobre mmap.

```
kof_alloc(size)
    ↓
mmap (size + 16 header)
    ↓
+0  total_size (16 bytes)
+16 payload (objeto/array/string) — alinhado a 16
    ↓
kof_free(ptr) → munmap(bloco exato)
```

O usuário da linguagem nunca chama `free` — o gerenciamento é decisão do
runtime/compiler. O código Kof é semanticamente independente do mecanismo
de memória do target.

---

## 2. Estratégia

### Alocação

- `kof_alloc(size)` usa `mmap` (`MAP_PRIVATE | MAP_ANONYMOUS`)
- Cada bloco tem header de 16 bytes: **tamanho total mapeado** (header + payload)
- Payload devolvido ao chamador, alinhado a 16 bytes
- Contadores globais: `alloc_count`, `free_count`, `alloc_bytes`, `free_bytes`

### Deallocation

- `kof_free(ptr)` lê o tamanho do header e executa `munmap` do bloco exato
- `kof_free(null)` é no-op seguro
- Contadores atualizados em tempo real

### Ownership

- Objetos são alocados via `kof_alloc`
- Referências são ponteiros diretos
- Sem reference counting (futuro)
- Sem weak references (futuro)

---

## 3. Lifetime de Objetos

| Tipo | Lifetime | Deallocation |
|------|----------|--------------|
| Objeto | Enquanto referenciado | `kof_free` / GC mark-sweep (03/09, `kof_gc_collect_now` manual) |
| Array | Enquanto referenciado | `kof_free` / GC mark-sweep (03/09, manual) |
| String | Enquanto referenciado | `kof_free` / GC mark-sweep (03/09, manual) |
| Method Table | Todo o programa | SO no exit |

Sem GC nesta fase: a memória é devolvida ao SO no exit do processo.
O allocator já possui a estrutura (header com tamanho + contadores) para
a evolução futura: arenas, reference tracking, GC generacional.

---

## 4. Root References

Roots são:
- Variáveis locais na stack
- Campos estáticos (se existirem)
- Registradores durante execução

Objetos referenciados por roots permanecem válidos durante toda a execução.

---

## 5. Runtime Functions

| Função | Propósito |
|--------|-----------|
| `kof_alloc(size)` | Aloca bloco mmap com header de 16 bytes |
| `kof_free(ptr)` | `munmap` do bloco exato (lê tamanho do header) |
| `kof_memstats()` | Imprime `allocs`, `frees`, `live bytes` reais |

---

## 6. Object Header

```
offset 0:  type_id (4 bytes)
offset 4:  flags (4 bytes)
offset 8:  method_table_ptr (8 bytes)
```

O header do objeto não contém informações de memória (sem mark bits,
sem forwarding pointer) — o header de alocação fica 16 bytes antes do objeto.

---

## 7. Arquivos

| Arquivo | Papel |
|---------|-------|
| NativeRuntime.java | `kof_alloc`, `kof_free`, `kof_memstats`, contadores |
| NativeBackend.java | Gera chamadas para funções de runtime |
| ClassLayout.java | Cálculo de tamanho de objetos |

---

## 8. Limitações

1. ~~Sem GC automático sob exaustão~~ ✅ **pousou 19/09** (D1-A, §260 FECHADO — o gatilho agora é SOUND: blanket-spill dos 15 GPRs + gate `kof_spawn_count==0` + flag one-shot; `kof_gc_collect_now` manual também disponível;
   free-list reusa `mmap`, memória devolvida só no `munmap` fallback — ver §9)
2. Sem reference counting
3. Sem weak references
4. Sem finalização de objetos
5. Sem detecção de memory leaks (apenas contadores)
6. `kof_free` ainda não é chamado pelo código gerado (fundação para GC)

---

## 9. Futuro

- Fase G: GC tracing (mark-and-sweep) sobre o header existente
- Arenas internas para alocações curtas
- Reference counting para objetos sem ciclos
- Weak references
- Memory compaction

> **Atualizado (0.2.6-beta, 31/08):** a evolução do GC partiu do allocator
> de header para uma **free-list** (`kof_free_head`) que reusa blocos já
> `munmap`ados/reativados via `mmap` — reduz o custo de `mmap` por alocação
> pequena (gargalo nº 1 do `language-state.md`). O **mark-sweep é pendente**:
> `kof_gc_collect` existe, mas o GC automático foi **desativado após um hang**
> durante a execução; a memória continua sendo devolvida ao SO no
> `munmap` fallback (e no exit). O `spawn`/`await` de 31/08 (pthread) exigeu
> que o allocator virasse **thread-safe** (futex), já que múltiplas threads
> do programa alocam/concorrem sobre o heap.
