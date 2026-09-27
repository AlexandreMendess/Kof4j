package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * §373 (issue #443, "§297 do corpo da issue") — coleção builtin NUA (sem
 * type-args) em posição de tipo DECLARADO (campo/retorno/parâmetro de método
 * de classe) resolvia para {@code ClassType("", "List")} no SÍMBOLO do membro
 * (caminho {@code SymbolTableBuilder.defineClassMembers →
 * MemberResolver.resolveType → Type.of → qualifyDeep}), enquanto o IRField
 * (caminho {@code lowerField → toType}, que tem o pin #139/#150/#214/§243)
 * punha {@code kof/List} no campo real. Os dois descritores divergiam no
 * class file: {@code Field Box.items:LList;} + receiver
 * {@code List.size:Ljava/lang/Object;} → {@code ClassNotFoundException:
 * List} no LOAD da classe (a resolução de Fieldref carrega o tipo do
 * descritor ANTES de comparar nome) — o programa nunca rodava.
 *
 * <p>Fix: {@code qualifyDeep} passo 2b (o mesmo ponto do §179 para kof.ui/
 * kof.media) passa a conhecer as coleções nuas via
 * {@link BuiltinTypes#declaredCollectionType} — espelho do conjunto de pins
 * do {@code toType} (List/ArrayList/LinkedList, Set/HashSet, Map/HashMap,
 * Channel). O guard §243/§179 do shadowing é do caller: {@code class List}
 * declarado pelo usuário VENCE (controles abaixo travam as duas direções).
 *
 * <p>Paridade medida com o jar do fix (regra 5 do freeze): o verbatim roda
 * {@code 2} nos 4 alvos — JVM/Script/JS/Native x86-64. Aqui o verbatim é
 * travado em JVM + Script; JS/Native ficam na nota da seção do ledger.
 */
class BareCollectionFieldE2ETest {

    private final CompilerDriver driver = new CompilerDriver();

    private Path write(Path tmp, String src) throws IOException {
        Path f = tmp.resolve("Main-" + System.nanoTime() + ".kf");
        Files.writeString(f, src);
        return f;
    }

    private String runJvm(Path tempDir, String source) throws IOException {
        Path file = write(tempDir, source);
        Path outDir = tempDir.resolve("out-" + System.nanoTime());
        CompilationResult result = driver.compile(file, outDir, Target.JVM);
        assertTrue(result.success(), "JVM compile failed: " + result.diagnostics().getDiagnostics());
        try {
            Path runnerDir = tempDir.resolve("run-" + System.nanoTime());
            Files.createDirectories(runnerDir);
            Path runnerSrc = runnerDir.resolve("Run.java");
            Files.writeString(runnerSrc, """
                public class Run {
                    public static void main(String[] args) throws Exception {
                        Class.forName(args[0]).getMethod("main", String[].class)
                            .invoke(null, (Object) new String[0]);
                    }
                }
                """);
            Process pCompile = new ProcessBuilder(TestJdk.javacBin(), "-d", runnerDir.toString(), runnerSrc.toString()).start();
            assertEquals(0, pCompile.waitFor());
            Process p = new ProcessBuilder(TestJdk.javaBin(),
                    "-cp", outDir.toString() + java.io.File.pathSeparator + runnerDir.toString(), "Run", "Default.Main")
                    .redirectErrorStream(true).start();
            String output = new String(p.getInputStream().readAllBytes(),
                    java.nio.charset.StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
            int ec = p.waitFor();
            assertEquals(0, ec, "JVM exit code, output: " + output);
            return output;
        } catch (InterruptedException e) {
            throw new IOException("interrupted", e);
        }
    }

    // ---- o verbatim da issue (#443 / home mel med g i443 Main.kf) ----

    @Test
    void bareListFieldReadAndSizeJvm(@TempDir Path tmp) throws IOException {
        // read + chamada de método no campo nu — as DUAS faces fantasma do
        // javap (descritor do Fieldref e owner do receiver .size).
        assertEquals("2", runJvm(tmp, """
                class Box {
                    List items
                    public constructor() { items = listOf(1,2) }
                }
                main() { var b = Box(); println(b.items.size) }
                """));
    }

    @Test
    void bareCollectionsFieldWriteAndReadJvm(@TempDir Path tmp) throws IOException {
        // write no campo nu (putfield) + read (getfield) + chamada de método
        // no receiver (a face 2 do javap), List/Set/Map. ARGS de referência
        // apenas: o arg primitivo (`b.xs.add(2)`) cai na face catalogada do
        // §374 (box por elemType declarado, preexistente no caminho local —
        // fora do escopo do §373).
        assertEquals("3\n1\n7", runJvm(tmp, """
                class Bag {
                    List xs
                    Set ks
                    Map vals
                    public constructor() {
                        xs = listOf("a")
                        ks = setOf("a")
                        vals = mapOf("a", 7)
                    }
                }
                main() {
                    var b = Bag()
                    b.xs = listOf("p", "q", "r")
                    println(b.xs.size)
                    println(b.ks.size)
                    println(b.vals.get("a"))
                }
                """));
    }

    @Test
    void bareChannelFieldDeclaresRealChannelJvm(@TempDir Path tmp) throws IOException {
        // Channel nu tem a MESMA raiz (toType pin; qualifyDeep não) — medido
        // phantom antes do fix; fecha junto com a família.
        assertEquals("ok", runJvm(tmp, """
                class Box {
                    Channel ch
                    public constructor() { ch = channel() }
                }
                main() { var b = Box(); println("ok") }
                """));
    }

    @Test
    void bareCollectionAsMethodReturnAndParamJvm(@TempDir Path tmp) throws IOException {
        // Mesmo resolveType nos símbolos de método: retorno nu e parâmetro nu.
        assertEquals("2\n2", runJvm(tmp, """
                class Box {
                    List items
                    public constructor() { items = listOf(1,2) }
                    List get() { return items }
                    Int count(List xs) { return xs.size }
                }
                main() {
                    var b = Box()
                    println(b.get().size)
                    println(b.count(listOf(3,4)))
                }
                """));
    }

    // ---- controles: twin tipado e sombra do usuário (guard §243) ----

    @Test
    void typedListFieldTwinStillWorksJvm(@TempDir Path tmp) throws IOException {
        // `List<Int>` sempre funcionou (Type.of pinha a forma parametrizada) —
        // controle de não-regressão do twin.
        assertEquals("2", runJvm(tmp, """
                class Box {
                    List<Int> items
                    public constructor() { items = listOf(1,2) }
                }
                main() { var b = Box(); println(b.items.size) }
                """));
    }

    @Test
    void userShadowBareListFieldStillResolvesToUserClassJvm(@TempDir Path tmp) throws IOException {
        // `class List` do usuário em campo NU: o guard do qualifyDeep 2b
        // (!unitDeclaresType && sa.getClass == null) preserva o shadowing —
        // o descritor do campo é a classe do usuário, não kof/List.
        assertEquals("7", runJvm(tmp, """
                class List {
                    Int size
                }
                class Box {
                    List items
                    public constructor() { items = new List() }
                }
                main() { var b = Box(); b.items.size = 7; println(b.items.size) }
                """));
    }

    // ---- prova de DESCRIPTOR (precedente §357: bytes do .class) ----

    @Test
    void bareListFieldDescriptorIsRealCollectionNotPhantom(@TempDir Path tmp) throws IOException {
        Path file = write(tmp, """
                class Box {
                    List items
                    public constructor() { items = listOf(1,2) }
                }
                main() { var b = Box(); println(b.items.size) }
                """);
        Path outDir = tmp.resolve("out-desc");
        CompilationResult r = driver.compile(file, outDir, Target.JVM);
        assertTrue(r.success(), "compile: " + r.diagnostics().getDiagnostics());
        String cp = new String(Files.readAllBytes(outDir.resolve("Default/Main.class")),
                java.nio.charset.StandardCharsets.ISO_8859_1);
        assertFalse(cp.contains("LList;"),
                "descritor fantasma LList; não pode aparecer no constant pool");
        assertTrue(cp.contains("Ljava/util/ArrayList;"),
                "o Fieldref do campo deve apontar para a coleção real");
        assertFalse(cp.contains("List.size"),
                "receiver fantasma owner \"List\" (NameAndType List.size) não pode voltar");
    }

    // ---- Script (segundo alvo do verbatim; JS/Native na nota da seção) ----

    @Test
    void bareListFieldReadAndSizeRunsOnScript(@TempDir Path tmp) throws IOException {
        Path file = write(tmp, """
                class Box {
                    List items
                    public constructor() { items = listOf(1,2) }
                }
                main() { var b = Box(); println(b.items.size) }
                """);
        KofInterpreter.Result ir = driver.interpret(java.util.List.of(file), tmp, new String[0]);
        assertEquals(0, ir.exitCode(), "script stderr: " + ir.stderr());
        assertEquals("2\n", ir.stdout(), "verbatim #443 no Script (paridade regra 5)");
    }
}
