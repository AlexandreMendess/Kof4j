package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.Assumptions;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class KofConcurrency2Test {
    private final CompilerDriver driver = new CompilerDriver();

    private static boolean has(String... cmds) {
        for (String c : cmds) {
            try {
                Process p = new ProcessBuilder("sh", "-c", "command -v " + c).redirectErrorStream(true).start();
                String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
                if (p.waitFor() != 0 || out.isEmpty()) return false;
            } catch (Exception e) {
                return false;
            }
        }
        return true;
    }

    @Test
    void cancelCooperativeJvm(@TempDir Path tmp) throws Exception {
        runJvm(tmp, """
                Int trabalho() {
                    var i = 0
                    while (i < 10000 && !cancelled()) {
                        time.sleep(1)
                        i++
                    }
                    if (cancelled()) {
                        println("cancelado")
                    } else {
                        println("completo")
                    }
                    return i
                }

                main() {
                    val r = spawn trabalho()
                    time.sleep(30)
                    assert(cancel(r))
                    await r
                    println("fim")
                }
                """, "cancelado\nfim");
    }


    @Test
    void cancelledOutsideIsFalse(@TempDir Path tmp) throws Exception {
        runJvm(tmp, """
                main() {
                    assert(!cancelled())
                    println("ok")
                }
                """, "ok");
    }

    @Test
    void selectAnyJvm(@TempDir Path tmp) throws Exception {
        runJvm(tmp, """
                String rapida() { return "primeira" }

                String lenta() {
                    time.sleep(300)
                    return "segunda"
                }

                main() {
                    val a = spawn lenta()
                    val b = spawn rapida()
                    val v = selectAny(a, b)
                    println(v)
                }
                """, "primeira");
    }

    @Test
    void selectAnyPrimitiveJvm(@TempDir Path tmp) throws Exception {
        // §128-JVM: selectAny de Handle<Int> atribuído a var e usado como Int
        // dava VerifyError "Type 'java/lang/Object' is not assignable to
        // integer" no istore — o lowerer roteava await/awaitTimeout p/ unbox
        // mas NÃO selectAny (mesmo retorno Object do runtime). O caso String
        // (selectAnyJvm) não pegava: referência não precisa de unbox.
        runJvm(tmp, """
                Int um() { return 7 }
                Int outro() { time.sleep(200); return 9 }
                main() {
                    val a = spawn um()
                    val b = spawn outro()
                    val v = selectAny(a, b)
                    println(v + 1)
                }
                """, "8");
    }

    @Test
    void selectAnyNative(@TempDir Path tmp) throws Exception {
        // CONC001 residual fechado: selectAny nativo (polling 1ms sobre o handle).
        // Os handles são criados JUNTOS (spawn-all-up-front) e o selectAny vem
        // depois — evitam o bug pré-existente de spawn→await→spawn (thread já
        // finalizada + novo pthread_create corrompe a pilha da main).
        Path f = tmp.resolve("M.kf");
        Files.writeString(f, """
                Int t1() { return 1 }
                Int t2() { time.sleep(300); return 2 }
                main() {
                    val a = spawn t1()
                    val b = spawn t2()
                    time.sleep(50)
                    println(selectAny(a, b))
                }
                """);
        CompilationResult r = driver.compile(f, tmp.resolve("out"), Target.NATIVE);
        assertTrue(r.success(), "Native selectAny deve compilar: " + r.diagnostics().getDiagnostics());
        Path bin = tmp.resolve("out").resolve("Default/Main");
        Process p = new ProcessBuilder(bin.toString()).redirectErrorStream(true).start();
        String output = new String(p.getInputStream().readAllBytes()).trim();
        assertEquals(0, p.waitFor(), "exit code, output: " + output);
        assertEquals("1", output, "a task rápida (1) deve vencer a lenta (2)");
    }

    @Test
    void cancelCooperativeNative(@TempDir Path tmp) throws Exception {
        // CONC001 residual fechado: cancel cooperativo nativo (flag por TID).
        // Worker checa !cancelled() no loop; main cancela após 30ms. Spawn único
        // (evita o bug pré-existente de spawn→await→spawn).
        Path f = tmp.resolve("M.kf");
        Files.writeString(f, """
                Int trabalho() {
                    var i = 0
                    while (i < 100000 && !cancelled()) {
                        time.sleep(1)
                        i++
                    }
                    if (cancelled()) { println("cancelado") } else { println("completo") }
                    return i
                }
                main() {
                    val r = spawn trabalho()
                    time.sleep(30)
                    assert(cancel(r))
                    await r
                    println("fim")
                }
                """);
        CompilationResult r = driver.compile(f, tmp.resolve("out"), Target.NATIVE);
        assertTrue(r.success(), "Native cancel/cooperativo deve compilar: " + r.diagnostics().getDiagnostics());
        Path bin = tmp.resolve("out").resolve("Default/Main");
        Process p = new ProcessBuilder(bin.toString()).redirectErrorStream(true).start();
        String output = new String(p.getInputStream().readAllBytes()).trim();
        assertEquals(0, p.waitFor(), "exit code, output: " + output);
        assertEquals("cancelado\nfim", output, "o worker deve ver o cancel e encerrar cedo");
    }

    // §117 (decisão 8a, 13/09): tabela de cancel por TID REAL (probe linear)
    // — colisão forçada: N workers VIVOS em sequência; o cancel do worker k
    // NÃO pode afetar o worker k+1 (bug antigo: slot por hash truncado
    // reutilizado + `movb $0` cego no trampoline apagava flag alheia).
    @Test
    void cancelDoesNotLeakAcrossWorkersNative(@TempDir Path tmp) throws Exception {
        // 30 iterações: worker longo cancelado; após await, novo worker
        // VERIFICA que a própria flag nasce limpa (cancelled() == false).
        Path f = tmp.resolve("MC.kf");
        Files.writeString(f, """
                Int longo() {
                    var i = 0
                    while (i < 100000 && !cancelled()) {
                        time.sleep(1)
                        i++
                    }
                    return i
                }
                Int curto() {
                    if (cancelled()) { return 999 }
                    time.sleep(5)
                    if (cancelled()) { return 999 }
                    return 1
                }
                main() {
                    var ok = true
                    var k = 0
                    while (k < 20) {
                        val a = spawn longo()
                        time.sleep(15)
                        assert(cancel(a))
                        await a
                        val b = spawn curto()
                        val v = await b
                        if (v == 999) { ok = false }
                        k++
                    }
                    if (ok) { println("sem-vazamento") } else { println("VAZOU") }
                }
                """);
        CompilationResult r = driver.compile(f, tmp.resolve("out"), Target.NATIVE);
        assertTrue(r.success(), "Native §117 deve compilar: " + r.diagnostics().getDiagnostics());
        Path bin = tmp.resolve("out").resolve("Default/Main");
        Process p = new ProcessBuilder(bin.toString()).redirectErrorStream(true).start();
        String output = new String(p.getInputStream().readAllBytes()).trim();
        assertEquals(0, p.waitFor(), "exit code, output: " + output);
        assertEquals("sem-vazamento", output,
                "cancel do worker k não pode marcar o worker k+1 (§117)");
    }

    private static final String CANCEL_BEFORE_START_KF = """
            Int longo() {
                var i = 0
                while (i < 200 && !cancelled()) { time.sleep(1); i++ }
                return i
            }
            main() {
                var ok = 0
                var k = 0
                while (k < %ROUNDS%) {
                    val a = spawn longo()
                    cancel(a)
                    val v = await a
                    if (v < 200) { ok = ok + 1 }
                    k = k + 1
                }
                println(ok + "/%ROUNDS%")
            }
            """;

    @Test
    void cancelImmediatelyAfterSpawnNative(@TempDir Path tmp) throws Exception {
        // §286 (causa-raiz do flake sob carga): cancel ANTES da trampoline
        // registrar a entry — o `find` falhava e o pedido se PERDIA (assert(
        // cancel) mentia / worker corrido inteiro). O pending no handle + o
        // re-check Dekker fecham a janela SEM depender de agendamento:
        // deterministicamente 30/30 workers cancelam cedo. Runtime antigo
        // marca ok=0 (todos fogem da janela) — RED mediato, não hang.
        runNative(tmp, CANCEL_BEFORE_START_KF.replace("%ROUNDS%", "30"), "30/30");
    }

    @Test
    void cancelImmediatelyAfterSpawnCrossArch(@TempDir Path tmp) throws Exception {
        // Rule 5: o MESMO fechamento no riscv64 (fonte) e aarch64 (tradutor).
        for (Target t : new Target[]{Target.NATIVE_RISCV64, Target.NATIVE_AARCH64}) {
            String arch = t.nativeArch();
            Assumptions.assumeTrue(NativeRiscv64E2ETest.hasToolchain(arch), "cross toolchain " + arch + " ausente — pulando");
            Path f = tmp.resolve("CB" + arch + ".kf");
            Files.writeString(f, CANCEL_BEFORE_START_KF.replace("%ROUNDS%", "10"));
            Path outDir = tmp.resolve("cancelpend-" + arch);
            CompilationResult r = driver.compile(f, outDir, t);
            assertTrue(r.success(), t + " deve compilar: " + r.diagnostics().getDiagnostics());
            String out = NativeRiscv64E2ETest.runQemu(arch, outDir.resolve("Default/Main"));
            assertEquals("10/10", out, t + " cancel-imediato deve fechar a janela");
        }
    }

    @Test
    void awaitTimeoutJvm(@TempDir Path tmp) throws Exception {
        // G8/CONC residual: awaitTimeout(r, ms) -> valor no prazo; lança no estouro
        // (capturável via try/catch). JVM: Future.get(ms).
        runJvm(tmp, """
                Int lenta() { time.sleep(400); return 9 }
                Int rapida() { return 42 }
                main() {
                    val r = spawn lenta()
                    try {
                        awaitTimeout(r, 50)
                        println("in")
                    } catch (String e) {
                        println("err")
                    }
                    val q = spawn rapida()
                    var w = awaitTimeout(q, 100)
                    println("q=" + w)
                    var f = await r
                    println("f=" + f)
                }
                """, "err\nq=42\nf=9");
    }

    @Test
    void awaitTimeoutNative(@TempDir Path tmp) throws Exception {
        // awaitTimeout no Native: polling 1ms com deadline; estouro -> kof_throw_string
        // (try/catch do usuário).
        // Ordem segura p/ o bug pré-existente spawn->(task)->spawn (SIGSEGV no
        // próximo pthread_create): a 1ª task é uma sleep (não alocadora) e é
        // joinada (`await`) antes do 2º spawn.
        Path f = tmp.resolve("M.kf");
        Files.writeString(f, """
                Int lenta() { time.sleep(400); return 9 }
                Int rapida() { return 42 }
                main() {
                    val r = spawn lenta()
                    try {
                        awaitTimeout(r, 50)
                        println("in")
                    } catch (String e) {
                        println("err")
                    }
                    var f = await r
                    println("f=" + f)
                    val q = spawn rapida()
                    var w = awaitTimeout(q, 100)
                    println("q=" + w)
                }
                """);
        CompilationResult r = driver.compile(f, tmp.resolve("out"), Target.NATIVE);
        assertTrue(r.success(), "Native awaitTimeout deve compilar: " + r.diagnostics().getDiagnostics());
        Path bin = tmp.resolve("out").resolve("Default/Main");
        Process p = new ProcessBuilder(bin.toString()).redirectErrorStream(true).start();
        String output = new String(p.getInputStream().readAllBytes()).trim();
        assertEquals(0, p.waitFor(), "exit code, output: " + output);
        assertEquals("err\nf=9\nq=42", output, "lenta estoura (catch); rápida no prazo");
    }

    @Test
    void awaitTimeoutJs(@TempDir Path tmp) throws Exception {
        // JS CONC003: task instantânea — awaitTimeout conclui no prazo.
        runJs(tmp, """
                Int t() { return 9 }
                main() {
                    val r = spawn t()
                    var v = awaitTimeout(r, 50)
                    println(v)
                }
                """, "9");
    }

    @Test
    void awaitTimeoutSlowTaskJvm(@TempDir Path tmp) throws Exception {
        runJvm(tmp, """
                Int lenta() { time.sleep(200); return 9 }
                main() {
                    val r = spawn lenta()
                    try {
                        awaitTimeout(r, 20)
                        println("in")
                    } catch (String e) {
                        println("err")
                    }
                }
                """, "err");
    }

    @Test
    void awaitTimeoutSlowTaskJs(@TempDir Path tmp) throws Exception {
        // CONC003: task com vários yields assíncronos — estouro real (time.sleep
        // bloqueia o event loop no JS e não serve aqui).
        runJs(tmp, """
                Int step() { return 1 }
                Int lenta() {
                    var i = 0
                    while (i < 80) {
                        await spawn step()
                        i++
                    }
                    return 9
                }
                main() {
                    val r = spawn lenta()
                    try {
                        awaitTimeout(r, 5)
                        println("in")
                    } catch (String e) {
                        println("err")
                    }
                }
                """, "err");
    }

    @Test
    void spawnInterleavingJvm(@TempDir Path tmp) throws Exception {
        String out = runJvm(tmp, """
                Int tick() { return 1 }

                Int slow() {
                    await spawn tick()
                    await spawn tick()
                    await spawn tick()
                    println("A:done")
                    return 1
                }

                main() {
                    spawn slow()
                    spawn { println("B") }
                }
                """);
        int b = out.indexOf("B");
        int a = out.indexOf("A:done");
        assertTrue(b >= 0 && a >= 0, "saída: " + out);
        assertTrue(b < a, "B deve aparecer antes de A:done (não-bloqueante): " + out);
    }

    @Test
    void spawnInterleavingJs(@TempDir Path tmp) throws Exception {
        // Impossível sob fake sequencial: slow() terminaria (incl. A:done)
        // antes de B ser despachado.
        String out = runJs(tmp, """
                Int tick() { return 1 }

                Int slow() {
                    await spawn tick()
                    await spawn tick()
                    await spawn tick()
                    println("A:done")
                    return 1
                }

                main() {
                    spawn slow()
                    spawn { println("B") }
                }
                """);
        int b = out.indexOf("B");
        int a = out.indexOf("A:done");
        assertTrue(b >= 0 && a >= 0, "saída: " + out);
        assertTrue(b < a, "B deve aparecer antes de A:done (microtasks reais): " + out);
    }

    @Test
    void channelBlocksBeforeSendJvm(@TempDir Path tmp) throws Exception {
        // JVM: receive bloqueia a virtual thread. A única ordem garantida é
        // recv:42 DEPOIS de pre-send (o receive só retorna após o send).
        // recv-wait vs pre-send e recv:42 vs post-send são corrida de
        // agendamento de virtual threads — não pinamos.
        String out = runJvm(tmp, """
                main() {
                    val c = channel<Int>()
                    spawn {
                        println("recv-wait")
                        val v = c.receive()
                        println("recv:" + v)
                    }
                    time.sleep(20)
                    println("pre-send")
                    c.send(42)
                    println("post-send")
                }
                """);
        for (String line : new String[]{"recv-wait", "pre-send", "post-send", "recv:42"}) {
            assertTrue(out.contains(line), "faltou '" + line + "' em: " + out);
        }
        assertTrue(out.indexOf("pre-send") < out.indexOf("recv:42"),
                "receive deve bloquear até o send (recv:42 após pre-send): " + out);
    }

    @Test
    void channelBlocksBeforeSendJs(@TempDir Path tmp) throws Exception {
        runJs(tmp, """
                Int tick() { return 0 }

                main() {
                    val c = channel<Int>()
                    spawn {
                        println("recv-wait")
                        val v = await c.receive()
                        println("recv:" + v)
                    }
                    await spawn tick()
                    println("pre-send")
                    c.send(42)
                    println("post-send")
                }
                """, "recv-wait\npre-send\npost-send\nrecv:42");
    }

    @Test
    void selectAnyJs(@TempDir Path tmp) throws Exception {
        runJs(tmp, """
                Int tick() { return 0 }

                Int lenta() {
                    await spawn tick()
                    await spawn tick()
                    await spawn tick()
                    return 1
                }

                Int rapida() { return 2 }

                main() {
                    val a = spawn lenta()
                    val b = spawn rapida()
                    println(selectAny(a, b))
                }
                """, "2");
    }

    @Test
    void channelJvm(@TempDir Path tmp) throws Exception {
        // Canais tipados (G8): channel<Int>() FIFO; send/receive. JVM:
        // LinkedBlockingQueue (put/take bloqueantes).
        runJvm(tmp, """
                main() {
                    val c = channel<Int>()
                    c.send(5)
                    c.send(6)
                    c.send(7)
                    var s = 0
                    var i = 0
                    while (i < 3) {
                        s = s + c.receive()
                        i++
                    }
                    println("s=" + s)
                    val cs = channel<String>()
                    cs.send("a")
                    cs.send("b")
                    println(cs.receive() + cs.receive())
                }
                """, "s=18\nab");
    }

    @Test
    void channelNative(@TempDir Path tmp) throws Exception {
        // Canais no Native: FIFO de lista ligada + mutex futex (mesma tecnica
        // do kof_alloc). Sem spawn (bug pre-existente spawn->await->spawn).
        Path f = tmp.resolve("M.kf");
        Files.writeString(f, """
                main() {
                    val c = channel<Int>()
                    c.send(5)
                    c.send(6)
                    c.send(7)
                    var s = 0
                    var i = 0
                    while (i < 3) {
                        s = s + c.receive()
                        i++
                    }
                    println("s=" + s)
                    val cs = channel<String>()
                    cs.send("a")
                    cs.send("b")
                    println(cs.receive() + cs.receive())
                }
                """);
        CompilationResult r = driver.compile(f, tmp.resolve("out"), Target.NATIVE);
        assertTrue(r.success(), "Native channel deve compilar: " + r.diagnostics().getDiagnostics());
        Path bin = tmp.resolve("out").resolve("Default/Main");
        Process p = new ProcessBuilder(bin.toString()).redirectErrorStream(true).start();
        String output = new String(p.getInputStream().readAllBytes()).trim();
        assertEquals(0, p.waitFor(), "exit code, output: " + output);
        assertEquals("s=18\nab", output, "FIFO Int + String no Native");
    }

    // known-bugs #50 — channel send/recv DENTRO de spawn no Native (SIGSEGV 139).
    // Correção candidata 09/09: futex WAIT do canal x86_64 com args corretos
    // (rdi=&lock, rsi=op). Este teste valida o fix quando o toolchain/qemu
    // estiver disponível.
    @Test
    void channelWithSpawnNative(@TempDir Path tmp) throws Exception {
        Path f = tmp.resolve("M.kf");
        Files.writeString(f, """
                main() {
                    val c = channel<Int>()
                    spawn { c.send(42) }
                    val v = c.receive()
                    println("v=" + v)
                }
                """);
        CompilationResult r = driver.compile(f, tmp.resolve("out"), Target.NATIVE);
        assertTrue(r.success(), "Native channel+spawn deve compilar: " + r.diagnostics().getDiagnostics());
        Path bin = tmp.resolve("out").resolve("Default/Main");
        Process p = new ProcessBuilder(bin.toString()).redirectErrorStream(true).start();
        String output = new String(p.getInputStream().readAllBytes()).trim();
        assertEquals(0, p.waitFor(), "exit code, output: " + output);
        assertEquals("v=42", output, "channel send dentro de spawn no Native");
    }

    /** §423 (TIER 13.4, 23/09): canais + spawn no cross — o MESMO cenário do
     *  #50 que no x86_64 travava em SIGSEGV (futex WAIT do receive) roda nos 2
     *  alvos: o worker envia 2 itens, a main bloqueia no futex e recebe (FIFO);
     *  paridade JVM byte-a-byte. */
    @Test
    void channelWithSpawnCrossArch(@TempDir Path tmp) throws Exception {
        for (Target t : new Target[]{Target.NATIVE_RISCV64, Target.NATIVE_AARCH64}) {
            String arch = t.nativeArch();
            Assumptions.assumeTrue(NativeRiscv64E2ETest.hasToolchain(arch),
                    "cross toolchain " + arch + " ausente — pulando");
            Path f = tmp.resolve("CHAN" + arch + ".kf");
            Files.writeString(f, """
                    main() {
                        val c = channel<Int>()
                        spawn {
                            c.send(41)
                            c.send(1)
                        }
                        val v = c.receive() + c.receive()
                        println("v=" + v)
                    }
                    """);
            Path outDir = tmp.resolve("chan-" + arch);
            CompilationResult r = driver.compile(f, outDir, t);
            assertTrue(r.success(), t + " canal+spawn compila: " + r.diagnostics().getDiagnostics());
            String out = NativeRiscv64E2ETest.runQemu(arch, outDir.resolve("Default/Main"));
            assertEquals("v=42", out, t + " canal+spawn no cross (§423)");
        }
    }

    /** §485 (23/09): DRENA-e-ENVIA determinístico. Receber o último item
     *  esvazia a fila (head=0) mas a cauda ficava apontando p/ o nó já liberado;
     *  o SEND seguinte via tail!=0, anexava SEM pôr head → head=0 com count>0 →
     *  o receive dereferenciava NULL (SIGSEGV 139). Reproduz em single-thread,
     *  sem depender da escala de threads: falhava nos 3 alvos antes do fix. */
    @Test
    void channelDrainThenSendNative(@TempDir Path tmp) throws Exception {
        String prog = """
                main() {
                    val c = channel<Int>()
                    c.send(1)
                    val a = c.receive()
                    c.send(2)
                    val b = c.receive()
                    println("a=" + a + " b=" + b)
                }
                """;
        Target[] targets = {Target.NATIVE, Target.NATIVE_RISCV64, Target.NATIVE_AARCH64};
        for (Target t : targets) {
            String arch = t == Target.NATIVE ? "x86_64" : t.nativeArch();
            if (t != Target.NATIVE) {
                Assumptions.assumeTrue(NativeRiscv64E2ETest.hasToolchain(arch),
                        "cross toolchain " + arch + " ausente — pulando");
            }
            Path f = tmp.resolve("DRAIN" + arch + ".kf");
            Files.writeString(f, prog);
            Path outDir = tmp.resolve("drain-" + arch);
            CompilationResult r = driver.compile(f, outDir, t);
            assertTrue(r.success(), arch + " drena+envia compila: " + r.diagnostics().getDiagnostics());
            Path bin = outDir.resolve("Default/Main");
            if (t == Target.NATIVE) {
                Process p = new ProcessBuilder(bin.toString()).redirectErrorStream(true).start();
                String out = new String(p.getInputStream().readAllBytes()).trim();
                assertEquals(0, p.waitFor(), arch + " exit code, output: " + out);
                assertEquals("a=1 b=2", out, arch + " FIFO após drenar a fila (§485)");
            } else {
                String out = NativeRiscv64E2ETest.runQemu(arch, bin);
                assertEquals("a=1 b=2", out, arch + " FIFO após drenar a fila (§485)");
            }
        }
    }

    @Test
    void channelJs(@TempDir Path tmp) throws Exception {
        // JS sequencial: canal = {items:[]} (send push, receive shift).
        runJs(tmp, """
                main() {
                    val c = channel<Int>()
                    c.send(5)
                    c.send(6)
                    c.send(7)
                    var s = 0
                    var i = 0
                    while (i < 3) {
                        s = s + c.receive()
                        i++
                    }
                    println("s=" + s)
                }
                """, "s=18");
    }

    @Test
    void channelAsFunctionParameterJvm(@TempDir Path tmp) throws Exception {
        // Channel<T> como PARÂMETRO: antes o tipo do parâmetro era
        // ClassType(package="") e o isChannel exigia "kof.concurrent" → o
        // dispatch caía no genérico e gerava bytecode inválido (JVM),
        // undefined reference (Native) e c.receive() inexistente (JS).
        runJvm(tmp, """
                Int soma(Channel<Int> c) {
                    var s = 0
                    s = s + c.receive()
                    s = s + c.receive()
                    return s
                }
                main() {
                    var c = channel<Int>()
                    c.send(3)
                    c.send(4)
                    println(soma(c))
                }
                """, "7");
    }

    @Test
    void channelAsFunctionParameterNative(@TempDir Path tmp) throws Exception {
        Path f = tmp.resolve("M.kf");
        Files.writeString(f, """
                Int soma(Channel<Int> c) {
                    var s = 0
                    s = s + c.receive()
                    s = s + c.receive()
                    return s
                }
                main() {
                    var c = channel<Int>()
                    c.send(3)
                    c.send(4)
                    println(soma(c))
                }
                """);
        CompilationResult r = driver.compile(f, tmp.resolve("out"), Target.NATIVE);
        assertTrue(r.success(), "Native channel-params deve compilar: " + r.diagnostics().getDiagnostics());
        Path bin = tmp.resolve("out").resolve("Default/Main");
        Process p = new ProcessBuilder(bin.toString()).redirectErrorStream(true).start();
        String output = new String(p.getInputStream().readAllBytes()).trim();
        assertEquals(0, p.waitFor(), "exit code, output: " + output);
        assertEquals("7", output, "channel como parâmetro no Native");
    }

    @Test
    void channelAsFunctionParameterJs(@TempDir Path tmp) throws Exception {
        runJs(tmp, """
                Int soma(Channel<Int> c) {
                    var s = 0
                    s = s + c.receive()
                    s = s + c.receive()
                    return s
                }
                main() {
                    var c = channel<Int>()
                    c.send(3)
                    c.send(4)
                    println(soma(c))
                }
                """, "7");
    }

    @Test
    void pollDoneNative(@TempDir Path tmp) throws Exception {
        // CONC001 residual fechado: done/poll não-bloqueantes sobre o handle
        // nativo (flag done no bloco de 32B: 0=tag(2), 4=done, 16=result)
        Path f = tmp.resolve("M.kf");
        Files.writeString(f, """
                Int lenta() { time.sleep(400); return 7 }
                Int rapida() { return 3 }
                main() {
                    val r = spawn lenta()
                    val f = spawn rapida()
                    time.sleep(50)
                    println("poll_f=" + poll(f))
                    println("done_r=" + done(r))
                    println("poll_r=" + poll(r))
                    println("await_r=" + await r)
                    println("done_r2=" + done(r))
                }
                """);
        CompilationResult r = driver.compile(f, tmp.resolve("out"), Target.NATIVE);
        assertTrue(r.success(), "Native poll/done deve compilar: " + r.diagnostics().getDiagnostics());
        Path bin = tmp.resolve("out").resolve("Default/Main");
        Process p = new ProcessBuilder(bin.toString()).redirectErrorStream(true).start();
        String output = new String(p.getInputStream().readAllBytes()).trim();
        assertEquals(0, p.waitFor(), "exit code, output: " + output);
        // task rápida termina antes da main checar; lenta não
        assertTrue(output.contains("poll_f=3"), "poll da rápida devolve o valor: " + output);
        assertTrue(output.contains("done_r=false"), "lenta ainda não terminou: " + output);
        assertTrue(output.contains("poll_r=0"), "poll da lenta não-pronta devolve 0: " + output);
        assertTrue(output.contains("await_r=7"), "await da lenta devolve o valor: " + output);
        assertTrue(output.contains("done_r2=true"), "done vira true após o await: " + output);
    }

    @Test
    void cancelJsSequential(@TempDir Path tmp) throws Exception {
        runJs(tmp, """
                Int t() { return 9 }
                main() {
                    val r = spawn t()
                    assert(cancel(r))
                    assert(!cancelled())
                    println(await r)
                }
                """, "9");
    }

    @Test
    void schedulerEveryNative(@TempDir Path tmp) throws Exception {
        // SCHED001 fechado: scheduler.every/cancel no Native — thread por job
        // (trampoline: usleep ms→us + invoke da task enquanto active) e
        // cancel(id) marca active=0; o job sai sozinho. Ticks no 1º trecho,
        // silêncio após o cancel, END por último.
        Path f = tmp.resolve("M.kf");
        Files.writeString(f, """
                main() {
                    var id = scheduler.every(100) { println("T") }
                    time.sleep(250)
                    scheduler.cancel(id)
                    time.sleep(250)
                    println("END")
                }
                """);
        CompilationResult r = driver.compile(f, tmp.resolve("out"), Target.NATIVE);
        assertTrue(r.success(), "Native scheduler deve compilar: " + r.diagnostics().getDiagnostics());
        Path bin = tmp.resolve("out").resolve("Default/Main");
        Process p = new ProcessBuilder(bin.toString()).redirectErrorStream(true).start();
        String output = new String(p.getInputStream().readAllBytes()).trim();
        assertEquals(0, p.waitFor(), "exit code, output: " + output);
        assertTrue(output.endsWith("END"), "END deve ser a última linha: " + output);
        int ticks = 0;
        for (String l : output.split("\n")) if (l.equals("T")) ticks++;
        assertTrue(ticks >= 1 && ticks <= 6, "esperava 1..6 ticks, veio " + ticks + ": " + output);
    }

    @Test
    void schedulerEveryJvm(@TempDir Path tmp) throws Exception {
        // Paridade JVM: scheduler.every/cancel (thread daemon por job).
        // Contagem frouxa (timer não determinístico); END sempre por último.
        Path file = tmp.resolve("M.kf");
        Files.writeString(file, """
                main() {
                    var id = scheduler.every(100) { println("T") }
                    time.sleep(250)
                    scheduler.cancel(id)
                    time.sleep(250)
                    println("END")
                }
                """);
        Path outDir = tmp.resolve("out");
        CompilationResult result = driver.compile(file, outDir, Target.JVM);
        assertTrue(result.success(), "JVM scheduler deve compilar: " + result.diagnostics().getDiagnostics());
        try {
            Process p = new ProcessBuilder(System.getProperty("java.home") + "/bin/java",
                    "-cp", outDir.toString(), "Default.Main").redirectErrorStream(true).start();
            String output = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
            assertEquals(0, p.waitFor(), "JVM exit code, output: " + output);
            assertTrue(output.endsWith("END"), "END deve ser a última linha: " + output);
            int ticks = 0;
            for (String l : output.split("\n")) if (l.equals("T")) ticks++;
            assertTrue(ticks >= 1 && ticks <= 10, "esperava 1..10 ticks, veio " + ticks + ": " + output);
        } catch (InterruptedException e) {
            throw new java.io.IOException("interrupted", e);
        }
    }

    @Test
    void schedulerEveryCrossNative(@TempDir Path tmp) throws Exception {
        // SCHED001 FEITO no cross (05/09): thread por job via clone 220 +
        // nanosleep 101 + spinlock amoswap.w (mesmo mecanismo do spawn).
        // Ticks no 1º trecho, silêncio após o cancel, END por último —
        // contagem frouxa (timer não determinístico sob qemu).
        Path f = tmp.resolve("M.kf");
        Files.writeString(f, """
                main() {
                    var id = scheduler.every(100) { println("T") }
                    time.sleep(250)
                    scheduler.cancel(id)
                    time.sleep(250)
                    println("END")
                }
                """);
        String[] q = {null, "qemu-riscv64", "qemu-aarch64"};
        Target[] ts = {Target.NATIVE, Target.NATIVE_RISCV64, Target.NATIVE_AARCH64};
        Assumptions.assumeTrue(has("riscv64-linux-gnu-as", "riscv64-linux-gnu-ld", "qemu-riscv64")
                        && has("aarch64-linux-gnu-as", "aarch64-linux-gnu-ld", "qemu-aarch64"),
                "cross toolchain riscv64/aarch64 + qemu ausente — pulando (NATIVE002)");
        for (int i = 0; i < 3; i++) {
            CompilationResult r = driver.compile(f, tmp.resolve("out-" + i), ts[i]);
            assertTrue(r.success(), ts[i] + " deve compilar: " + r.diagnostics().getDiagnostics());
            Path bin = tmp.resolve("out-" + i).resolve("Default/Main");
            var pb = q[i] != null ? NativeRiscv64E2ETest.qemu(q[i].substring(5), bin)
                    : new ProcessBuilder(bin.toString());
            pb.redirectErrorStream(true);
            Process p = pb.start();
            String output = new String(p.getInputStream().readAllBytes()).trim();
            assertEquals(0, p.waitFor(), ts[i] + " exit, output: " + output);
            assertTrue(output.endsWith("END"), ts[i] + ": END deve ser a última linha: " + output);
            int ticks = 0;
            for (String l : output.split("\n")) if (l.equals("T")) ticks++;
            assertTrue(ticks >= 1 && ticks <= 6, ts[i] + ": esperava 1..6 ticks, veio " + ticks + ": " + output);
        }
    }

    // ===== §129 (DECISIONS §2, opção B): handler chain PER-THREAD no Native =====
    // Antes o chain era um `.data` global: um `throw` sem try DENTRO de um
    // worker longjmpava para o try da MAIN (frame de outra thread) — crash/hang
    // (known-bugs §129, provado por GDB). Agora o chain é TLS e o trampolim do
    // spawn instala handler próprio: o worker marca o handle como excepcional e
    // o await/selectAny relança no consumidor (paridade JVM).
    @Test
    void spawnWorkerThrowIsolatedFromSiblingsCrossArch(@TempDir Path tmp) throws Exception {
        // §129 (DECISIONS §2, opção B) porta do x86 para riscv/aarch: o throw
        // de um worker TEM de marcar o handle como excepcional (handle->exc) e
        // NÃO longjmpa na chain GLOBAL da main (crash/hang).
        for (Target t : new Target[]{Target.NATIVE_RISCV64, Target.NATIVE_AARCH64}) {
            String arch = t.nativeArch();
            Assumptions.assumeTrue(NativeRiscv64E2ETest.hasToolchain(arch), "cross toolchain " + arch + " ausente — pulando");
            Path f = tmp.resolve("ISO" + arch + ".kf");
            Files.writeString(f, """
                    Object bomba() { throw "boom" }
                    Void vigia() {
                        val h = spawn bomba()
                        try { await h; println("nao-deveria") } catch (String e) { println("cap=" + e) }
                    }
                    main() {
                        val a = spawn vigia()
                        val b = spawn vigia()
                        await a
                        await b
                        println("fim")
                    }
                    """);
            Path outDir = tmp.resolve("iso-" + arch);
            CompilationResult r = driver.compile(f, outDir, t);
            assertTrue(r.success(), t + " compila: " + r.diagnostics().getDiagnostics());
            String out = NativeRiscv64E2ETest.runQemu(arch, outDir.resolve("Default/Main"));
            assertEquals("cap=boom\ncap=boom\nfim", out, t + " throw isolado no worker");
        }
    }

    @Test
    void spawnWorkerThrowUnhandledPropagatesCrossArch(@TempDir Path tmp) throws Exception {
        // Um worker INTERMEDIÁRIO sem try: a falha do filho sobe até o try da
        // main via rethrow do await (paridade JVM).
        for (Target t : new Target[]{Target.NATIVE_RISCV64, Target.NATIVE_AARCH64}) {
            String arch = t.nativeArch();
            Assumptions.assumeTrue(NativeRiscv64E2ETest.hasToolchain(arch), "cross toolchain " + arch + " ausente — pulando");
            Path f = tmp.resolve("PROP" + arch + ".kf");
            Files.writeString(f, """
                    Object falha() { throw "boom" }
                    Void vigiar() {
                        val h = spawn falha()
                        var r = await h
                        println("nao-deveria")
                    }
                    main() {
                        val v = spawn vigiar()
                        try { await v; println("nao-deveria") }
                        catch (String e) { println("cap=" + e) }
                        println("fim")
                    }
                    """);
            Path outDir = tmp.resolve("prop-" + arch);
            CompilationResult r = driver.compile(f, outDir, t);
            assertTrue(r.success(), t + " compila: " + r.diagnostics().getDiagnostics());
            String out = NativeRiscv64E2ETest.runQemu(arch, outDir.resolve("Default/Main"));
            assertEquals("cap=boom\nfim", out, t + " rethrow no consumidor");
        }
    }

    @Test
    void spawnWorkerThrowAwaitedAndCaughtNative(@TempDir Path tmp) throws Exception {
        runNative(tmp, """
                Object falha() { throw "boom" }
                main() {
                    val h = spawn falha()
                    try {
                        await h
                        println("nao-deveria")
                    } catch (String e) {
                        println("cap=" + e)
                    }
                    println("fim")
                }
                """, "cap=boom\nfim");
    }

    @Test
    void spawnWorkerUnhandledThrowPropagatesNative(@TempDir Path tmp) throws Exception {
        // a falha sobe pelo worker INTERMEDIÁRIO (sem try) até o try da main —
        // era exatamente o cenário do longjmp cross-thread do §129.
        runNative(tmp, """
                Object falha() { throw "boom" }
                Void vigiar() {
                    val h = spawn falha()
                    var r = await h
                    println("nao-deveria")
                }
                main() {
                    val v = spawn vigiar()
                    try {
                        await v
                        println("nao-deveria2")
                    } catch (String e) {
                        println("main-capturou=" + e)
                    }
                    println("fim")
                }
                """, "main-capturou=boom\nfim");
    }

    @Test
    void spawnWorkerThrowIsolatedFromSiblingsNative(@TempDir Path tmp) throws Exception {
        // a falha de UM worker não pode contaminar os irmãos (o chain global
        // fazia o throw atravessar threads). 3 workers: um falha, dois somam.
        runNative(tmp, """
                Object falha() { throw "boom" }
                Int a42() { time.sleep(30); return 42 }
                Int a3() { return 3 }
                main() {
                    val f = spawn falha()
                    val s1 = spawn a42()
                    val s2 = spawn a3()
                    var ok = true
                    try { await f; ok = false } catch (String e) { }
                    println("s1=" + await s1 + " s2=" + await s2 + " ok=" + ok)
                }
                """, "s1=42 s2=3 ok=true");
    }

    @Test
    void spawnWorkerThrowPropagatesThroughSelectAnyNative(@TempDir Path tmp) throws Exception {
        // selectAny também relança a causa do handle excepcional.
        // §291: a forma antiga (`rapida(){return 7}` sem delay) NÃO fixava
        // qual handle concluía primeiro — dependia da sorte de agendamento
        // do pthread_create (~6.5% de miss medido no host ocioso, invertia
        // sob carga da suíte/CI). O contrato (oráculo JVM = anyOf) é
        // "primeiro a concluir no TEMPO"; a propagação do throw exige um
        // vencedor determinístico — aqui o worker que falha conclui pelo
        // menos 20ms antes. O ramo oposto (valor vence) já é coberto por
        // selectAnyJvm/selectAnyNative/selectAnyWaitPathReturnsValueAfterUsleep.
        runNative(tmp, """
                Object falha() { throw "boom" }
                Int devagar() { time.sleep(20); return 7 }
                main() {
                    val a = spawn falha()
                    val b = spawn devagar()
                    var ok = true
                    try { selectAny(a, b); ok = false } catch (String e) { println("sel=" + e) }
                    println("ok=" + ok)
                }
                """, "sel=boom\nok=true");
    }

    @Test
    void spawnWorkerThrowPropagatesThroughSelectAnyJvm(@TempDir Path tmp) throws Exception {
        // §291/paridade: o MESMO contrato no oráculo — handle excepcional
        // concluído primeiro é relançado pelo selectAny no consumidor (JVM).
        runJvm(tmp, """
                Object falha() { throw "boom" }
                Int devagar() { time.sleep(20); return 7 }
                main() {
                    val a = spawn falha()
                    val b = spawn devagar()
                    var ok = true
                    try { selectAny(a, b); ok = false } catch (String e) { println("sel=" + e) }
                    println("ok=" + ok)
                }
                """, "sel=boom\nok=true");
    }

    @Test
    void selectAnyWaitPathSurvivesUsleepNative(@TempDir Path tmp) throws Exception {
        // §252 (raiz): select_any cacheava size em -8(%rsp); o `call usleep`
        // empilhava o endereço de retorno NA MESMA SLOT e o re-scan relia a
        // slot -> varria idx 0,1,2 além do fim da lista -> pânico falso.
        // AMBOS os handles lenta (>1ms) força o caminho de espera; o scan
        // pós-usleep deve parar no fim da lista (2) e continuar esperando.
        runNative(tmp, """
                Object lenta() { time.sleep(2); throw "boom" }
                Int rapida() { time.sleep(30); return 7 }
                main() {
                    val a = spawn lenta()
                    val b = spawn rapida()
                    var ok = true
                    try { selectAny(a, b); ok = false } catch (String e) { println("sel=" + e) }
                    println("ok=" + ok)
                }
                """, "sel=boom\nok=true");
    }

    @Test
    void selectAnyWaitPathReturnsValueAfterUsleepNative(@TempDir Path tmp) throws Exception {
        // mesmo caminho de espera (2 lentos), mas o primeiro pronto entrega
        // VALOR: o re-scan pós-usleep não pode varrer pós-fim nem perder o
        // handle concluído.
        runNative(tmp, """
                Int lenta1() { time.sleep(2); return 11 }
                Int lenta2() { time.sleep(30); return 22 }
                main() {
                    val a = spawn lenta1()
                    val b = spawn lenta2()
                    println("sel=" + selectAny(a, b))
                }
                """, "sel=11");
    }

    private String runNative(Path tempDir, String source, String expected) throws java.io.IOException {
        Path file = tempDir.resolve("Main-" + System.nanoTime() + ".kf");
        Files.writeString(file, source);
        Path outDir = tempDir.resolve("out-" + System.nanoTime());
        CompilationResult r = driver.compile(file, outDir, Target.NATIVE);
        assertTrue(r.success(), "Native compile failed: " + r.diagnostics().getDiagnostics());
        Path bin = outDir.resolve("Default/Main");
        try {
            Process p = new ProcessBuilder(bin.toString()).redirectErrorStream(true).start();
            String output = new String(p.getInputStream().readAllBytes(),
                    StandardCharsets.UTF_8).replace("\r\n", "\n").trim();
            int ec = p.waitFor();
            assertEquals(0, ec, "Native exit code, output: " + output);
            if (expected != null) assertEquals(expected, output, "Native output");
            return output;
        } catch (InterruptedException e) {
            throw new java.io.IOException("interrupted", e);
        }
    }

    // ── helpers ──
    private String runJvm(Path tempDir, String source) throws java.io.IOException {
        return runJvm(tempDir, source, null);
    }

    // ── SG-020 (modelo de memória concorrente) — provas §4.3 e §4.4 ──

    // §4.3: statics sequentialmente consistentes — visibilidade e sem
    // reordenação: cada tarefa publica SEU delta final com HB de await;
    // o pai acumula APÓS os awaits (data race de read-modify-write NÃO é
    // atômico por definição do modelo — a abstração Kof p/ contagem
    // concorrente é Channel, não campo compartilhado).
    @Test
    void staticsAreSequentiallyConsistent(@TempDir Path tmp) throws Exception {
        String out = runJvm(tmp, """
                class Resultado {
                    static Int r1
                    static Int r2
                    static Int r3
                    static Int r4
                }
                Int soma1000() {
                    var s = 0
                    var i = 0
                    while (i < 1000) {
                        s = s + i
                        i = i + 1
                    }
                    return s
                }
                main() {
                    var a = spawn soma1000()
                    var b = spawn soma1000()
                    var c = spawn soma1000()
                    var d = spawn soma1000()
                    await a
                    await b
                    await c
                    await d
                    var total = 0 + await a + await b + await c + await d
                    println(total)
                }
                """);
        // 4 × (0+1+...+999) = 4 × 499500 = 1998000 — HB das bordas de await
        // garante que o pai vê as 4 escritas completas
        assertEquals("1998000", out.trim(), "HB de await deve tornar os resultados visíveis");
    }

    // §4.4: sem word-tearing em Long — leitor concorrente nunca observa
    // valor inválido (valores publicados são sempre pares e >= 0).
    @Test
    void noWordTearingOnLong(@TempDir Path tmp) throws Exception {
        String out = runJvm(tmp, """
                class Estado {
                    static Long v
                    static Bool pronto
                }
                Long escreve() {
                    var i = 0L
                    while (i < 5000L) {
                        Estado.v = i * 2L
                        i = i + 1L
                    }
                    Estado.pronto = true
                    return i
                }
                Bool le() {
                    var ok = true
                    var i = 0
                    while (i < 500000) {
                        var atual = Estado.v
                        if (atual < 0L || atual > 10000L) {
                            ok = false
                        }
                        i = i + 1
                    }
                    if (ok) {
                        println("valido")
                    } else {
                        println("invalido-observado")
                    }
                    return ok
                }
                main() {
                    var r = spawn le()
                    var w = spawn escreve()
                    await w
                    await r
                    println(Estado.v)
                }
                """);
        String[] lines = out.trim().split("\n");
        assertTrue(lines.length >= 2, "esperava valido/invalido + valor final: " + out);
        assertEquals("valido", lines[0], "Long nunca pode ser observado rasgado (borda 6)");
        // última escrita do loop: 4999*2 = 9998 (a var i chega a 5000 mas o
        // static guarda o valor DA ÚLTIMA ESCRITA, não do contador final)
        assertEquals("9998", lines[lines.length - 1], "valor final do escritor");
    }

    // §4.5-amendment (14/09): padrão STOP-FLAG — o fluxo A escreve `Bool = true`,
    // o fluxo B lê em laço fechado até observar. É a prova que faltava da
    // regra 5 (SC de campos de objetos compartilhados) e destrava o
    // DD-OTP-08 (`.stop()` do supervisor). Pré-fix (campo mutável sem
    // ACC_VOLATILE) o C2 hoistava o getfield fora do laço — leitor NUNCA
    // observava ("nao-observou" 3/3 medido); pós-fix "observou".
    // §245 (15/09): o orçamento de 500M iterações INT é fechado pelo C2
    // (~83 ms de laço vs 100 ms de sleep do escritor) → `nao-observou`
    // determinístico em host rápido, sem relação com o modelo de memória.
    // Orçamento subido para 5B (long): com o fix (leitura volátil, laço NÃO
    // eliminável) o leitor gira até ver `pode` aos +100ms (medido ~1,55 s para
    // 5B = ~15× a margem); sem o fix (getfield hoistado) o laço é fechado e
    // termina antes → "nao-observou". Q0 medido: vermelho sem o ACC_VOLATILE,
    // verde com ele.
    @Test
    void stopFlagFieldWriteObservedBySpinReader(@TempDir Path tmp) throws Exception {
        runJvm(tmp, """
                class Sinalizador {
                    Bool pode = false
                }
                main() {
                    var estado = Sinalizador()
                    var t = spawn {
                        var i = 0L
                        var visto = false
                        while (i < 5000000000L) {
                            if (estado.pode) { visto = true; i = 5000000001L }
                            i = i + 1L
                        }
                        if (visto) {
                            println("observou")
                        } else {
                            println("nao-observou")
                        }
                    }
                    time.sleep(100)
                    estado.pode = true
                    await t
                }
                """, "observou");
    }

    // mesma prova pelo caminho da CAPTURA de lambda (Box sintético — a forma
    // que o `.stop()` do supervisor usa: `var parar` capturada pelo spawn).
    @Test
    void stopFlagCapturedBoxObservedBySpinReader(@TempDir Path tmp) throws Exception {
        runJvm(tmp, """
                main() {
                    var parar = false
                    var t = spawn {
                        while (!parar) {
                            var x = 1
                            x = x + 1
                        }
                        println("observou")
                    }
                    time.sleep(100)
                    parar = true
                    await t
                }
                """, "observou");
    }

    private String runJvm(Path tempDir, String source, String expected) throws java.io.IOException {
        Path file = tempDir.resolve("Main-" + System.nanoTime() + ".kf");
        Files.writeString(file, source);
        Path outDir = tempDir.resolve("out-" + System.nanoTime());
        CompilationResult result = driver.compile(file, outDir, Target.JVM);
        assertTrue(result.success(), "JVM compile failed: " + result.diagnostics().getDiagnostics());
        try {
            Process p = new ProcessBuilder(System.getProperty("java.home") + "/bin/java",
                    "-cp", outDir.toString(), "Default.Main").redirectErrorStream(true).start();
            String output = new String(p.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                .replace("\r\n", "\n").trim();
            int ec = p.waitFor();
            assertEquals(0, ec, "JVM exit code, output: " + output);
            if (expected != null) {
                assertEquals(expected, output, "JVM output");
            }
            return output;
        } catch (InterruptedException e) {
            throw new java.io.IOException("interrupted", e);
        }
    }

    private String runJs(Path tempDir, String source) throws java.io.IOException {
        return runJs(tempDir, source, null);
    }

    private String runJs(Path tempDir, String source, String expected) throws java.io.IOException {
        Path file = tempDir.resolve("Main-" + System.nanoTime() + ".kf");
        Files.writeString(file, source);
        Path outDir = tempDir.resolve("out-" + System.nanoTime());
        CompilationResult result = driver.compile(file, outDir, Target.JS);
        assertTrue(result.success(), "JS compile failed: " + result.diagnostics().getDiagnostics());
        try (java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream()) {
            int ec = dev.kof.runtime.KofJsRunner.run(findJsEntry(outDir), buf,
                    java.io.InputStream.nullInputStream(), new java.io.ByteArrayOutputStream());
            String output = buf.toString(java.nio.charset.StandardCharsets.UTF_8).trim();
            assertEquals(0, ec, "JS exit code, output: " + output);
            if (expected != null) {
                assertEquals(expected, output, "JS output");
            }
            return output;
        }
    }

    @Test
    void crossNativeConcurrencyHelpersRun(@TempDir Path tmp) throws Exception {
        // CONC001 FECHADO (15/09): os 6 helpers existem no runtime cross
        // (fatia RtB48 + trampoline do spawn com cancel slot; TID real via
        // gettid(178) gravado pelo kernel no clone ctid). Antes do fechamento
        // este teste era o GATE NEGATIVO (compile-time CONC001 em cada helper);
        // agora o MESMO programa compila e executa sob qemu nas duas arches
        // com a ordem determinística: selectAny devolve 1 (a termina), done
        // true, poll devolve 1 APÓS `await b` (HB real), cancel marca,
        // cancelled()==false no main, awaitTimeout devolve o valor (prazo).
        // §256(b): o golden antigo NÃO tinha HB com o worker b (poll logo
        // após selectAny era 6/15 flake — ver SG-020/§4.4); `await b` antes
        // do poll fecha a janela por construção.
        for (Target t : new Target[]{Target.NATIVE_RISCV64, Target.NATIVE_AARCH64}) {
            String arch = t.nativeArch();
            Assumptions.assumeTrue(NativeRiscv64E2ETest.hasToolchain(arch), "cross toolchain " + arch + " ausente — pulando");
            Path f = tmp.resolve("M" + arch + ".kf");
            Files.writeString(f, """
                    Int trabalho() { return 1 }
                    main() {
                        val a = spawn trabalho()
                        val b = spawn trabalho()
                        println(selectAny(a, b))
                        println(done(a))
                        await b
                        println(poll(b))
                        cancel(a)
                        println(cancelled())
                        println(awaitTimeout(a, 100))
                    }
                    """);
            Path outDir = tmp.resolve("cross-" + arch);
            CompilationResult r = driver.compile(f, outDir, t);
            assertTrue(r.success(), t + " não deve mais reportar CONC001: " + r.diagnostics().getDiagnostics());
            String out = NativeRiscv64E2ETest.runQemu(arch, outDir.resolve("Default/Main"));
            assertEquals("1\ntrue\n1\nfalse\n1", out, t + " helpers output");
        }
    }

    @Test
    void crossNativeCancelDuringRunningWorker(@TempDir Path tmp) throws Exception {
        // Face cooperativa: cancel DURANTE a task em execução (worker dormindo)
        // retorna true e o main segue; await depois devolve o valor. Prova
        // também o ra-save do kof_cancel (não-leaf): sem ele, loop infinito
        // no próprio bloco de retorno (achado qemu -d exec, 2.2M loops).
        for (Target t : new Target[]{Target.NATIVE_RISCV64, Target.NATIVE_AARCH64}) {
            String arch = t.nativeArch();
            Assumptions.assumeTrue(NativeRiscv64E2ETest.hasToolchain(arch), "cross toolchain " + arch + " ausente — pulando");
            Path f = tmp.resolve("MC" + arch + ".kf");
            Files.writeString(f, """
                    Int longo() {
                        time.sleep(2000)
                        return 7
                    }
                    main() {
                        val a = spawn longo()
                        time.sleep(15)
                        var ok = cancel(a)
                        println(ok)
                        println(awaitTimeout(a, 10000))
                    }
                    """);
            Path outDir = tmp.resolve("crossc-" + arch);
            CompilationResult r = driver.compile(f, outDir, t);
            assertTrue(r.success(), t + ": " + r.diagnostics().getDiagnostics());
            String out = NativeRiscv64E2ETest.runQemu(arch, outDir.resolve("Default/Main"));
            assertEquals("true\n7", out, t + " cancel-during-run output");
        }
    }

    @Test
    void spawnAwaitStillGreenOnCrossTargets(@TempDir Path tmp) throws Exception {
        // O gate #91 não pode tocar spawn/await (existem em riscv/aarch via
        // clone+futex) nem pode silenciar o que já funcionava: programa só com
        // spawn/await/join continua compilando nos alvos cruzados.
        Path f = tmp.resolve("M.kf");
        Files.writeString(f, """
                Int trabalho() { return 42 }
                main() {
                    val r = spawn trabalho()
                    println(await r)
                }
                """);
        for (Target t : new Target[]{Target.NATIVE_RISCV64, Target.NATIVE_AARCH64}) {
            CompilationResult r = driver.compile(f, tmp.resolve("cross-ok-" + t), t);
            assertTrue(r.success(), t + " não deve reportar CONC001: " + r.diagnostics().getDiagnostics());
        }
    }

    @Test
    void crossNativeHelpersUnchangedOnX86JvmJs(@TempDir Path tmp) throws Exception {
        // Retroscompatibilidade (regra 2): o gate é só dos alvos cruzados.
        // O mesmo programa que falha em riscv/aarch continua compilando em
        // x86/JVM (helpers reais, provados por selectAnyNative/cancelCooperative
        // /awaitTimeout acima).
        Path f = tmp.resolve("M.kf");
        Files.writeString(f, """
                Int trabalho() { return 1 }
                main() {
                    val a = spawn trabalho()
                    val b = spawn trabalho()
                    println(selectAny(a, b))
                    println(done(a))
                    println(poll(b))
                    cancel(a)
                    println(cancelled())
                    println(awaitTimeout(a, 10))
                }
                """);
        for (Target t : new Target[]{Target.NATIVE, Target.JVM}) {
            CompilationResult r = driver.compile(f, tmp.resolve("x-" + t), t);
            assertTrue(r.success(), t + " não deve reportar CONC001: " + r.diagnostics().getDiagnostics());
        }
    }

    private static Path findJsEntry(Path dir) throws java.io.IOException {
        try (var s = Files.walk(dir)) {
            var opt = s.filter(p -> p.getFileName().toString().equals("Default.mjs")).findFirst();
            if (opt.isPresent()) return opt.get();
        }
        try (var s = Files.walk(dir)) {
            return s.filter(p -> p.toString().endsWith(".mjs"))
                    .findFirst().orElseThrow(() -> new java.io.IOException("no .mjs in " + dir));
        }
    }
}
