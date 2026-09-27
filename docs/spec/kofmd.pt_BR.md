[English](kofmd.md) | [Português](kofmd.pt_BR.md)

# Especificação do Kofmd — Markdown Tipado, Orientado a Intenção e Memória de Agentes (D-KOFMD)

> **Estado: Fase 2 (Especificação) — NORMATIVA para a 0.5.0.** A Fase 2 é a
> superfície congelada decidida em `docs/development/kofmd-plan.md` (+PT) e
> registrada em `DECISIONS.md` §`D-KOFMD`. A Fase 3 (parser) é destravada por
> este documento. Baseada nos achados da Fase 1 medidos contra a árvore real
> (27/09/2026).
>
> **Contrato:** Kofmd = Markdown para humanos + semântica Kof para dados +
> intenção explícita + memória operacional compacta para agentes. Extensão
> `.md`, degradável para Markdown puro, nunca verboso, nunca XML/YAML
> disfarçado. Regra de ouro: **intenção no menor número razoável de palavras,
> sem perder semântica.** `Write intent, not narration.`

As palavras-chave de conformidade **MUST** (DEVE), **MUST NOT** (NÃO DEVE),
**SHOULD** (DEVERIA) e **MAY** (PODE) são usadas conforme a RFC 2119.

---

## 1. Escopo

Este documento especifica o formato Kofmd, sua gramática de nível de
documento, suas regras de tipo e schema, seu vocabulário de memória de agente,
sua forma canônica, seus diagnósticos, sua superfície de tooling (`kof md`,
LSP) e sua interoperabilidade com Markdown.

Tudo que **não** está declarado aqui está fora do escopo do Kofmd 0.5.0. Uma
nova forma de superfície **DEVE** ser decisão explícita da mantenedora (regra 6)
e vir com `spec + exemplo + teste` (plano §59).

## 2. O que o Kofmd é e o que não é

| Kofmd **é** | Kofmd **não é** |
|---|---|
| Markdown com construções tipadas opcionais | um substituto do Markdown |
| Uma superfície de dados e intenção com modelo de tipos Kof | uma segunda linguagem de tipos |
| Degradável para Markdown puro | XML / YAML / JSON disfarçado |
| Memória operacional compacta para agentes | um log, uma transcrição ou chain-of-thought |
| Um vocabulário pequeno e fechado | uma linguagem de metadados aberta ou uma DSL |
| Dados e documentação | conteúdo executável ou um motor de prompt |

## 3. Princípios de design

1. **Intenção antes de apresentação.** Representar *o que significa* e *o que se
   pretende*, não apenas *como é exibido*.
2. **Não obrigar a IA a inferir.** Expor intenção, estado, causa, localização e
   restrições explicitamente em vez de enterrá-los em prosa.
3. **Markdown continua Markdown.** Um documento Markdown comum é um documento
   Kofmd válido com zero construções; as construções Kofmd renderizam como
   texto comum.
4. **Tipo não é intenção.** Um tipo responde *qual é a natureza deste valor?*;
   uma intenção responde *por que esta informação existe?*. Ambos são
   representáveis e **NÃO DEVEM** ser confundidos.
5. **Dado é dado; prosa é prosa.** O que puder ser tipado é tipado; a prosa é
   usada só onde a prosa é genuinamente necessária (explicação, contexto,
   narrativa).
6. **Zero redundância.** Nunca repetir em prosa informação já representada
   estruturalmente.
7. **Menor forma adequada.** Se `next: tests` expressa a intenção, não escrever
   uma frase. Medir por semântica ÷ complexidade (plano §56).
8. **Não inventar.** Chaves desconhecidas são dados inertes, nunca erros e
   nunca executadas. Nenhuma forma de superfície é adotada de outra linguagem
   (regras 8/10).

## 4. Modelo do documento

Um **documento Kofmd** é um arquivo texto UTF-8 com extensão `.md`. Ele é
varrido **linha a linha**, e somente linhas que começam na **coluna 0** podem
carregar construções Kofmd. Linhas dentro de um bloco de código cercado são
sempre conteúdo verbatim.

