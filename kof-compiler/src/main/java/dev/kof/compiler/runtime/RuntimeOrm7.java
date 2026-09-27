package dev.kof.compiler.runtime;

/**
 * F2c2 (D-DB-GAPS, 21/09): faces {@code where} da leitura row-object no
 * runtime Native x86-64 - {@code kof_orm_where} (igualdade, 6 args) e
 * {@code kof_orm_where_op} (op do usuario, 7 args com className na stack)
 * compartilham UM corpo: whitelist do operador identica ao host (medida:
 * throw exato {@code ORM operator not allowed: <op>}), SQL
 * {@code SELECT * FROM "t" WHERE "f" <op> ?} com bind do value pelo mesmo
 * classificador do key no {@code RuntimeOrm5} (box 284 / KofString do
 * coerce do call-site / null) e o loop de campos do {@code RuntimeOrm6}
 * (397 incluso) acumulado em {@code kof_list_new}/{@code kof_list_add};
 * vazio = lista VAZIA (nunca null), como o host.
 *
 * <p>Contrato de pilha (F1c): prologo com {@code andq}, frame 168, todo
 * {@code call} de C sai com rsp = 0; stmt em {@code r12}, SQL em
 * {@code rbx} (padrao Orm4/Orm5/Orm6).
 *
 * <p>Split do gate {@code <=500} (TIER 13.5, 23/09): o asm vive em
 * {@link RuntimeOrm7Setup} (globals/whitelist/SQL/bind) e
 * {@link RuntimeOrm7Fetch} (loop/leitura/fail/strings). A ordem de
 * concatenacao abaixo E o contrato - as labels {@code .Lorm7_*} atravessam os
 * dois blocos e o assembler resolve no {@code .s} completo; a saida emitida e
 * byte-identica a de antes do split.
 */
public final class RuntimeOrm7 {

    private RuntimeOrm7() {}

    public static void emit(StringBuilder sb) {
        sb.append(RuntimeOrm7Setup.WHERE_ASM_SETUP);
        sb.append(RuntimeOrm7Fetch.WHERE_ASM_FETCH);
    }
}
