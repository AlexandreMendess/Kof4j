package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * #796 — o cursor do pool de literais String era por FUNÇÃO (`Ctx.dataNext`),
 * então `internString` devolvia `DATA_BASE` de novo em cada função e os
 * segmentos de dados se sobrepunham (saída errada em silêncio no WASI). E o pool
 * não era conferido contra o início do heap (`HEAP_BASE`).
 *
 * Prova SEM toolchain (wasmtime ausente aqui): lê os bytes do `.wasm` emitido e
 * extrai as seções `data` (11) e `globals` (6).
 */
class WasmStringLiteralPoolE2ETest {

    private static final String TWO_FUNCS = """
            Int tag() {
                println("alpha")
                return 1
            }

            main(String[] args) {
                println("beta-gamma")
                println(tag())
            }
            """;

    private byte[] compileWasi(Path dir, String src) throws Exception {
        Path file = dir.resolve("Main.kf");
        Files.writeString(file, src);
        var result = new CompilerDriver().compile(file, dir.resolve("out"), Target.WASI);
        assertTrue(result.success(), "wasi compile must succeed: " + result.diagnostics().getDiagnostics());
        Path wasm = dir.resolve("out").resolve("Default").resolve("Main.wasm");
        assertTrue(Files.exists(wasm), "Main.wasm must be emitted");
        return Files.readAllBytes(wasm);
    }

    // ------------------------------------------------------------------
    // mini-parser do .wasm (LEB128 + secoes 6/11) — determinista, sem host
    // ------------------------------------------------------------------

    private record DataSeg(int addr, byte[] bytes) {}

    private static int uleb(byte[] b, int[] pos) {
        int r = 0, shift = 0;
        while (true) {
            int c = b[pos[0]++] & 0xff;
            r |= (c & 0x7f) << shift;
            if ((c & 0x80) == 0) return r;
            shift += 7;
        }
    }

    private static int sleb(byte[] b, int[] pos) {
        int r = 0, shift = 0, c;
        do {
            c = b[pos[0]++] & 0xff;
            r |= (c & 0x7f) << shift;
            shift += 7;
        } while ((c & 0x80) != 0);
        if (shift < 32 && (c & 0x40) != 0) r |= -(1 << shift);
        return r;
    }

    private static List<DataSeg> dataSegments(byte[] bin) {
        List<DataSeg> out = new ArrayList<>();
        int[] p = {8}; // header
        while (p[0] < bin.length) {
            int id = bin[p[0]++] & 0xff;
            int size = uleb(bin, p);
            int end = p[0] + size;
            if (id == 11) {
                int count = uleb(bin, p);
                for (int i = 0; i < count; i++) {
                    p[0]++; // flags = 0x00 (ativo, mem 0)
                    assertEquals(0x41, bin[p[0]++] & 0xff, "i32.const");
                    int addr = sleb(bin, p);
                    assertEquals(0x0b, bin[p[0]++] & 0xff, "end");
                    int len = uleb(bin, p);
                    byte[] payload = new byte[len];
                    System.arraycopy(bin, p[0], payload, 0, len);
                    p[0] += len;
                    out.add(new DataSeg(addr, payload));
                }
            }
            p[0] = end;
        }
        return out;
    }

    private static List<Integer> globalInits(byte[] bin) {
        List<Integer> out = new ArrayList<>();
        int[] p = {8};
        while (p[0] < bin.length) {
            int id = bin[p[0]++] & 0xff;
            int size = uleb(bin, p);
            int end = p[0] + size;
            if (id == 6) {
                int count = uleb(bin, p);
                for (int i = 0; i < count; i++) {
                    p[0]++; // valtype 0x7f
                    p[0]++; // mut 0x01
                    assertEquals(0x41, bin[p[0]++] & 0xff, "i32.const");
                    out.add(sleb(bin, p));
                    assertEquals(0x0b, bin[p[0]++] & 0xff, "end");
                }
            }
            p[0] = end;
        }
        return out;
    }

    private static int addrOf(List<DataSeg> segs, String literal) {
        byte[] want = literal.getBytes(StandardCharsets.UTF_8);
        for (DataSeg s : segs) {
            if (s.bytes().length == want.length && java.util.Arrays.equals(s.bytes(), want)) return s.addr();
        }
        return -1;
    }

    @Test
    void literalsInDifferentFunctionsGetDistinctAddresses(@TempDir Path dir) throws Exception {
        List<DataSeg> segs = dataSegments(compileWasi(dir, TWO_FUNCS));
        int alpha = addrOf(segs, "alpha");
        int beta = addrOf(segs, "beta-gamma");
        assertTrue(alpha >= 0, "o literal 'alpha' deve estar num segmento de dados");
        assertTrue(beta >= 0, "o literal 'beta-gamma' deve estar num segmento de dados");
        assertNotEquals(alpha, beta,
                "#796 Face 1: dois literais em funções distintas caíram no MESMO endereço " + alpha);
    }

    @Test
    void stringPoolNeverOverlapsTheHeap(@TempDir Path dir) throws Exception {
        // Face 2: 40 literais DISTINTOS de 500 chars, um println cada.
        StringBuilder sb = new StringBuilder("main(String[] args) {\n");
        for (int i = 0; i < 40; i++) {
            StringBuilder lit = new StringBuilder();
            lit.append((char) ('A' + i)); // prefixo unico: garante 40 literais distintos
            for (int j = 0; j < 499; j++) lit.append((char) ('a' + ((i + j) % 26)));
            sb.append("    println(\"").append(lit).append("\")\n");
        }
        sb.append("}\n");
        byte[] bin = compileWasi(dir, sb.toString());
        List<DataSeg> segs = dataSegments(bin);
        int lastEnd = 0;
        for (DataSeg s : segs) lastEnd = Math.max(lastEnd, s.addr() + s.bytes().length);
        List<Integer> globals = globalInits(bin);
        assertFalse(globals.isEmpty(), "o heap (global 0) deve existir quando há literais String");
        int heap = globals.get(0);
        assertTrue(heap >= lastEnd,
                "#796 Face 2: o heap começa em " + heap + " mas o último byte de dados é " + lastEnd
                        + " — o primeiro bloco do bump heap cairia dentro de um literal");
    }
}