| Termo | Definição |
|---|---|
| **Linha** | Uma sequência de caracteres terminada por `\n` (uma última linha sem `\n` é válida). |
| **Construção** | Uma linha que casa um dos padrões do §6. |
| **Prosa** | Qualquer linha que não é construção e está fora de um bloco de código. Preservada byte a byte. |
| **Bloco Kofmd** | Uma sequência maximal de linhas de construção consecutivas sem linha em branco, heading, prosa ou cerca de código entre elas. |
| **Campo** | Um par chave/valor tipado dentro de um bloco (ou isolado). |
| **Schema** | Uma declaração `record` (§8) que dá os tipos dos campos. |

Construções e prosa coexistem no mesmo arquivo. A prosa nunca é reescrita pelo
parser; só as construções carregam semântica.

## 5. Estrutura léxica

- A codificação **DEVE** ser UTF-8.
- Uma linha de construção **DEVE** começar na coluna 0. Uma linha indentada é
  prosa/código.
- Linhas em branco separam blocos; não carregam semântica.
- Um bloco de código cercado (```` ``` ```` … ```` ``` ````) é conteúdo de prosa:
  suas linhas **NÃO DEVEM** ser interpretadas como construções.
- Chaves de campo são `snake_case` minúsculo: `[a-z][a-z0-9_]*`.
- Nomes de intenção são minúsculos: `[a-z][a-z0-9_-]*`.
- **Não há** sintaxe de comentário, **não há** diretiva de include e **não há**
  escape inline além de strings entre aspas (§7.2).

## 6. Construções

A superfície da 0.5.0 é exatamente estas quatro construções. Nada mais é
construção.

### 6.1 Campo tipado

```text
key: value
key:
```

Uma linha cuja chave é uma chave de campo válida (§5) seguida de `:`. O valor é
um escalar (§7) ou vazio. Um valor vazio **PODE** ser seguido por uma lista
(§6.2). Um espaço após `:` é canônico; o parser **DEVE** aceitar zero ou mais.

Chaves de campo nunca são reservadas de modo a tornar chaves desconhecidas um
erro: uma chave desconhecida é um TypedField válido e é inerte (§17).

### 6.2 Campo de lista

```text
instructions:
  - inspect
  - preserve-api
```

Um campo com valor vazio seguido por uma ou mais linhas `- item` forma um
**valor de lista**. A indentação canônica é dois espaços; o parser **DEVE**
aceitar coluna 0 e qualquer indentação consistente. Uma lista termina na
primeira linha que não é um item de lista. Listas são canônicas para
`instructions` (§10) e são válidas para qualquer campo.

### 6.3 Intenção de bloco

```text
@decision
```

Uma linha que começa com `@` seguida por um nome de intenção anota o **bloco
seguinte** (o próximo bloco Markdown não vazio: parágrafo, lista, tabela ou
código cercado). Torna explícita a intenção que o bloco exigiria inferência
para recuperar. Múltiplas intenções **PODEM** empilhar; aplicam-se ao mesmo
bloco alvo.

O vocabulário reservado de intenções está no Apêndice A. Um nome de intenção
fora do vocabulário é um diagnóstico (§14), nunca uma extensão silenciosa.

### 6.4 Declaração de schema

```text
record TestResult(Int total, Int passed, Int failed)
```

Uma declaração de schema usa a **sintaxe real de `record` do Kof** (tipo antes
do nome do campo). Deliberadamente é a mesma declaração que o compilador Kof já
parseia; o Kofmd **não** introduz nova sintaxe de declaração de tipo. Um schema
nomeia os tipos dos campos que descreve para que campos tipados possam ser
validados contra ele (§8).

### 6.5 Prosa

Headings, parágrafos, listas, citações, tabelas, links, imagens, ênfase e
blocos de código são Markdown comum e são **preservados com zero semântica**.

