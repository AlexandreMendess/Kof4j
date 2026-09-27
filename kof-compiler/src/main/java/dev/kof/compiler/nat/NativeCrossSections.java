package dev.kof.compiler.nat;

/**
 * S-5 (issue #97, T1b — parte cross): cada FUNÇÃO do subset mantido do
 * runtime abre a própria `.section .text.<nome>,"ax"` para que o
 * `ld --gc-sections` delete os irmãos mortos dentro de uma peça mantida — a
 * granularidade fina que faltava à S-4 (peças inteiras). Transformação
 * puramente textual e determinística: padrão `.globl X` → (`type`) → `X:` em
 * seção `.text` anônima; labels locais `.L*`, dados (.data/.bss/.rodata) e o
 * programa (fora do subset) ficam como estão — o scan conservative existe SÓ
 * no x86 (lá a fase exige `kof_heap_root_end` primeiro; cross não tem GC no
 * asm → sem raiz oculta, seguro deletar). keep-all continua byte-idêntico (a
 * seção-injection só roda no caminho podado). aarch64: a linha passa ilesa
 * pelo tradutor (diretiva não-matching → passthrough) e o GAS ARMv8 aceita a
 * mesma sintaxe de flags.
 *
 * <p><b>Lição medida (§445, 22/09):</b> a transformação cria branches
 * cross-section para labels locais `.L*` (entradas-alias: um stub
 * `j .Lcorpo` com o corpo definido no range de outra função — a forma do
 * `kof_encoding_base64Encode` → `.Lv_b64_enc`). Isso é LEGAL no ELF — desde
 * que o assembler emita a relocação. O `riscv64-linux-gnu-as -mno-relax`
 * (flag que o backend usava) ligava esse salto à frente direto na seção
 * corrente, SEM `.rela` (o `.o` mostra `j 4`, sem símbolo); quando só a
 * primeira entrada era referenciada, o `ld --gc-sections` órfã a seção do
 * corpo e o salto vira si mesmo (`j .` — hang silencioso, `base64Encode`/
 * `base64Decode` travavam para sempre no qemu). O fix de causa-raiz foi
 * remover o `-mno-relax` do `as` (o `--no-relax` do LINK continua em
 * `NativeCrossLink.ldArgs` — é ele que impede a gp-relaxation de `la` com
 * `_start` sem gp; medido: 0 instruções gp-relative no hello, S-5 preservada).
 * Ver `known-bugs.md §445`.
 */
final class NativeCrossSections {

    private NativeCrossSections() {}

    /** Transforma o texto do subset: `.globl X` + `X:` ganha
     *  `.section .text.X,"ax"` (o par é o candidato; o resto fica como está). */
    static String sectionizeTextFunctions(String text) {
        return sectionizeTextFunctions(text, null);
    }

    /** Como {@link #sectionizeTextFunctions(String)}, mas só injeta a seção para
     *  labels que comecem com {@code onlyPrefix} ({@code null} = todas).
     *
     *  <p><b>x86/freestanding (B-1b, medida 23/09):</b> o passe era aplicado ao
     *  arquivo INTEIRO (programa + runtime) e movia também as funções do
     *  programa. O `.as` rejeitava (`can't resolve .text.<fn> - <nextFn>`) porque
     *  o DWARF `.debug` do codegen x86 expressa o range de uma função como
     *  `símbolo_fim - próximo_símbolo` — com funções em seções diferentes a
     *  diferença deixa de ser resolvível. Só o RUNTIME precisa da poda (o
     *  programa é sempre alcançado) → filtrar por {@code "kof_"} mantém as
     *  funções do programa juntas numa `.text` só e as expressões voltam a
     *  resolver. O cross (subset puro, sem DWARF) continua chamando o overload
     *  sem filtro. */
    static String sectionizeTextFunctions(String text, String onlyPrefix) {
        String[] lines = text.split("\n", -1);
        StringBuilder out = new StringBuilder();
        boolean inText = false;
        String pendingFn = null;   // último .globl sem label visto ainda
        for (int i = 0; i < lines.length; i++) {
            String s = lines[i].strip();
            if (s.startsWith(".section")) {
                inText = s.startsWith(".section .text") || s.equals(".section .text");
                pendingFn = null;
                out.append(lines[i]).append('\n');
                continue;
            }
            if (inText && s.startsWith(".globl")) {
                pendingFn = s.substring(".globl".length()).trim();
                out.append(lines[i]).append('\n');
                continue;
            }
            if (inText && s.endsWith(":") && !s.contains(" ") && !s.startsWith(".L")) {
                String label = s.substring(0, s.length() - 1);
                boolean selected = onlyPrefix == null || label.startsWith(onlyPrefix);
                if (pendingFn != null && pendingFn.equals(label) && selected
                        && label.matches("[A-Za-z_][A-Za-z0-9_]*")) {
                    out.append("    .section .text.").append(label).append(",\"ax\"\n");
                }
                pendingFn = null;
            }
            out.append(lines[i]).append('\n');
        }
        // split(-1) + append('\n') por linha: reconstitui exatamente o
        // original quando nada é injetado (e o último '' do split vira o
        // newline final — remove o '\n' sobra se o texto não terminava em \n)
        String r = out.toString();
        if (!text.endsWith("\n") && r.endsWith("\n")) r = r.substring(0, r.length() - 1);
        return r;
    }
}
