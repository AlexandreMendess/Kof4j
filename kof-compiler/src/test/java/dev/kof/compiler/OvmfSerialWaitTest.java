package dev.kof.compiler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Prova determinística da espera progress-aware da §511 — roda SEM qemu/OVMF
 *  (host sem toolchain certifica o fix; os E2E de boot continuam nos hosts
 *  com toolchain). O antigo loop de parede fixa punia boot lento e premiava
 *  boot congelado; aqui cada assinatura do flake medido tem um caso. */
class OvmfSerialWaitTest {

    private static void append(Path log, String s) throws Exception {
        Files.write(log, s.getBytes(StandardCharsets.ISO_8859_1),
                java.nio.file.StandardOpenOption.CREATE,
                java.nio.file.StandardOpenOption.APPEND);
    }

    @Test
    void slowButProgressingBootIsCaughtPastTheOldFixedWindow(@TempDir Path dir) throws Exception {
        // assinatura 1: sob carga o boot progride com pausa entre as fases —
        // bytes a cada 1,5 s, marcador só aos ~7,5 s. A janela antiga de teste
        // (fixa, aqui simulada em 3 s) cortaria no meio; a progress-aware
        // espera enquanto há crescimento.
        Path log = dir.resolve("ser.log");
        Thread writer = new Thread(() -> {
            try {
                for (int i = 0; i < 5; i++) {
                    TimeUnit.MILLISECONDS.sleep(1_500);
                    append(log, "K");
                }
                append(log, "KO-RING MAIN");
            } catch (Exception ignored) {
            }
        });
        writer.setDaemon(true);
        writer.start();
        String text = OvmfSerialWait.untilDone(log, t -> t.contains("KO-RING MAIN"),
                5_000, 20_000);
        assertTrue(text.contains("KO-RING MAIN"),
                "boot lento-mas-progressando foi cortado pela espera: " + text);
    }

    @Test
    void frozenBootAbortsOnTheIdleWindowNotTheCap(@TempDir Path dir) throws Exception {
        // assinatura 2 (a medida do flake): "imprime o 1º byte e estanca".
        // A espera deve desistir perto do idle (nunca queimando o cap de 240 s
        // real), para a tentativa seguinte começar limpa.
        Path log = dir.resolve("ser.log");
        append(log, "K");
        long start = System.nanoTime();
        String text = OvmfSerialWait.untilDone(log, t -> t.contains("NECA"),
                TimeUnit.MILLISECONDS.toMillis(2_000), TimeUnit.SECONDS.toMillis(60));
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        assertFalse(text.contains("NECA"), "marcador inventado apareceu?");
        assertTrue(elapsedMs >= 1_500, "abortou antes do idle: " + elapsedMs + "ms");
        assertTrue(elapsedMs < 20_000,
                "queimou o cap em vez de abortar no idle: " + elapsedMs + "ms");
    }

    @Test
    void absentSerialIsHonestIdleAbortNotAnError(@TempDir Path dir) throws Exception {
        // assinatura 3: qemu nem abriu o serial (host travado). Ausência é caso
        // ocioso desde o início — ~idle, texto vazio, sem exceção.
        Path log = dir.resolve("nao-existe.log");
        long start = System.nanoTime();
        String text = OvmfSerialWait.untilDone(log, t -> t.contains("X"),
                TimeUnit.MILLISECONDS.toMillis(1_500), TimeUnit.SECONDS.toMillis(60));
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        assertEquals("", text);
        assertTrue(elapsedMs >= 1_000 && elapsedMs < 20_000,
                "ausência should abortar no idle (~1.5 s), veio " + elapsedMs + "ms");
    }

    @Test
    void capStillBoundsABootThatTricklesForeverWithoutTheMarker(@TempDir Path dir)
            throws Exception {
        // falha REAL (marcação errada com bytes pingando) não pode pendurar a
        // suíte: o teto absoluto encerra, e o chamador decide o veredito.
        Path log = dir.resolve("ser.log");
        append(log, "junk");
        long start = System.nanoTime();
        String text = OvmfSerialWait.untilDone(log, t -> t.contains("NECA"),
                TimeUnit.SECONDS.toMillis(60), TimeUnit.SECONDS.toMillis(3));
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        assertTrue(elapsedMs >= 2_500 && elapsedMs < 20_000,
                "cap de 3 s não encerrou o trickle: " + elapsedMs + "ms");
    }
}
