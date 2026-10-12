package dev.kof.compiler;

import dev.kof.compiler.parser.Lexer;
import dev.kof.compiler.parser.Parser;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Pacote virtual {@code kof.orm.history} (entity-history, D-ENTITY-HISTORY).
 * Os records {@code Revision}/{@code FieldChange} sao escritos EM KOF
 * ({@code dev/kof/orm-history.kf} no resource) e injetados FLAT no
 * {@code import kof.orm.history} EXPLICITO — o mecanismo de
 * {@code kof.pagination}/{@code kof.supervisor}/{@code kof.workflow}.
 * Sao os tipos de retorno travados da superficie de consulta do plano
 * (§9.2); o runtime de consulta JVM ({@code kof_orm_history}) e a
 * resolucao entity-static sao fatias seguintes.
 */
final class CompilerOrmHistory {

    static final String HOST_IMPORT = "kof.orm.history";

    private CompilerOrmHistory() {}

    static CompilationUnitNode injectHostIfNeeded(CompilerDriver driver,
                                                  CompilationUnitNode unit,
                                                  DiagnosticCollector diagnostics) {
        boolean wantsHost = false;
        for (String imp : unit.imports()) {
            String base = imp.endsWith(".*") ? imp.substring(0, imp.length() - 2) : imp;
            if (HOST_IMPORT.equals(base)) { wantsHost = true; break; }
        }
        if (!wantsHost) return unit;
        // usuario definiu o proprio Revision/FieldChange: nao injeta (nome
        // colidindo e sinal, nao silencio — criterio dos pacotes virtuais).
        boolean collision = unit.declarations().stream().anyMatch(d ->
                d instanceof TypeDeclarationNode t
                        && ("Revision".equals(t.name()) || "FieldChange".equals(t.name())));
        if (collision) return unit;
        CompilationUnitNode hostUnit = parseHostResource(diagnostics);
        if (hostUnit == null) return null;
        List<String> imports = new ArrayList<>();
        for (String imp : unit.imports()) {
            String base = imp.endsWith(".*") ? imp.substring(0, imp.length() - 2) : imp;
            if (HOST_IMPORT.equals(base)) continue; // virtual — resolvido aqui
            if (!imports.contains(imp)) imports.add(imp);
        }
        List<AstNode> decls = new ArrayList<>(unit.declarations());
        for (AstNode d : hostUnit.declarations()) {
            driver.declarationPackages.put(d, "");
            decls.add(d);
        }
        return new CompilationUnitNode(unit.position(), unit.packageName(), imports, decls);
    }

    private static CompilationUnitNode parseHostResource(DiagnosticCollector diagnostics) {
        String resource = "/dev/kof/orm-history.kf";
        try (var in = CompilerDriver.class.getResourceAsStream(resource)) {
            if (in == null) {
                diagnostics.error("", 0, 0, 0,
                        "orm-history host resource " + resource + " missing", "PKG003");
                return null;
            }
            String hostSource = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            DiagnosticCollector silent = new DiagnosticCollector();
            Lexer lexer = new Lexer(hostSource, "orm-history.kf", silent);
            Parser parser = new Parser(lexer.tokenize(), silent, "orm-history.kf");
            CompilationUnitNode hostUnit = parser.parse();
            if (silent.hasErrors() || hostUnit == null) {
                for (Diagnostic d : silent.getDiagnostics()) diagnostics.report(d);
                diagnostics.error("", 0, 0, 0, "orm-history host did not parse", "PKG003");
                return null;
            }
            return hostUnit;
        } catch (IOException e) {
            diagnostics.error("", 0, 0, 0,
                    "orm-history host could not be loaded: " + e.getMessage(), "PKG003");
            return null;
        }
    }
}