## 7. Valores e tipos

### 7.1 Inferência escalar

O Kofmd reusa a superfície real de tipos do Kof — ele **não** inventa
primitivos. Um valor escalar é inferido assim:

| Lexema | Tipo inferido | Exemplo |
|---|---|---|
| `true` / `false` | `Bool` | `stable: false` |
| `-?[0-9]+` cabendo em signed de 32 bits | `Int` | `retries: 3` |
| forma decimal / expoente | `Float` | `ratio: 0.5` |
| `"…"` (entre aspas) | `String` (explícito) | `version: "0.5.0"` |
| qualquer outro token não vazio | `String` (puro) | `location: compiler/parser` |
| vazio (com ou sem lista) | ausente / lista | `next:` |

Regras:

- **Não existe** literal `null`. A ausência é expressa omitindo o campo.
- `Int` é signed de 32 bits. Um literal inteiro maior não é inferido `Int`; é
  tratado como `String` puro até que um schema ou aspas explícitas digam o
  contrário.
- Um `String` puro é um token curto (recomendado path-like ou `kebab-case`).
  Ele **NÃO DEVE** ser usado para contrabandear uma frase; isso é prosa (§3.6).
- Colocar um escalar entre aspas força `String`; as aspas não são preservadas na
  saída canônica a menos que sejam necessárias para preservar um valor que de
  outro modo seria re-tipado (ex.: `"true"`, `"3"`).

### 7.2 Tipos adotados e tipos recusados

**Adotados** (superfície real do Kof): `Bool`, `Int`, `Float`, `String` e
`record` como portador de dados. A nulabilidade se escreve `T?`; erros são
`throw "msg"`.

**Não adotados como tipos de superfície do Kofmd** (guarda de fake-idiom,
`D-KOFMD` item 4): `Option<T>`, `Result<T,E>`, `Date`, `Time`, `DateTime`,
`Duration`, `UUID`, `URL`, `Path`, `Bytes`. Refinamentos escalares declarados
por schema (`date`/`time`/`uuid`/`url`/`path`) validam como `String` mais uma
checagem de formato documentada, nunca como um primitivo novo. Pedidos da lista
recusada são respondidos com o idioma Kof (`String?` + narrowing; `throw`) e
fechados como "Kof não é Java" (regra 8).

## 8. Vínculo de schema e validação

- Um schema é declarado com §6.4. Nomes de campo no schema são as chaves de
  campo que ele valida.
- Um campo tipado cuja chave casa um campo de schema declarado **DEVE** ser
  validado contra o tipo desse campo. Um descasamento é `MD002`.
- Um campo sem schema é validado apenas pela inferência escalar.
- Um campo de schema ausente do documento **não** é erro a menos que o schema o
  marque como obrigatório; marcação de campo obrigatório é **não-meta** para a
  0.5.0 (declarado, não omitido em silêncio).
- Campos desconhecidos nunca são erro. Campos desconhecidos **NÃO DEVEM** ser
  coagidos para um schema.

## 9. Vocabulário de memória de agente

A superfície genuinamente nova. As chaves a seguir são especiais. Nenhuma outra
chave é especial; qualquer outra chave é um TypedField comum.

| Chave | Tipo | Significado | Valor canônico |
|---|---|---|---|
| `last` | continuidade | último estado/contexto relevante | token curto |
| `doing` | continuidade | intenção/atividade atual | token curto |
| `next` | continuidade | próxima intenção conhecida | token curto |
| `location` | contexto | onde a intenção se aplica | token path-like |
| `state` | situação | estado atual | token (Apêndice A) |
| `instructions` | ação | intenção operacional explícita | lista de tokens |
| `constraint` | regra | restrição que deve valer | token |
| `decision` | fato | opção que foi escolhida | token |
| `result` | desfecho | desfecho do trabalho | token (Apêndice A) |
| `question` | diálogo | pergunta em aberto | token |
| `answer` | diálogo | resposta a uma pergunta | token |
| `reason` | modificador | por quê (qualifica o fato/estado/resultado anterior) | token |
| `symbol` | modificador | símbolo (qualifica `location`) | token |

