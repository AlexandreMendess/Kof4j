package dev.kof.compiler;

import dev.kof.compiler.parser.Lexer;
import dev.kof.compiler.parser.Parser;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Pacote virtual {@code kof.makealive} (tracker 3.1, plano
 * {@code docs/development/makealive-plan.md} §5; superfície assinada por
 * {@code DECISIONS.md} §D-MAKEALIVE 20/09 — Q1 nome, Q4 faces flat em
 * inglês {@code resource}/{@code requires}/{@code plan}/{@code apply}/
 * {@code destroy}). O host é escrito EM KOF ({@code dev/kof/makealive-host.kf}
 * no resource) e injetado FLAT no {@code import kof.makealive} EXPLÍCITO —
 * exatamente o mecanismo do {@code kof.workflow}/{@code kof.supervisor}
 * (DD-OTP-01 opção A).
 *
 * <p>Sem gate de target: o host é composição pura (laço fixpoint, String
 * throws, function-values), nenhuma fronteira de runtime — os gaps honestos
 * ({@code ORM001}/{@code DB001} no estado kof.db da fatia 3.4, {@code
 * CRON001} no reconcile 3.3) sobem no CORPO do provider/reconcile do
 * usuário, nunca na camada de composição (lição do workflow).
 */
final class CompilerMakealive {

    private CompilerMakealive() {}

    /** Nomes públicos do host — qualquer um declarado pelo usuário sem o
     *  host é colisão: não injeta (o import vira PKG006 real — nome
     *  colidindo é sinal, não silêncio; regra 8). */
    private static final List<String> HOST_MARKS = List.of(
            "Resource", "StateEntry", "State", "Spec", "Infrastructure",
            "Provider", "Plan", "Report",
            "plan", "apply", "destroy", "kofMkTemNome",
            "KofMkState", "mkSaveState", "mkLoadState", "mkMaxGen",
            "ReconJob", "reconcile");

    private static void mergeSlice(CompilerDriver driver,
                                       List<AstNode> decls,
                                       DiagnosticCollector diagnostics,
                                       String resource) {
        try (var in = CompilerDriver.class.getResourceAsStream(resource)) {
            if (in == null) {
                diagnostics.error("", 0, 0, 0,
                        "makealive host slice resource " + resource + " missing", "PKG003");
                return;
            }
            String sliceSource = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            String sliceName = resource.substring(resource.lastIndexOf('/') + 1);
            DiagnosticCollector silent = new DiagnosticCollector();
            Lexer lexer = new Lexer(sliceSource, sliceName, silent);
            Parser parser = new Parser(lexer.tokenize(), silent, sliceName);
            CompilationUnitNode sliceUnit = parser.parse();
            if (silent.hasErrors() || sliceUnit == null) {
                for (Diagnostic d : silent.getDiagnostics()) diagnostics.report(d);
                diagnostics.error("", 0, 0, 0, "makealive host slice did not parse: " + sliceName, "PKG003");
                return;
            }
            for (AstNode d : sliceUnit.declarations()) {
                driver.declarationPackages.put(d, "");
                decls.add(d);
            }
        } catch (IOException e) {
            diagnostics.error("", 0, 0, 0,
                    "makealive host slice read failed: " + resource, "PKG003");
        }
    }

    static CompilationUnitNode injectHostIfNeeded(CompilerDriver driver,
                                                  CompilationUnitNode unit,
                                                  DiagnosticCollector diagnostics) {
        boolean wantsHost = false;
        for (String imp : unit.imports()) {
            String base = imp.endsWith(".*") ? imp.substring(0, imp.length() - 2) : imp;
            if ("kof.makealive".equals(base)) { wantsHost = true; break; }
        }
        if (!wantsHost) return unit;
        boolean collision = unit.declarations().stream().anyMatch(d ->
                (d instanceof TypeDeclarationNode t && HOST_MARKS.contains(t.name()))
                        || (d instanceof FunctionDeclarationNode f && HOST_MARKS.contains(f.name())));
        if (collision) return unit;
        try (var in = CompilerDriver.class.getResourceAsStream("/dev/kof/makealive-host.kf")) {
            if (in == null) {
                diagnostics.error("", 0, 0, 0,
                        "makealive host resource /dev/kof/makealive-host.kf missing", "PKG003");
                return null;
            }
            String hostSource = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            DiagnosticCollector silent = new DiagnosticCollector();
            Lexer lexer = new Lexer(hostSource, "makealive-host.kf", silent);
            Parser parser = new Parser(lexer.tokenize(), silent, "makealive-host.kf");
            CompilationUnitNode hostUnit = parser.parse();
            if (silent.hasErrors() || hostUnit == null) {
                for (Diagnostic d : silent.getDiagnostics()) diagnostics.report(d);
                diagnostics.error("", 0, 0, 0, "makealive host did not parse", "PKG003");
                return null;
            }
            List<String> imports = new ArrayList<>();
            for (String imp : unit.imports()) {
                String base = imp.endsWith(".*") ? imp.substring(0, imp.length() - 2) : imp;
                if ("kof.makealive".equals(base)) continue; // virtual — resolvido aqui
                if (!imports.contains(imp)) imports.add(imp);
            }
            List<AstNode> decls = new ArrayList<>(unit.declarations());
            for (AstNode d : hostUnit.declarations()) {
                driver.declarationPackages.put(d, "");
                decls.add(d);
            }
            // fatia de ESTADO mk1 (D-MAKEALIVE Q3, espelhando o checkpoint do
            // workflow 2.1.3): o gate ORM001 e estatico — no NATIVE entra o
            // STUB (throw ORM001 em runtime, R6), nunca a delegacao (que
            // derrubaria o host INTEIRO na recusa).
            if (!driver.target.isNative()) {
                mergeSlice(driver, decls, diagnostics, "/dev/kof/makealive-db-host.kf");
            } else {
                mergeSlice(driver, decls, diagnostics, "/dev/kof/makealive-db-host.native.kf");
            }
            // fatia RECONCILE 3.3: todos os alvos — scheduler.every é real
            // também no Native (SCHED001 fechado 05/09; o gate CRON001 é do
            // at/cron, que reconcile NUNCA toca). Sem stub.
            mergeSlice(driver, decls, diagnostics, "/dev/kof/makealive-recon-host.kf");
            return new CompilationUnitNode(unit.position(), unit.packageName(), imports, decls);
        } catch (IOException e) {
            diagnostics.error("", 0, 0, 0,
                    "makealive host could not be loaded: " + e.getMessage(), "PKG003");
            return null;
        }
    }
}
