package dev.kof.compiler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Predicate;

/** Espera bounded e progress-aware pelo log `-serial file:` de um boot OVMF (§511).
 *
 *  A janela fixa de parede, calibrada em host ocioso, punia o boot LENTO sob
 *  carga (abortava no meio do progresso) e premiava o boot CONGELADO (queimava a
 *  janela inteira esperando o que nunca chega — a assinatura medida do flake:
 *  "imprime o 1º byte e estanca"). Aqui: enquanto o serial cresce a espera
 *  continua (até um teto absoluto); parado de crescer por {@code idleMillis},
 *  aborta cedo e a tentativa seguinte começa limpa. O idle é calibrado no antigo
 *  abort (60 s) — o abort nunca fica PIOR que antes; o que muda é o lado bom:
 *  um boot lento-mas-progressando não é mais cortado no meio. Uma falha REAL
 *  (saída errada, nenhuma saída) falha igual em todas as tentativas — não há
 *  progresso a estender. Lição §418: nunca suíte pendurada — o filho é morto
 *  pelo chamador. */
final class OvmfSerialWait {

    private OvmfSerialWait() {
    }

    static String serialText(Path log) throws IOException {
        return Files.readString(log, StandardCharsets.ISO_8859_1).replace("\0", "");
    }

    /** Polla {@code log} até {@code done} aceitar o texto, o serial ficar ocioso
     *  por {@code idleMillis}, ou {@code capMillis} passar. Devolve o último texto
     *  lido (pode não conter o marcador — quem decide é o chamador). Ausência do
     *  arquivo é caso ocioso desde o início: aborta em ~idleMillis, nunca lança. */
    static String untilDone(Path log, Predicate<String> done, long idleMillis, long capMillis)
            throws InterruptedException, IOException {
        long start = System.currentTimeMillis();
        long lastGrowth = start;
        long lastSize = -1;
        String text = "";
        long step = Math.max(50, Math.min(500, idleMillis / 10));
        while (true) {
            long size = Files.exists(log) ? Files.size(log) : 0L;
            if (size != lastSize) {
                lastSize = size;
                lastGrowth = System.currentTimeMillis();
            }
            if (size > 0) {
                text = serialText(log);
                if (done.test(text)) {
                    return text;
                }
            }
            long now = System.currentTimeMillis();
            if (now - start >= capMillis || now - lastGrowth >= idleMillis) {
                return text;
            }
            Thread.sleep(Math.min(step, Math.max(1, idleMillis - (now - lastGrowth))));
        }
    }
}