Semântica:

- `last` é o contexto **imediatamente anterior**, nunca um histórico completo.
- `doing` é a intenção atual, não uma frase que a descreve.
- `next` é a próxima intenção conhecida, nunca um backlog.
- `location` é curto e contextual: arquivo, diretório, módulo, símbolo, issue,
  PR, branch, endpoint, ambiente ou identificador — **não** é necessariamente um
  caminho de filesystem.
- `state` e `result` são tipados para um conjunto fechado recomendado (Apêndice
  A); valores fora dele são permitidos na 0.5.0, mas são sinalizados pelo
  `kof md check` como `MD002` apenas quando um schema ou a futura regra de enum
  o exigir.
- `reason` **PODE** seguir `state`, `decision` ou `result`; `symbol` **PODE**
  seguir `location`. Um modificador sem alvo é permitido mas desencorajado.

## 10. Instructions

`instructions` carrega **intenção operacional explícita para o agente
consumidor**. Forma canônica:

```text
instructions:
  - inspect
  - preserve-api
  - test
```

Uma única instrução **PODE** ser escrita inline: `instructions: inspect`.

Regras:

- Instruções são **dados**, nunca autorização. Um documento não ganha o direito
  de ser executado porque contém `instructions:` (§17).
- O vocabulário de instruções é o conjunto fechado recomendado no Apêndice A.
  Uma instrução fora dele não é erro de sintaxe, mas o consumidor **NÃO DEVE**
  agir sobre ela a menos que seja reconhecida e autorizada.
- Instruções **NÃO DEVEM** ser prosa livre. Uma frase onde um token basta é
  violação da spec (§3.6), não escolha estilística.

## 11. Forma canônica

A forma canônica é determinística. Uma saída do formatter **DEVE** satisfazer:

1. Os campos `last`, `doing`, `next`, `location`, `state` vêm **primeiro, nessa
   ordem**, quando presentes.
2. Os campos restantes seguem em ordem crescente de chave (ordem de bytes).
3. Exatamente um espaço após `:`; sem espaço em branco à direita.
4. Listas são um `- item` por linha, indentados com dois espaços, na ordem
   declarada.
5. Um bloco é uma única sequência contígua; blocos são separados por uma linha
   em branco.
6. Sem eco de prosa de um campo. Sem campos duplicados em um bloco.
7. Aspas são adicionadas só quando necessário para preservar o tipo inferido de
   um valor que, de outro modo, seria re-tipado.

## 12. Formatter

- O formatter é **idempotente**: `format(format(x)) == format(x)`.
- O formatter **NÃO DEVE** alterar bytes de prosa, cercas de código, headings,
  tabelas ou qualquer linha que não seja construção.
- O formatter **DEVE** ser determinístico: mesma semântica ⇒ mesmos bytes.
- O formatter é exposto como `kof md format` (§15) e no LSP (§16).

## 13. Interoperabilidade e round-trip

| Direção | Garantia |
|---|---|
| Markdown puro → Kofmd | Sempre válido; zero construções. |
| Kofmd → Markdown | Markdown válido: linhas de construção renderizam como texto comum. A extração semântica é separada da renderização. |
| Kofmd → parse → canônico → parse | **DEVE** preservar semântica (forma canônica idempotente). |
| Kofmd → Markdown → Kofmd | **DEVE** preservar semântica quando as construções são mantidas como linhas literais; se um renderizador as descarta, a perda é documentada, nunca silenciosa. |

Recursos Markdown que o Kofmd **DEVE** preservar: headings, parágrafos, listas,
links, imagens, blocos de código, tabelas, citações e ênfase.

## 14. Diagnósticos

Todo diagnóstico é **nomeado e nunca silencioso** (R6). O namespace é `MDxxx`.

