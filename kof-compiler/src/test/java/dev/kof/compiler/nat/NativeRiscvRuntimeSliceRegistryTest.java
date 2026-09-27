package dev.kof.compiler.nat;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Issue #97 / S-4.1: o inventário de PEÇAS do runtime riscv64 é FIEL ao
 * caminho de produção, sem tocar na emissão (aarch64 herda a mesma
 * concatenação via tradutor). Pilares:
 * (a) concatenação das 48 peças POR REFLEXÃO/ordem-derivada = byte-a-byte o
 *     bloco que o NativeArchEmitter anexa hoje (a prova "bins idênticos");
 * (b) 1 dona por símbolo kof_* (0 homônimos globl — medido);
 * (c) needs fechados no mapa ∪ program-side;
 * (d) 0 homônimos .L cross-peça, MAS 73 arestas .L cross-peça → o fecho
 *     UNIFICADO é obrigatório no riscv também (a lição da S-2.5);
 * (e) piso print/panic/alloc = 7/48 peças (medido).
 */
class NativeRiscvRuntimeSliceRegistryTest {

    @Test
    void piecesRenderByteIdenticalToProduction() {
        assertEquals(
                NativeRiscvAsm.RISCV_RUNTIME_ASM + NativeRiscvAsm.RISCV_STRN002_ASM
                        + NativeRiscvAsm.RISCV_RUNTIME_ASM_B + NativeRiscvAsm.RISCV_MAPSET_ASM,
                RiscvSlices.renderRuntime(),
                "concatenação reflexiva na ordem derivada deve ser byte-idêntica ao runtime de produção");
    }

    @Test
    void piecesCoverProductionCount() {
        List<RiscvSlices.Piece> ps = RiscvSlices.pieces();
        assertTrue(ps.size() >= 40, "esperava ~48 peças, veio " + ps.size());
    }

    @Test
    void eachSymbolHasExactlyOneOwner() {
        Map<String, Integer> idx = RiscvSlices.providerIndex(); // lança se houver 2 donos
        assertTrue(idx.size() > 200, "símbolos definidos: " + idx.size());
    }

    @Test
    void needsAreClosedOverMapPlusProgramSide() {
        Map<String, Integer> gp = RiscvSlices.providerIndex();
        Set<String> ext = RiscvSlices.programSideSymbols();
        for (RiscvSlices.Piece p : RiscvSlices.pieces()) {
            for (String n : p.needs()) {
                assertTrue(gp.containsKey(n) || ext.contains(n),
                        "needs órfão em " + p.className() + "." + p.field() + ": " + n);
            }
        }
    }

    @Test
    void localLabelEdgesExistAndDemandUnifiedClosure() {
        // 0 homônimos cross-peça (namespace riscv) mas >0 arestas .L cross-peça
        // (medido 33 pares distintos — o probe de 73 contava OCORRÊNCIAS de
        // referência, o modelo dedupe por peça): podar a peça-dona de um .L lido
        // por peça viva quebraria o `as` — a BFS da poda DEVE ser o fecho
        // unificado (a regra da S-2.5 vale no riscv também). travado:
        assertTrue(RiscvSlices.crossPieceLocalEdgeCount() >= 30,
                "arestas .L cross-peça (medido 33) sumiram — o modelo riscv não exige mais fecho unificado?");
        // e o piso unificado é pequeno (dá podar 48→poucas):
        Set<String> floor = Set.of("kof_panic", "kof_alloc", "kof_print", "kof_println",
                "kof_print_string", "kof_println_string");
        Set<Integer> uni = RiscvSlices.reachableFrom(floor, Set.of());
        // 27/09: o piso medido subiu de ~7 p/ 12 — o modelo é por PEÇA, e a
        // fatia do log estruturado pôs `call kof_json_enc_elem` na peça B4
        // (kof_exc_slot); isso arrasta B3 (encoders JSON FP/Long) → B42–B45
        // (double/float→string) e, via kof_list_new (B0), scheduler/mapset.
        // Não é regressão de comportamento (a poda por peça é conservadora);
        // é drift de limite. Trava em 12 (não em ~7) p/ pegar bloat real novo.
        assertTrue(uni.size() <= 12, "piso riscv deveria ser ~12 peças (B4 json+FP), veio " + uni.size());
    }

