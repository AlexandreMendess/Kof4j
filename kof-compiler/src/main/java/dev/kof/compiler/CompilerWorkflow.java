package dev.kof.compiler;

import dev.kof.compiler.parser.Lexer;
import dev.kof.compiler.parser.Parser;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Pacote virtual {@code kof.workflow} (tracker 2.1, plano
 * {@code docs/development/workflow-plan.md} §5 2.1.2). O host é escrito EM KOF
 * ({@code dev/kof/workflow-host.kf} no resource) e injetado FLAT no
 * {@code import kof.workflow} EXPLÍCITO — exatamente o mecanismo do
 * {@code kof.supervisor} (DD-OTP-01 opção A), portanto SEM o prefixo
 * {@code workflow.} do sketch §2: a superfície assinada Q1 é stdlib puro-Kof,
 * e em stdlib puro-Kof de host injetado o idioma real é o flat (supervisor é
 * plano: {@code supervisor("t")}, nunca {@code kof.supervisor.spawn}).
 *
 * <p>Sem gate de target: a execução MVP é sequencial (laço fixpoint em Kof
 * puro, sem threads/await/process), então o próprio host roda em JVM, ANDROID,
 * Script, JS e Native — honestidade {@code PROC001}/{@code CRON001}/{@code
 * ORM001} só aparece quando o CORPO do job chama essas primitivas, não na
 * camada de composição. Superfície: job/dag/after/run/Report (Q2 mínimo);
 * retry/checkpoint/deadLetter chegam no bundle 2.1.3.
 */
final class CompilerWorkflow {

    private CompilerWorkflow() {}

