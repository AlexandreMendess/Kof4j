package dev.kof.compiler;

import java.util.List;

/**
 * `infra "prod" { ... }` — bloco declarativo do Makealive (linha 3.2 do
 * plano; {@code DECISIONS.md} §D-MAKEALIVE-SYNTAX 21/09). É AÇÚCAR PURO:
 * o lowering vira `design(): Infrastructure` chamando as faces do host
 * (`resource`/`prop`/`requires`) — sem keyword/token/tipo/runtime novo.
 */
public record InfraDeclarationNode(SourcePosition position, String name,
                                   List<StatementNode> body) implements AstNode {
}
