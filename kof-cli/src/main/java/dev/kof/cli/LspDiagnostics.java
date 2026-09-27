package dev.kof.cli;

import dev.kof.compiler.CompilationResult;
import dev.kof.compiler.Diagnostic;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Traducao Diagnosticas Kof -> LSP publishDiagnostics (#636, extraida de
 * LspServer na unidade do mirror de projeto — LspServer e divida travada no
 * baseline 584 e nao cresce; a responsabilidade "formato LSP de diagnostico"
 * mora aqui).
 */
final class LspDiagnostics {

    private LspDiagnostics() {}

    /** Todas as diagnostiques (modo arquivo-unico — comportamento historico). */
    static List<Object> all(CompilationResult result) {
        return map(result.diagnostics().getDiagnostics(), null);
    }

    /**
     * So as diagnostiques do arquivo-alvo (#636): no modo projeto o build ve
     * os irmaos espelhados tambem; linhas de um irmao NAO podem virar
     * diagnostico do buffer aberto (posicao de outro arquivo e lixo LSP).
     */
    static List<Object> forFile(CompilationResult result, Path target) {
        return map(result.diagnostics().getDiagnostics(), target);
    }

    private static List<Object> map(List<Diagnostic> diags, Path target) {
        List<Object> out = new ArrayList<>();
        for (Diagnostic d : diags) {
            if (target != null && !belongs(d, target)) continue;
            Map<String, Object> diag = new LinkedHashMap<>();
            Map<String, Object> range = new LinkedHashMap<>();
            Map<String, Object> start = new LinkedHashMap<>();
            Map<String, Object> end = new LinkedHashMap<>();
            start.put("line", Math.max(0, d.line() - 1));
            start.put("character", Math.max(0, d.column() - 1));
            end.put("line", Math.max(0, d.line() - 1));
            end.put("character", Math.max(0, d.column() - 1 + Math.max(0, d.length())));
            range.put("start", start);
            range.put("end", end);
            diag.put("range", range);
            diag.put("severity", d.severity() == Diagnostic.Severity.ERROR ? 1 : 2);
            diag.put("source", "kof");
            diag.put("code", d.code());
            diag.put("message", d.message() + (d.code() != null && !d.code().isEmpty()
                    ? " [" + d.code() + "]" : ""));
            out.add(diag);
        }
        return out;
    }

    private static boolean belongs(Diagnostic d, Path target) {
        String f = d.file();
        if (f == null || f.isBlank()) return true; // diagnostico sem arquivo = global, honesto manter
        Path t = target.toAbsolutePath().normalize();
        try {
            Path p = Path.of(f);
            if (p.isAbsolute()) return p.normalize().equals(t);
            // a pipeline do compiler rotula diagnosticos pelo FILE NAME
            // (CompilerPipeline -> new Lexer(code, src.getFileName())) — irmao
            // de outro diretorio tem outro nome; colisao de homonimos e o mesmo
            // limite que o `kof check` ja tem, nao um lixo novo do LSP.
            return t.getFileName().toString().equals(p.getFileName().toString());
        } catch (Exception unparsable) {
            return false;
        }
    }
}
