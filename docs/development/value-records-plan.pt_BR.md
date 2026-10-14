[English](value-records-plan.md) | [Português](value-records-plan.pt_BR.md)

# Value Records — tipos-valor first-class (EM DESENVOLVIMENTO · TIER 2.7)

estado:
last: PROMOVIDO 14/10 de `docs/development/future/` (ordem da mantenedora "puxa um de future" + `D-FUTURE-UNFREEZE`)
fazendo: os passos 2.7.3 (a ABI Native) + 2.7.4 (a ABI JS) POUSADOS 14/10 — a decisão da ABI Native: a representação ATUAL de heap permanece (um value record é imutável e compara por campos, então a semântica de valor já está entregue; pass-by-value é invisível pela linguagem e fica EXPLICITAMENTE adiado — a flag viaja no IRClass para uma passada futura de alocação); a decisão da ABI JS: um `value record` é emitido com `Object.freeze(this)` no fim do seu construtor (um record comum NÃO é congelado) — invisível ao código Kof (records são imutáveis) e entregando a garantia de não-mutação no nível do objeto JS. A flag viaja em `IRClass.isValueRecord` (carregada pelos rebuilds do Optimizer e do JsBackend — esses rebuilds PERDIAM o `isRecord` antes, bug latente agora consertado). fazendo: passo 2.7.2 (a ABI JVM) POUSADO 14/10 — o modificador `value` é carregado do parse para o símbolo da classe (`SymbolTable.ClassSymbol.isValueRecord`, lido de `rec.modifiers()` no `SymbolTableBuilder`), então 2.7.3/2.7.4 e o futuro erro de compilação de identidade podem consultá-lo; a ABI JVM (R7, decidida durante a implementação por `D-VALUE-RECORDS-GO`) é a classe record COMUM — igualdade estrutural já é o contrato do record, coleções guardam value records BOXADOS, e o Java 25 não tem value classes estáveis, então nenhuma mudança de layout é possível. `ValueRecordFrontEndE2ETest` 6/6 (acresce a flag do símbolo e a paridade de coleções/chave de Map). fazendo: passo 2.7.1 (a keyword de front-end `value`) POUSADO 14/10 — `value record Nome(campos)` parseia e tipa exatamente como `record` + o modificador `value`, SEM semântica de identidade ainda (isso é 2.7.2–2.7.4, pela fila do roadmap). `value` é keyword CONTEXTUAL (IDENTIFIER no lexer, como `sealed`): `ParseContext.valueModifierAhead()` o torna modificador SÓ antes de `record`, então o campo `value` das caixas de captura, o `value` do `kof.ui` e qualquer local chamado `value` seguem funcionando (aditivo, R2). PortuKof `valor registro` mapeia pelo vocabulário (`value`→`valor`). `ValueRecordFrontEndE2ETest` 4/4 (JVM, nativo, a guarda do identificador e a face PortuKof); RED-first (modificador desabilitado → `PARSE010`).
próximo: passo 2.7.5 (paridade + docs: células de conformância `valuerecord` + matriz de paridade + `training/` + `learn/`) — o ÚLTIMO passo; depois dele o plano está completo e promovível para `docs/`.
local: docs/development/value-records-plan.pt_BR.md
dona: 192.168.100.14:0, flatt CLI, sem servidor opencode neste host; porta n/a
estado: under-development

**Estado:** EM DESENVOLVIMENTO — promovido de `future/` 14/10 (ordem da mantenedora; `D-FUTURE-UNFREEZE`). A frente está autorizada (`D-VALUE-RECORD` 16/09 aceita; `D-VALUE-RECORDS-GO` 28/09 resolvida: `value` aplica SÓ a `record`, coleções guardam boxado, uso onde identidade é exigida = erro de compilação). **O passo 2.7.1 (a keyword de front-end) está POUSADO**; 2.7.2–2.7.5 restam.
**Fonte:** `DECISIONS.md` §D-VALUE-RECORD (16/09, aceita) · issue #275 ·
`../roadmap.md` §23 TIER 2.7 (fila de passos 2.7.1–2.7.5)

## 1. Objetivo

Permitir que o programador declare que um agregado definido pelo usuário tem
**semântica de valor e nenhuma identidade de objeto observável** — para tipos
pequenos orientados a dados (vetores, coordenadas, cores, intervalos, tokens de
parser, estado de iterador), onde uma alocação de objeto separada adiciona
pressão de alocação, trabalho de GC, indireção e pior localidade de cache.
Depender de escape analysis da JVM não expressa intenção e não vale nos backends
Native/JS.

## 2. Proposta (sintaxe candidata)

```kof
value record Vec2(Float x, Float y)
value record Color(Int r, Int g, Int b, Int a)
```

Um `value record` tem o **mesmo modelo de dados imutável e conciso** de um
`record` existente; o modificador `value` adiciona "sem identidade". O `record`
comum mantém a semântica intacta.

## 3. Contrato (de `DECISIONS.md` §D-VALUE-RECORD)

- Igualdade/hash por campos (já é o contrato de record).
- **Aditivo e retrocompatível** (regra 2 do freeze): código existente continua
  compilando e rodando.
- A linguagem **intencionalmente não garante "alocação na stack"** — a garantia é
  semântica de valor + ausência de identidade; o armazenamento físico é decisão
  do backend.
- Fronteira: **core stdlib**, não pacote oficial (R1).

## 4. ABI por alvo (decisão de escopo antes do código — R7 escopo honesto)

| Alvo | Representação candidata |
|------|--------------------------|
| JVM | value/inline class (semântica de valor no `invokevirtual`, sem identidade `Object`) |
| Native | passagem por valor (struct por valor / registradores) |
| JS | objeto plano congelado (sem identidade) |

A ABI por alvo é uma **decisão de escopo explícita** antes de qualquer código
landar; o alvo JS pode legitimamente landar por último (R7).

## 5. Fila de passos

A ordem executável vive em `../roadmap.md` §23 **TIER 2.7** (2.7.1 keyword
`value` no front-end → 2.7.2 ABI JVM → 2.7.3 ABI Native → 2.7.4 ABI JS → 2.7.5
paridade + docs). Este documento é a justificativa de design; não duplica a fila.

## 6. Questões abertas (decisões da mantenedora)

- `value` vale também para `class`, ou só para `record`?
- Interação com genéricos/coleções (ex.: `List<Vec2>` — achatado ou boxed?)
- Diagnóstico exato quando um `value record` é usado onde a identidade é exigida.
- Se a representação JS é congelada/selada ou um objeto plano.

## 7. O que NÃO fazer

- **Não** prometer alocação na stack nem modelo de ownership/borrowing (não-objetivo
  permanente).
- **Não** mudar a semântica do `record` comum.
- **Não** abrir a frente sem autorização (regra 6 / R12): frentes novas não abrem
  antes do estágio SYSTEMS fechar.
