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
| `copyTo(destino)` | Bool — **somente JVM** (18/09). Copia bytes + atributos básicos. Sem sobrescrita por padrão (devolve `false`, sem alterar nenhum dos dois arquivos, se `destino` já existir); não cria o diretório pai de `destino` implicitamente — quem chama precisa garantir que ele exista |
| `moveTo(destino)` | Bool — **somente JVM** (18/09). Primitiva de filesystem para mover/renomear, sem sobrescrita por padrão (mesmo contrato de `copyTo`). Não é uma transação segura: quem precisa de mover com verificação de hash continua fazendo copiar → validar → apagar, como já fazia antes deste método existir |
| `modifiedTime()` | Long — **somente JVM** (18/09). Data de modificação em milissegundos desde a época; lança exceção se o arquivo não existir (mesmo contrato de `size()`, sem sentinela) |
| `isSymlink()` | Bool — **somente JVM** (18/09). `true` quando o próprio caminho é um link simbólico (o link nunca é seguido implicitamente por essa checagem) |

Formas estáticas: `File.exists(p)`, `File.readText(p)`,
`File.writeText(p, s)`, `File.appendText(p, s)`, `File.delete(p)`,
`File.size(p)`, `File.name(p)`.

`copyTo`/`moveTo`/`modifiedTime`/`isSymlink` ainda não têm forma estática e
não têm backend Native (`RuntimeIo2` ainda não tem esses casos) — usá-los
mirando Native é um gap conhecido, não um no-op silencioso; isso não foi
exercitado nesta mudança (somente JVM) e o modo de falha exato em Native
ainda não foi caracterizado.

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
`D-KOF-FILE-GO` fatia 1, provada na JVM; sem sintaxe nova, sem mudança no
compilador.

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

Alvos: JVM e Native (`readRange` tem prova cross em x86-64/riscv64/aarch64).
JS e Script não têm `readRange` — lacuna honesta (`D-KOF-FILE-GO`), nunca
fallback silencioso de arquivo inteiro.

## Referência

- [learn/34-file-system.md](../../learn/34-file-system.md)
- Testes: `kof-compiler/src/test/java/dev/kof/compiler/IoE2ETest.java`
- Streaming: `libs/file/FileStream.kf`, `FileLibraryE2ETest.java`