[English](IO.md) | [Português](IO.pt_BR.md)

# kof.io — Filesystem API

`kof.io` é a API oficial de filesystem do Kof: arquivos, diretórios e
caminhos com uma única semântica nos targets JVM e Native.

## Tipos

`File`, `Path` e `Directory` representam um caminho (a string do path).
Todas as operações de `kof.io` funcionam nos três tipos — o tipo apenas
orienta a intenção.

## Path

| Operação | Exemplo | Resultado (Linux/macOS) |
|----------|---------|--------------------------|
| `resolve` | `Path("data").resolve("users.txt")` | `data/users.txt` |
| `parent` | `Path("data/users.txt").parent()` | `data` |
| `fileName` | `Path("data/users.txt").fileName()` | `users.txt` |
| `extension` | `Path("data/users.txt").extension()` | `txt` |
| `normalize` | `Path("a/./b/../c").normalize()` | `a/c` |
| `isAbsolute` | `Path("/x").isAbsolute()` | `true` |
| `toAbsolute` | `Path("x").toAbsolute()` | caminho absoluto |

No Windows o separador é `\`; o código Kof nunca concatena separadores.

## File

| Operação | Descrição |
|----------|-----------|
| `exists()` | Bool |
| `isFile()` / `isDirectory()` | Bool |
| `readText()` | `String?` — `null` se falhar (JVM e Native) |
| `writeText(s)` / `appendText(s)` | Bool, UTF-8 |
| `readBytes()` | `Int[]` (0-255), `null` se falhar |
| `writeBytes(b)` / `appendBytes(b)` | Bool |
| `size()` | Long; lança exceção se o arquivo não existe (02/09 — sem sentinela `-1`) |
| `delete()` | Bool (arquivo ou diretório vazio) |
| `name()` / `path()` | String |
| `copyTo(destino)` | Bool — JVM + Native (x86-64/riscv64/aarch64, linha de paridade 13). Copia bytes + atributos básicos. Sem sobrescrita por padrão (devolve `false`, sem alterar nenhum dos dois arquivos, se `destino` já existir); não cria o diretório pai de `destino` implicitamente — quem chama precisa garantir que ele exista |
| `moveTo(destino)` | Bool — JVM + Native. Primitiva de filesystem para mover/renomear, sem sobrescrita por padrão (mesmo contrato de `copyTo`). Não é uma transação segura: quem precisa de mover com verificação de hash continua fazendo copiar → validar → apagar, como já fazia antes deste método existir |
| `modifiedTime()` | Long — JVM + Native. Data de modificação em milissegundos desde a época; lança exceção se o arquivo não existir (mesmo contrato de `size()`, sem sentinela) |
| `isSymlink()` | Bool — JVM + Native. `true` quando o próprio caminho é um link simbólico (o link nunca é seguido implicitamente por essa checagem) |

**Não existe forma estática** para nenhuma face do `kof.io`:
`File.exists(p)`, `File.readText(p)`, `File.readRange(p, o, n)` são rejeitados
pelo typer com `SEM011 Undefined variable or type: 'File'` em **todos** os
alvos (medido 28/09) — use a forma de instância `File(p).exists()`,
`File(p).readRange(0, 4)`. (A tabela `KofIo.staticMethod` do compilador é
inalcançável porque o typer nunca resolve o tipo como receptor estático;
`copyTo`/`moveTo`/`modifiedTime`/`isSymlink` são só de instância.)

## Directory

| Operação | Descrição |
|----------|-----------|
| `exists()` | Bool |
| `create()` | cria; falha se já existe |
| `createDirectories()` | cria recursivamente |
| `list()` | `List<String>` dos nomes, ordenado |
| `delete()` | remove diretório vazio |

```kof
var dir = Directory("data")
dir.createDirectories()
for (var entry in dir.list()) {
    println(entry.name)
}
```

`entry.name` e `entry.path` retornam o próprio entry.

## Exemplo completo

```kof
var path = Path("data/users.txt")
path.parent().createDirectories()
path.writeText("Mel\nKof\n")
var text = path.readText()
println(text)
println(path.size())
```

## Erros e encoding

- Texto: UTF-8 sempre.
- Ausência como valor (02/09): `readText()`/`readFile()` devolvem `String?`
  (`null` para arquivo inexistente) em JVM e Native; `size()` lança exceção
  recuperável (`catch (String e)`) — o `-1` sentinela foi removido.
- Booleanas: `true`/`false`. `size()` lança exceção quando o arquivo não existe (sem `-1`).

## Streaming (`libs/file`)

O `kof.io` também expõe `readRange(offset, len)` (leitura incremental). A
biblioteca pure-Kof oficial `libs/file` constrói streaming sobre ele —
`D-KOF-FILE-GO`; sem sintaxe nova, sem mudança no compilador. A fatia 2
mediu o leitor de bytes em todos os alvos, e a fatia 2.1 adicionou o
`TextStream` (linhas UTF-8) sobre o mesmo modelo (`FileLibraryE2ETest`
13/13).

```kof
import file.FileStream

