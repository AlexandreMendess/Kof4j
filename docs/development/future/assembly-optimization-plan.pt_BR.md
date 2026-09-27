[English](assembly-optimization-plan.md) | [Português](assembly-optimization-plan.pt_BR.md)

# Otimização de Assembly Cross-Target

**Estado:** Plano futuro — somente design, **zero código**
**Local:** `docs/development/future/`
**Natureza:** arquitetura, contratos, estratégia de implementação e critérios de promoção
**Escopo:** backend Native do Kof em múltiplas ISAs
**Alvos:** x86_64, aarch64, riscv64 e futuros backends Native
**Princípio:** otimizar antes do assembly específico da ISA sempre que possível; especializar no backend somente quando a arquitetura exigir.

---

## 1. Objetivo

Estabelecer uma estratégia de otimização para o backend Native do Kof capaz de produzir assembly pequeno, eficiente e previsível em diferentes arquiteturas sem transformar cada backend em um compilador independente.

O objetivo não é simplesmente emitir menos instruções.

O objetivo é preservar a intenção do programa através da pipeline:

```text
Kof
 ↓
AST
 ↓
Kof IR
 ↓
análise semântica
 ↓
otimizações independentes de ISA
 ↓
lowering
 ↓
otimizações específicas da ISA
 ↓
register allocation
 ↓
instruction selection
 ↓
assembly
```

A mesma construção Kof deve possuir uma representação intermediária suficientemente rica para permitir otimizações comuns, enquanto cada backend pode explorar características próprias da arquitetura de destino.

---

# 2. Princípios

## 2.1 Assembly não é o nível primário de otimização

O Kof não deve depender de transformar assembly já emitido como mecanismo principal de otimização.

Preferencialmente:

```text
AST
 ↓
IR
 ↓
IR otimizado
 ↓
backend
 ↓
assembly
```

e não:

```text
AST
 ↓
assembly ruim
 ↓
assembly optimizer
 ↓
assembly menos ruim
```

O assembly final pode possuir peephole optimization, mas isso deve ser uma última camada.

---

## 2.2 Otimização deve ser cross-target por padrão

Uma transformação deve ser implementada uma única vez quando sua validade independe da arquitetura.

Exemplos:

* constant folding;
* constant propagation;
* dead-code elimination;
* unreachable-code elimination;
* common subexpression elimination;
* simplificação algébrica;
* eliminação de temporários;
* propagação de cópias;
* redução de branches;
* análise de efeitos;
* eliminação de operações redundantes.

A arquitetura só deve participar quando a transformação depender da ISA, ABI ou modelo de memória.

---

## 2.3 O backend não deve conhecer a semântica inteira da linguagem

O backend deve receber uma representação já suficientemente reduzida.

O backend deve responder:

> “Como represento esta operação nesta ISA?”

e não:

> “O que o programa quis dizer?”

Isso mantém a fronteira:

```text
Kof IR
   │
   │ semântica
   ▼
Optimizer
   │
   │ operação reduzida
   ▼
Target Lowering
   │
   ▼
ISA
```

---

# 3. Camadas de otimização

A pipeline deve ser dividida em camadas explícitas.

## O1 — otimizações semânticas

Independentes de arquitetura.

Exemplos:

```text
1 + 2
↓
3
```

```text
x * 0
↓
0
```

```text
if (false) {
    ...
}
↓
remover bloco
```

```text
x = y
return x
↓
return y
```

Estas transformações devem operar sobre o IR.

---

## O2 — otimizações estruturais

Transformam o fluxo de controle e representação das operações.

Incluem:

* basic block simplification;
* branch folding;
* block merging;
* unreachable block removal;
* jump threading;
* loop simplification;
* loop invariant code motion;
* strength reduction;
* induction variable simplification.

Exemplo:

```text
if (condition) {
    return 10;
} else {
    return 20;
}
```

deve chegar ao backend já representado como um fluxo mínimo.

---

# 4. O3 — otimizações de memória

O Kof deve reduzir loads e stores desnecessários antes do lowering específico da ISA.