    static CompilationUnitNode injectHostIfNeeded(CompilerDriver driver,
                                                  CompilationUnitNode unit,
                                                  DiagnosticCollector diagnostics) {
        boolean wantsHost = false;
        for (String imp : unit.imports()) {
            String base = imp.endsWith(".*") ? imp.substring(0, imp.length() - 2) : imp;
            if ("kof.workflow".equals(base)) { wantsHost = true; break; }
        }
        if (!wantsHost) return unit;
        // usuário definiu o próprio host: não injeta (o import vira PKG006
        // real — nome colidindo é sinal, não silêncio).
        boolean collision = unit.declarations().stream()
                .anyMatch(d -> d instanceof TypeDeclarationNode t
                        && ("KofWfJob".equals(t.name()) || "KofWfDag".equals(t.name())
                                || "KofWfReport".equals(t.name()) || "KofWfCk".equals(t.name())));
        if (collision) return unit;
        try (var in = CompilerDriver.class.getResourceAsStream("/dev/kof/workflow-host.kf")) {
            if (in == null) {
                diagnostics.error("", 0, 0, 0,
                        "workflow host resource /dev/kof/workflow-host.kf missing", "PKG003");
                return null;
            }
            String hostSource = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            DiagnosticCollector silent = new DiagnosticCollector();
            Lexer lexer = new Lexer(hostSource, "workflow-host.kf", silent);
            Parser parser = new Parser(lexer.tokenize(), silent, "workflow-host.kf");
            CompilationUnitNode hostUnit = parser.parse();
            if (silent.hasErrors() || hostUnit == null) {
                for (Diagnostic d : silent.getDiagnostics()) diagnostics.report(d);
                diagnostics.error("", 0, 0, 0, "workflow host did not parse", "PKG003");
                return null;
            }
            List<String> imports = new ArrayList<>();
            for (String imp : unit.imports()) {
                String base = imp.endsWith(".*") ? imp.substring(0, imp.length() - 2) : imp;
                if ("kof.workflow".equals(base)) continue; // virtual — resolvido aqui
                if (!imports.contains(imp)) imports.add(imp);
            }
            List<AstNode> decls = new ArrayList<>(unit.declarations());
            for (AstNode d : hostUnit.declarations()) {
                driver.declarationPackages.put(d, "");
                decls.add(d);
            }
            // fatia schedule (bundle 2.1.3): separada do host principal
            // porque o gate CRON001 do scheduler.at é estático — no NATIVE
            // entra o STUB (throw CRON001 em runtime, R6), nunca a
            // delegação (que derrubaria o host INTEIRO na recusa).
            if (!driver.target.isNative()) {
                mergeHostSlice(driver, decls, diagnostics, "/dev/kof/workflow-sched-host.kf");
            } else {
                mergeHostSlice(driver, decls, diagnostics, "/dev/kof/workflow-sched-host.native.kf");
            }
            // fatia checkpoint (bundle 2.1.3): MESMO mecanismo — o gate
            // ORM001 do kof.orm é estático; não-Native injeta a fatia orm
            // (entity KofWfCk + hooks), Native injeta o stub ORM001.
            if (!driver.target.isNative()) {
                mergeHostSlice(driver, decls, diagnostics, "/dev/kof/workflow-ckpt-host.kf");
            } else {
                mergeHostSlice(driver, decls, diagnostics, "/dev/kof/workflow-ckpt-host.native.kf");
            }
            // fatia supervisão (bundle 2.1.3, plano §3: o workflow DELEGA o
            // restart ao kof.supervisor — nunca re-implementa). O
            // CompilerSupervisor roda ANTES no pipeline: se o usuário
            // importou kof.supervisor, o host já está nas decls (KofSupWrap é
            // a marca dele — Supervisor/supervisor() então NÃO são colisão,
            // são o próprio host) e só injeta a fatia. Sem o import, o host é
            // injetado flat aqui (mesmo mecanismo DD-OTP-01). Supervisor
            // PRÓPRIO do usuário sem o host, ou um runSupervised/KofWfSupStatus
            // dele = colisão — a peça correspondente não entra (regra 8:
            // jamais quebrar um programa que compila hoje).
            boolean hostSupJa = decls.stream().anyMatch(d -> d instanceof TypeDeclarationNode t
                    && "KofSupWrap".equals(t.name()));
            boolean colideHostSup = !hostSupJa && decls.stream().anyMatch(d ->
                    (d instanceof TypeDeclarationNode t
                            && ("Supervisor".equals(t.name()) || "KofWorker".equals(t.name())
                                    || "KofWorkerFactory".equals(t.name())))
                            || (d instanceof FunctionDeclarationNode f && "supervisor".equals(f.name())));
            boolean colideFace = decls.stream().anyMatch(d ->
                    (d instanceof TypeDeclarationNode t && "KofWfSupStatus".equals(t.name()))
                            || (d instanceof FunctionDeclarationNode f && "runSupervised".equals(f.name())));
            if (!hostSupJa && !colideHostSup) {
                mergeHostSlice(driver, decls, diagnostics, "/dev/kof/supervisor-host.kf");
            }
            if (!colideHostSup && !colideFace) {
                mergeHostSlice(driver, decls, diagnostics, "/dev/kof/workflow-sup-host.kf");
            }
            return new CompilationUnitNode(unit.position(), unit.packageName(), imports, decls);
        } catch (IOException e) {
            diagnostics.error("", 0, 0, 0,
                    "workflow host could not be loaded: " + e.getMessage(), "PKG003");
            return null;
        }
    }

    private static void mergeHostSlice(CompilerDriver driver,
                                       List<AstNode> decls,
                                       DiagnosticCollector diagnostics,
                                       String resource) {
        try (var in = CompilerDriver.class.getResourceAsStream(resource)) {
            if (in == null) {
                diagnostics.error("", 0, 0, 0,
                        "workflow host slice resource " + resource + " missing", "PKG003");
                return;
            }
            String schedSource = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            String sliceName = resource.substring(resource.lastIndexOf('/') + 1);
            DiagnosticCollector silent = new DiagnosticCollector();
            Lexer lexer = new Lexer(schedSource, sliceName, silent);
            Parser parser = new Parser(lexer.tokenize(), silent, sliceName);
            CompilationUnitNode schedUnit = parser.parse();
            if (silent.hasErrors() || schedUnit == null) {
                for (Diagnostic d : silent.getDiagnostics()) diagnostics.report(d);
                diagnostics.error("", 0, 0, 0, "workflow host slice did not parse", "PKG003");
                return;
            }
            for (AstNode d : schedUnit.declarations()) {
                driver.declarationPackages.put(d, "");
                decls.add(d);
            }
        } catch (IOException e) {
            diagnostics.error("", 0, 0, 0,
                    "workflow host slice could not be loaded: " + e.getMessage(), "PKG003");
        }
    }
}