| Código | Nome | Gatilho | 0.5.0 |
|---|---|---|---|
| `MD001` | alvo sem backing | uma capacidade Kofmd é pedida em um alvo que não a implementa (JVM-first) | entregue |
| `MD002` | descasamento de tipo/schema | um tipo inferido/explícito discorda de um tipo de schema, ou um valor inválido para um slot tipado | entregue |
| `MD003` | construção malformada | uma linha em forma de construção falha ao parsear (reservado) | reservado |

Diagnósticos **DEVEM** carregar um número de linha e uma mensagem curta. Eles
**NÃO DEVEM** ser rebaixados a aviso para fazer um gate passar. Chaves
desconhecidas **não** são diagnóstico.

## 15. CLI

A superfície de CLI segue o dispatch existente do `kof-cli` e o padrão
`CmdCheck`. Uma classe por verbo, sujeita ao gate de ≤500 linhas.

```text
kof md check <file>     # parse + validação; exit não-zero em MDxxx
kof md format <file>    # reescreve na forma canônica (idempotente)
```

`kof md convert` é **não-meta** para a 0.5.0 (§21) e é recusado honestamente.

## 16. LSP

O Kofmd pega carona no servidor LSP existente (`LspServer`) — **não há** segundo
servidor. O hook de arquivo `.md` fornece:

- **diagnósticos** para `MDxxx`;
- **hover** para chaves de campo e tipos inferidos.

Completion, go-to-definition e completion completo de schema são **não-metas**
para a 0.5.0 (declarados).

## 17. Segurança

O Kofmd é **dado e documentação**; ele nunca executa código implicitamente.

- O parser e o validador **NÃO DEVEM** avaliar, buscar ou executar qualquer
  conteúdo.
- Não há includes, macros, imports, referências remotas ou avaliação de
  expressão.
- Links, imagens e URLs são dados inertes; nada é dereferenciado.
- Campos desconhecidos são inertes e **NÃO DEVEM** disparar comportamento.
- `instructions` **não** é canal de autorização: o consumidor decide quais
  instruções são válidas e autorizadas. `unknown ≠ execute`.
- Um documento Kofmd **NÃO DEVE** ser tratado como prompt confiável. Conteúdo de
  prompt-injection na prosa não tem status especial.

## 18. Requisitos de teste

A implementação **DEVE** entregar um teste E2E por capacidade (`KofmdE2ETest`)
e um corpus golden, conforme o quality gate (Q0–Q7). A suíte cobre:

```text
kofmd syntax · parser · types · semantics · intent · agent-memory
instructions · markdown compatibility · roundtrip · formatter · schema
ai patterns · diagnostics · lsp
```

Os testes provam as duas direções (`Markdown→Kofmd→Markdown` e
`Kofmd→Markdown→Kofmd`) onde for semanticamente possível. Um teste que cobre só
o caminho feliz é insuficiente (Q3); bordas numéricas, vazias, de limite, de
erro, cross-target, de idempotência e de entrada malformada **DEVEM** ser
cobertas onde aplicável.

## 19. Corpus golden

Um corpus de arquivos pequenos sob `kofmd/`, uma ideia por arquivo:

```text
basic · typed-data · schema · mixed-markdown
agent-memory · agent-task · agent-context · agent-instructions
agent-handoff · agent-blocked · agent-result · agent-decision
```

Cada arquivo é simultaneamente documentação, teste, exemplo e material de
avaliação de IA. Consistência importa mais que volume.

## 20. Regras para IA (normativas)

1. Dados → campos tipados. 2. Intenção → campos semânticos. 3. Estado → estado
explícito. 4. Contexto → `location`. 5. Ação → `instructions`. 6.
Continuidade → `last`/`doing`/`next`. 7. Explicação → prosa Markdown. 8. Não
repetir informação. 9. Não inventar campos. 10. Não inventar tipos. 11.
Preferir estruturas planas. 12. Preferir respostas curtas. 13. Preservar
schema. 14. Preservar intenção.

