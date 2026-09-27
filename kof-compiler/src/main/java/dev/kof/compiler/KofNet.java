package dev.kof.compiler;

import java.util.List;

/**
 * Compile-time dispatch for {@code kof.net} (STDLIB S8, plan §4 — decisão
 * 09/09: 6 escalares em vez de record, pois nenhuma fn de runtime asm devolve
 * objeto estruturado no Native; mesma família do precedente validation).
 *
 * Semântica v1 travada em plan-stdlib-expansion §4 (RFC 3986 subset, escopo
 * honesto): scheme = [A-Za-z][A-Za-z0-9+.-]* antes do 1º ':' (senão "");
 * authority só após "//" (userinfo após o último '@' ignorado; host até o 1º
 * ':' — v1 SEM colchetes IPv6, documentado); path até '?'/'#'; query após o
 * 1º '?' até '#'; fragment após o 1º '#'; campo ausente => ""; null => null;
 * NUNCA lança. queryEncode/Decode = fachada de intenção sobre encoding.url*.
 *
 * NET001 CLOSED 09/09 (padrão SECN000/ENC002-histórico): byte-scan nativo
 * fechado nos 3 nativos — x86 (RuntimeNet S8-B), riscv64 (slice B24) e
 * aarch64 (mesmo asm via tradutor); `net.*` (incl. queryEncode/Decode, que
 * compõem encoding.url* já portado) roda em todos os alvos e nenhum caminho
 * é gated. Prova: `KofNetTest.netOnCrossArch` (17 vetores oracle, qemu) —
 * ver `conformance-matrix.md` §net.
 */
public final class KofNet {

    private KofNet() {}

    private static final Type STR = BuiltinTypes.STRING;

    static final List<String> NAMESPACES = List.of("net");

    static boolean isNetNamespace(String name) {
        return NAMESPACES.contains(name);
    }

    record NetCall(String function, Type returnType, List<Type> parameterTypes) {}


    /** X10 fatia 1: nomes aceitos pelo staticMethod (catálogo p/ LSP).
     *  GUARDA: StdCatalogTest exige == case literals do switch(name) abaixo
     *  (19/09 LSP-A fatia 3: a família `case "scheme", "host", ...` binda os 8
     *  e a lista só tinha o primeiro literal — drift do tipo db/process). */
    static List<String> functions() {
        return List.of("scheme", "host", "port", "path", "query",
                "fragment", "queryEncode", "queryDecode");
    }
    static NetCall staticMethod(String namespace, String name, List<Type> argTypes) {
        int argc = argTypes.size();
        return switch (name) {
            case "scheme", "host", "port", "path", "query", "fragment",
                    "queryEncode", "queryDecode" -> argc == 1
                    ? new NetCall("kof_net_" + name, STR, List.of(STR)) : null;
            default -> null;
        };
    }

    static boolean supportedOn(@SuppressWarnings("unused") String function,
            @SuppressWarnings("unused") Target target) {
        // NET001 fechado 09/09: `net.*` roda nos 3 nativos (x86 + riscv B24
        // + aarch via tradutor) — nenhum alvo é gated, por isso `true`.
        return true;
    }

    static String gapCode(String function) {
        // Vestigial: NET001 fechado 09/09 (conformance-matrix §net) e
        // `supportedOn` devolve true em todo alvo, então nenhum caminho emite
        // este código hoje. Mantido porque KofStd.gapCode é a rota genérica.
        return "NET001";
    }
}
