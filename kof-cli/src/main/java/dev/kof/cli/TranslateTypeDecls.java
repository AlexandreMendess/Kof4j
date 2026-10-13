package dev.kof.cli;

import java.util.ArrayList;
import java.util.List;

/**
 * Satélite do REFACTOR-500 (check_500, TranslateEmitter 627→<600): a análise
 * de DECLARAÇÕES DE TIPO (record — corpo não-vazio é recusa honesta, R6/Q4
 * 13/09). Todos os acessos de estado do emitor passam pelo parâmetro {@code em}.
 */
final class TranslateTypeDecls {

    private TranslateTypeDecls() {}

    static void parseRecord(TranslateEmitter em) {
        em.p.expect("record");            String name = em.p.next().text;
        String typeParams = em.p.at("<") ? em.parseTypeParams() : "";
        List<String> components = new ArrayList<>();
        if (em.p.at("(")) {
            em.p.next();
            while (!em.p.at(")")) {
                String type = TranslateTypes.kofType(em.p.next().text);
                String cname = em.p.next().text;
                components.add(type + " " + cname);
                if (em.p.at(",")) em.p.next();
            }
            em.p.expect(")");
        }
        if (em.p.at("{")) {
            // Corpo do record (construtor compacto, accessors, métodos) —
            // Kof record é só os componentes. Corpo VAZIO `{}` é no-op;
            // corpo com conteúdo era pulado em SILÊNCIO → validações/
            // overrides sumiam (R6, Q4 13/09).
            if (!em.p.peek(1).text.equals("}")) {
                throw new TranslateException(
                        "record with a body (compact constructor/accessors/methods) has no "
                        + "equivalent in Kof (record = components only) — manual review");
            }
            em.skipBlock();
        } else {
            em.p.expect(";");
        }
        em.out.append("record ").append(name).append(typeParams).append('(')
           .append(String.join(", ", components)).append(")\n");
    }
}