    /** R6/S5.4 (24/09): a mensagem DB001 do runtime CROSS passou a anunciar
     *  o que JÁ existe (sqlite:, mysql://, mariadb://) — o wire mysql foi
     *  portado (peça `B73`); esquemas fora disso seguem recusados no connect
     *  com código NOMEADO, nunca handle nulo. Medido no runtime de produção. */
    @Test
    void crossDb001MessageAdvertisesPortedSchemesOnly() {
        String asm = NativeRiscvAsm.RISCV_RUNTIME_ASM_B;
        assertTrue(asm.contains("DB001: unsupported db scheme"),
                "constante do diagnostico DB001 ausente no runtime cross");
        assertTrue(asm.contains("native cross: sqlite:, mysql://, mariadb://"),
                "o cross deve anunciar os esquemas que REALMENTE aceita (R6/S5.4)");
        assertFalse(asm.contains("(native: sqlite:, mysql://)"),
                "a mensagem cross nao pode ser a do x86 (contrato distinto)");
        assertTrue(asm.contains("kof_db_connect_mysql"),
                "a peca B73 (connect mysql/mariadb cross) deve estar no runtime");
    }

    @Test
    void mandatoryFloorIsSmall() {
        Set<Integer> floor = RiscvSlices.mandatoryRoots();
        // piso do hello riscv: print/panic/alloc — pequeno, prova que dá podar
        assertTrue(floor.size() < RiscvSlices.pieces().size() / 2,
                "piso deveria ser minoria das peças, veio " + floor.size());
    }

    @Test
    void keepAllSubsetIsByteIdenticalToProduction() {
        Set<Integer> all = new java.util.LinkedHashSet<>();
        for (RiscvSlices.Piece p : RiscvSlices.pieces()) all.add(p.index());
        assertEquals(RiscvSlices.renderRuntime(), RiscvSlices.renderSubset(all),
                "keep-all não pode injetar NENHUMA diretiva extra (contexto nunca diverge)");
    }

    /** Trava a injeção de seção que impede o SIGILL de 12/09: ao podar, o
     *  estado de seção na ENTRADA de cada peça mantida tem de ser o MESMO que
     *  ela veria na concatenação de produção — senão código cai em .rodata
     *  (símbolo `R`) → instrução ilegal em tempo de execução. Verificado para
     *  cada um dos primeiros 12 buracos. Helper próprio p/ não depender do
     *  formato da string que cada implementação injeta. */
    @Test
    void sectionContextIsRestoredAcrossHoles() {
        List<RiscvSlices.Piece> ps = RiscvSlices.pieces();
        String prod = RiscvSlices.renderRuntime();
        for (int hole = 0; hole < 12 && hole < ps.size(); hole++) {
            final int skip = hole;
            Set<Integer> keep = new java.util.LinkedHashSet<>();
            for (int i = 0; i < ps.size(); i++) if (i != skip) keep.add(i);
            String sub = RiscvSlices.renderSubset(keep);
            int atSub = 0; // busca SEQUENCIAL (indexOf global acharia subtexto repetido)
            for (RiscvSlices.Piece p : ps) {
                if (!keep.contains(p.index())) continue;
                int at = sub.indexOf(p.text(), atSub);
                assertTrue(at >= 0, "hole=" + hole + ": texto da peça " + p.index() + " não no subset");
                assertEquals(sectionStateBefore(prod, prod.indexOf(p.text())),
                        sectionStateBefore(sub, at),
                        "hole=" + hole + ": seção de entrada da peça " + p.index()
                                + " diverge da produção (risco de SIGILL)");
                atSub = at + p.text().length();
            }
        }
    }

    /** Seção ativa imediatamente antes do offset `at` (normaliza `.text` e
     *  `.section .text` p/ o MESMO formato, independente da injeção). O estado
     *  inicial é o do head do NativeArchEmitter: `.section .text`. */
    private static String sectionStateBefore(String text, int at) {
        String cur = "text";
        for (String l : text.substring(0, Math.max(0, at)).lines().toList()) {
            String t = l.trim();
            String s = null;
            if (t.startsWith(".section")) s = t.split("\\s+")[1];
            else if (t.equals(".text") || t.equals(".data") || t.equals(".bss") || t.equals(".rodata")) s = t.substring(1);
            if (s != null) cur = s.startsWith(".") ? s.substring(1) : s;
        }
        return cur;
    }
}