Exemplo:

```text
store x
load x
```

quando não existe nenhuma operação capaz de alterar `x` entre os dois pontos:

```text
valor já disponível
```

O backend não deve precisar gerar:

```asm
store
load
```

para depois tentar eliminar as duas instruções.

A análise deve ocorrer no IR.

---

# 5. O4 — register allocation

O Kof deve possuir uma etapa explícita de alocação de registradores.

Objetivo:

```text
IR temporaries
       ↓
virtual registers
       ↓
register allocation
       ↓
physical registers
```

A implementação inicial pode utilizar uma estratégia simples e determinística.

Posteriormente podem ser introduzidos:

* liveness analysis;
* interference graph;
* linear scan;
* graph coloring;
* spill heuristics;
* register class constraints;
* caller/callee-saved awareness.

A escolha da estratégia não deve fazer parte do contrato da linguagem.

---

# 6. O5 — target lowering

Depois das otimizações cross-target, operações abstratas devem ser convertidas para operações específicas da ISA.

Exemplo:

```text
ADD
```

pode virar:

```text
x86_64 → add
aarch64 → add
riscv64 → add
```

Mas algumas operações podem exigir sequências completamente diferentes.

O lowering deve encapsular essas diferenças.

---

# 7. O6 — otimizações específicas da ISA

Cada backend pode possuir otimizações próprias.

## x86_64

Possíveis otimizações:

* `lea` para operações aritméticas apropriadas;
* escolha entre `mov`, `lea` e operações combinadas;
* utilização eficiente de flags;
* redução de instruções para constantes;
* seleção de instruções com menor custo;
* utilização futura de SSE/AVX quando suportado.

Exemplo conceitual:

```text
a + b * 4
```

pode ser representado por:

```asm
lea rax, [rdi + rsi*4]
```

quando a situação permitir.

---

## AArch64

Possíveis otimizações:

* uso de immediate forms;
* addressing modes;
* combinação de shift + arithmetic;
* escolha adequada entre `add`, `sub`, `lsl` etc.;
* exploração de registradores temporários;
* instruções de extensão incorporadas ao acesso à memória.

Exemplo conceitual:

```text
load 32-bit signed value
extend to 64-bit
```

pode ser representado diretamente por uma instrução de load com extensão apropriada quando disponível.

---

## RISC-V 64

Possíveis otimizações:

* utilização de immediate forms;
* redução de instruções de materialização de constantes;
* addressing sequences;
* seleção apropriada de `addi`, `slli`, `add`, etc.;
* utilização de extensões disponíveis quando suportado pelo target;
* controle explícito de sequences afetadas por linker relaxation.

O backend não deve assumir que uma otimização válida em x86 existe em RISC-V.

---

# 8. Constant folding

Operações constantes devem ser resolvidas antes da geração de assembly.

Exemplo:

```kof
var x = 10 * 20
```

deve resultar em:

```text
x = 200
```

e não em:

```asm
mov ...
mov ...
imul ...
```

A otimização deve ocorrer no IR.

---

# 9. Constant propagation

Quando um valor é conhecido:

```text
x = 10
y = x + 2
```

deve tornar-se:

```text
y = 12
```

quando não houver efeitos que impeçam a propagação.

---

# 10. Dead Code Elimination

Código cujo resultado não possui observadores deve ser removido.

Exemplo:

```text
x = expensive()
return 42
```

Se `x` nunca for utilizado e `expensive()` não possuir efeitos observáveis:

```text
return 42
```

A análise de efeitos deve ser explícita.

O Kof não deve remover chamadas que possam:

* alterar memória observável;
* executar I/O;
* alterar estado global;
* acessar FFI;
* provocar efeitos definidos pelo runtime.

---

# 11. Copy propagation

Sequências como:

```text
a = b
c = a
```

podem ser reduzidas para:

```text
c = b
```

Isso também reduz pressão sobre registradores.

---

# 12. Strength reduction

Operações caras podem ser substituídas quando semanticamente equivalentes.

Exemplo:

```text
x * 2
```

pode tornar-se:

```text
x << 1
```

quando a semântica do tipo permitir.

Porém, a transformação não deve ser aplicada cegamente.

O custo real da operação depende da ISA e do contexto.

---

# 13. Peephole optimization

Uma pequena etapa final pode observar sequências específicas de instruções.

Exemplo:

```asm
mov rax, rbx
mov rcx, rax
```

pode ser reduzido para:

```asm
mov rcx, rbx
```

quando seguro.

Peephole optimization deve permanecer pequena.

Ela não deve virar uma segunda implementação do compilador.

---

# 14. ABI e otimização

A ABI faz parte do contrato do backend.

Otimizações nunca podem alterar incorretamente:

* registradores de argumento;
* registradores de retorno;
* caller-saved;
* callee-saved;
* stack alignment;
* layout de parâmetros;
* layout de structs;
* regras de retorno;
* convenções de chamada externas.

Especialmente para FFI:

```text
Kof
 ↓
ABI lowering
 ↓
C / foreign function
```

deve produzir exatamente a convenção esperada pelo alvo.

Uma otimização que quebra ABI é incorreta mesmo que produza assembly aparentemente melhor.

---

# 15. Structs e layout

Otimizações devem preservar o layout definido pelo contrato da ABI.

Para:

```c
struct Pair {
    int a;
    int b;
};
```

o backend deve manter:

```text
a = 32 bits
b = 32 bits
total = 64 bits
```

Otimizações podem eliminar loads/stores redundantes, mas não podem alterar o layout observável por FFI.

---

# 16. Cross-target equivalence

O mesmo programa Kof deve produzir resultados semanticamente equivalentes em:

```text
x86_64
aarch64
riscv64
```

Não é necessário que o assembly seja idêntico.

É necessário que:

```text
resultado
efeitos observáveis
ABI
layout
semântica
```

sejam equivalentes.

Portanto:

```text
mesmo IR
   ↓
┌──────────┬──────────┬──────────┐
x86_64     AArch64    RISC-V
 ↓           ↓          ↓
asm A       asm B      asm C
```

é esperado.

---

# 17. Benchmark-driven optimization

Nenhuma otimização específica de ISA deve ser promovida apenas porque “parece melhor no assembly”.

Cada otimização relevante deve possuir:

1. caso mínimo;
2. assembly esperado ou propriedades esperadas;
3. benchmark;
4. comparação antes/depois;
5. validação de corretude;
6. cobertura nos targets suportados.

O objetivo é evitar otimizações cosméticas.

Assembly menor também não significa necessariamente código mais rápido.

---

# 18. Métricas

O backend deve acompanhar, quando possível:

* tamanho do `.text`;
* número de instruções;
* número de loads;
* número de stores;
* número de branches;
* número de spills;
* número de chamadas;
* uso de registradores;
* tempo de execução;
* tamanho final do ELF.

A métrica deve ser escolhida conforme o objetivo da otimização.

---

# 19. Debuggabilidade

As otimizações não devem destruir completamente a capacidade de depuração.

Quando debug estiver habilitado, o backend deve preservar informações suficientes para relacionar:

```text
Kof source
    ↓
IR
    ↓
machine instruction
```

Otimizações podem remover variáveis ou fundir operações, mas isso deve ser representado corretamente nas informações DWARF futuras.

---

# 20. Determinismo

A geração de assembly deve ser determinística.

Dado:

```text
mesmo source
mesmo target
mesmas flags
mesma versão do compilador
```

o resultado deve ser reproduzível.

Isso facilita:

* testes;
* debugging;
* comparação de assembly;
* benchmarks;
* releases;
* investigação de regressões.

---

# 21. Fases de implementação

## Fase A — infraestrutura

* definir contratos do optimizer;
* definir representação de basic blocks;
* definir virtual registers;
* definir análise de uso;
* definir análise de efeitos;
* definir pipeline de passes.

**Critério:** nenhum backend existente pode perder comportamento.

---

## Fase B — otimizações básicas

Implementar:

* constant folding;
* constant propagation;
* copy propagation;
* dead-code elimination;
* unreachable-code elimination;
* basic block simplification.

**Critério:** redução mensurável de instruções em fixtures representativas.

---

## Fase C — register allocation

Implementar:

* liveness;
* virtual registers;
* allocation;
* spill;
* caller/callee-saved handling.

**Critério:** redução de loads/stores artificiais e manutenção da ABI em todos os targets.

---

## Fase D — target lowering

Formalizar:

```text
Kof IR operation
       ↓
Target lowering
       ↓
Machine instruction
```

Cada backend deve declarar explicitamente quais operações suporta.

---

## Fase E — ISA optimization

Adicionar otimizações específicas:

```text
x86_64
AArch64
RISC-V
```

sem contaminar o IR com detalhes desnecessários de uma única arquitetura.

---

## Fase F — benchmark suite

Criar uma suíte cross-target contendo:

* aritmética;
* loops;
* branches;
* chamadas;
* structs;
* arrays;
* memória;
* FFI;
* operações bitwise;
* constantes;
* casos com alta pressão de registradores.

Cada benchmark deve ser executado nos targets disponíveis.

---

# 22. Critérios de promoção

Uma otimização pode sair de `future/` quando:

* possui especificação clara;
* possui implementação;
* possui testes de corretude;
* possui cobertura cross-target quando aplicável;
* não altera contratos de ABI;
* não introduz comportamento dependente de target no código cross-target;
* possui benchmark quando houver impacto de performance;
* não degrada significativamente outro target sem justificativa explícita;
* possui documentação do comportamento esperado.

Uma otimização que melhora x86_64 mas degrada AArch64 e RISC-V não deve ser tratada como otimização universal.

Ela deve ser explicitamente classificada como target-specific.

---

# 23. Regra de ouro

O backend deve preferir:

```text
semântica correta
    ↓
IR bom
    ↓
otimização geral
    ↓
lowering correto
    ↓
otimização específica
    ↓
assembly eficiente
```

e evitar:

```text
assembly gerado de forma ingênua
    ↓
centenas de patches
    ↓
peephole infinito
    ↓
backend impossível de manter
```

---

# 24. Resultado esperado

A arquitetura final deve permitir que uma única implementação Kof produza código nativo adequado para múltiplas arquiteturas:

```text
                     Kof
                      │
                      ▼
                     IR
                      │
             ┌────────┴────────┐
             │                 │
       otimizações         análise
       cross-target        semântica
             │                 │
             └────────┬────────┘
                      ▼
                Target Lowering
                      │
        ┌─────────────┼─────────────┐
        ▼             ▼             ▼
     x86_64         AArch64       RISC-V
        │             │             │
        ▼             ▼             ▼
      ASM           ASM           ASM
        │             │             │
        └─────────────┼─────────────┘
                      ▼
                   linker
                      │
                      ▼
                 executável
```

O Kof continua sendo responsável por sua própria geração nativa. C, LLVM ou outras linguagens/toolchains não são necessários como intermediários obrigatórios.

Ferramentas externas podem continuar existindo na borda do sistema para assembler, linker, debug ou interoperabilidade, mas **não definem a arquitetura do compilador Kof**.

---

# 25. Fora de escopo inicial

Este plano não exige inicialmente:

* SIMD automático;
* auto-vectorization;
* PGO;
* JIT;
* superoptimization;
* instruction scheduling avançado;
* speculative optimization;
* otimização agressiva de floating point;
* geração de código específica para cada microarquitetura;
* substituição do assembler/linker do sistema.

Esses recursos podem ser adicionados posteriormente quando houver evidência de necessidade.

---

## Estado final pretendido

O backend Native deve produzir assembly que seja:

**correto, pequeno, previsível, ABI-compatible, cross-target e competitivo**, sem sacrificar a simplicidade arquitetural do Kof.

A regra fundamental permanece:

> **Kof não precisa gerar o assembly mais complexo possível. Precisa gerar o assembly necessário, corretamente e sem complexidade acidental.**