Uma IA escrevendo Kofmd **NÃO DEVE**: inventar sintaxe, inventar tipos, produzir
estruturas enormes, repetir informação, misturar dado e prosa, quebrar um
schema ou transformar memória em narrativa.

## 21. Não-metas para a 0.5.0

- Campos tipados agregados/por record (só escalares e listas entram).
- Marcação de campo obrigatório, imposição de enum e validação de refinamento
  de valor.
- `kof md convert`, completion pleno do LSP, go-to-definition.
- Tipos primitivos novos ou a lista de tipos recusada (§7.2).
- Promoção do `kof.file`; migração da doc inteira.
- Qualquer sintaxe fora do §6. Tudo além da superfície congelada é recusa
  honesta (R6), não stub (Q7).

## 22. Fases e aceitação

A implementação segue as fatias 3.1→3.9 do plano (cortes verticais completos com
prova). Aceitação para a 0.5.0:

1. Parser verde no corpus golden; prosa preservada byte a byte.
2. Inferência escalar e validação de schema com `MD002` RED→GREEN.
3. Vocabulário de memória de agente validado; forma de lista do `instructions`
   imposta.
4. Round-trip Markdown preserva semântica.
5. `kof md format` idempotente; `kof md check` sai não-zero em `MDxxx`.
6. LSP reporta `MDxxx` e faz hover dos tipos inferidos.
7. Suíte completa verde; zero regressão (quality gate).

---

## Apêndice A — Vocabulários reservados (fechados, só por adição)

**Intenções de bloco** (`@name`):

```text
information · decision · requirement · task · question · answer · result
warning · configuration · example · api · message · explanation · release
```

**Instructions** (conjunto fechado recomendado):

```text
inspect · preserve-api · no-breaking-change · run-tests · update-docs
do-not-refactor · verify-backends · add-tests · implement · test
```

**Conjuntos de valor consultivos** (imposição adiada, §9):

```text
state:  active · blocked · done · failed
result: pass · fail · blocked
```

## Apêndice B — Gramática (EBNF)

```ebnf
document     = { line } ;
line         = block-intent | schema-decl | typed-field | list-item | other ;

block-intent = "@" intent-name , newline ;
schema-decl  = "record" , ws , type-name , "(" , [ schema-field , { "," , ws , schema-field } ] , ")" , newline ;
schema-field = type-name , ws , field-key ;

typed-field  = field-key , ws , ":" , [ ws , scalar ] , newline , [ list-value ] ;
list-value   = { ws , "-" , ws , scalar , newline } ;
scalar       = bool | int | float | quoted-string | bare-string ;

bool         = "true" | "false" ;
int          = [ "-" ] , digit , { digit } ;
float        = [ "-" ] , digit , { digit } , "." , digit , { digit } , [ exponent ] ;
quoted-string= '"' , { char | escape } , '"' ;
bare-string  = non-space , { non-space } ;
intent-name  = lower , { lower | digit | "_" | "-" } ;
field-key    = lower , { lower | digit | "_" } ;
type-name    = upper , { letter | digit | "_" } ;
ws           = { " " | tab } ;
```

`other` é qualquer linha de prosa, heading, cerca de código, tabela ou linha em
branco; é preservada verbatim.

## Apêndice C — Exemplo completo

```text
# Handoff — type-system front

@decision
Adopt flow-sensitive narrowing on `x != null`.

location: compiler/type-system
last: null-safety
doing: ownership
next: lifetime
state: active
instructions:
  - inspect
  - preserve-api
  - add-tests
constraint: no-breaking-change
reason: smaller-diff

Ownership prevents invalid mutable aliasing.
```

- `@decision` aplica-se ao parágrafo seguinte.
- O bloco valida: `state` ∈ conjunto consultivo; `instructions` é lista.
- `reason` qualifica a `constraint`; `location` + `symbol` refinaria um alvo.
- O parágrafo de prosa é preservado intocado.