main() {
    var stream = FileStream("large.log", 4096)   // tamanho do chunk
    var chunk = stream.readChunk()
    while (chunk != null) {
        // processa um chunk de bytes de tamanho fixo; memória fica limitada
        chunk = stream.readChunk()
    }
    println(stream.position())
}
```

| Operação | Descrição |
|----------|-----------|
| `FileStream(path[, chunkSize])` | leitor de bytes por chunks (default 8192) |
| `readChunk()` | `Int[]?` — próximo chunk, `null` no fim do arquivo |
| `done()` | `Bool` |
| `position()` | `Long` bytes consumidos |
| `copyStream(source, destination, chunkSize)` | `Long` bytes copiados, memória constante |

Alvos: JVM, Native (x86-64/riscv64/aarch64) e Script rodam o `readRange`
real (todos medidos contra o mesmo golden). O JS não tem binding de host
para `readRange` (nem `copyTo`/`moveTo`/`modifiedTime`/`isSymlink`): a
chamada recusa em compile time com `IOJS001` (`D-KOF-FILE-GO`), nunca
fallback silencioso de arquivo inteiro nem `SyntaxError` em runtime.

### Texto streaming (`TextStream`, fatia 2.1)

O `TextStream` lê um arquivo como **linhas UTF-8** sobre o mesmo modelo de
chunks — use para logs, CSV, JSONL, datasets. Uma sequência multi-byte
cortada entre dois chunks é remontada em Kof (nunca emitida como bytes
quebrados); uma não terminada no EOF vira U+FFFD. `\n` e `\r\n` são ambos
aceitos (o `\r` é removido); a memória fica limitada a um chunk mais a
linha atual.

```kof
import file.TextStream

main() {
    var stream = TextStream("large.csv", 8192)   // tamanho do chunk
    var line = stream.nextLine()
    while (line != null) {
        // processa uma linha sem carregar o arquivo inteiro
        line = stream.nextLine()
    }
}
```

| Operação | Descrição |
|----------|-----------|
| `TextStream(path[, chunkSize])` | leitor streaming de linhas UTF-8 (default 8192) |
| `nextLine()` | `String?` — próxima linha sem terminador, `null` no fim do arquivo |
| `done()` | `Bool` — fim do arquivo alcançado e sem texto pendente |
| `position()` | `Long` bytes consumidos |

Exato para o BMP na JVM, Native e Script. **Texto não-BMP (astral) é
divergência medida no Native**: o Native armazena strings como UTF-8 e não
tem representação WTF-8 para um par surrogate (`§537`); é exato na JVM e no
Script.

### CSV / TSV (`CsvReader` / `CsvWriter`, fatia 2.2)

O `CsvReader` faz streaming de **registros** sobre o `TextStream` caractere a
caractere, então um campo entre quotes pode conter o delimitador e newlines,
os registros quebram em `\n`/`\r\n`, e a memória fica limitada a um chunk mais
o registro atual. TSV reusa o mesmo leitor com delimitador de tab. O
`CsvWriter` é o inverso: o primeiro `writeRow` trunca o arquivo, os demais
apendam, e um campo só é citado quando contém o delimitador, uma quote, um
newline ou um carriage return (quotes internas são duplicadas). Leitor e
escritor fazem round-trip dos mesmos registros.

```kof
import file.Csv

main() {
    var reader = CsvReader("data.csv", ',', 8192)   // delimitador, tamanho do chunk
    var row = reader.nextRow()
    while (row != null) {
        // row.get(0) é o primeiro campo do registro
        row = reader.nextRow()
    }

    var writer = CsvWriter("out.csv", ',')
    writer.writeRow(listOf("id", "nome"))
    writer.writeRow(listOf("1", "Doe, John"))       // citado automaticamente
}
```

| Operação | Descrição |
|----------|-----------|
| `CsvReader(path[, delimiter[, chunkSize]])` | leitor streaming de registros (delimitador default `,`, chunk 8192) |
| `nextRow()` | `List<String>?` — campos do próximo registro, `null` no fim do arquivo |
| `CsvWriter(path[, delimiter])` | escritor streaming de registros (delimitador default `,`; o primeiro write trunca) |
| `writeRow(cells)` | codifica e apenda um registro terminado por `\n` |

Um `"` abre um campo entre quotes apenas no início de um campo; dentro dele
`""` é uma quote literal. Uma linha em branco é um registro com um campo
vazio; um newline final não adiciona registro. O primeiro registro não é
especial — trate-o como header lendo-o primeiro. Alvos iguais ao `TextStream`
(JVM, Native x86-64/riscv64, Script; JS `IOJS001`).

## Referência

- [learn/34-file-system.md](../../learn/34-file-system.md)
- Testes: `kof-compiler/src/test/java/dev/kof/compiler/IoE2ETest.java`
- Streaming: `libs/file/FileStream.kf`, `libs/file/TextStream.kf`, `libs/file/Csv.kf`, `FileLibraryE2ETest.java`, `CsvReaderE2ETest.java`