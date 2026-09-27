package dev.kof.cli;

import java.io.IOException;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * D-ARTIFACT-TRUST (c) — para qual COMMIT a tag da release aponta hoje. Amarra a atestacao
 * ("construido do commit X") ao codigo que a tag nomeia: uma tag movida depois do build faz
 * o commit atestado divergir. Qualquer falha devolve null — "nao resolvido" e um estado
 * honesto que a politica trata fail-closed para oficial (REG008), nunca "confere".
 */
final class RegistryTags {

    private RegistryTags() {}

    private static final Pattern COMMIT = Pattern.compile("[0-9a-f]{40}");

    static String commitOf(String owner, String repo, String tag) {
        try {
            String base = DepsRegistry.apiBase() + "/repos/" + owner + "/" + repo + "/git/";
            Map<?, ?> obj = objectOf(DepsRegistry.getJson(base + "ref/tags/" + tag));
            if (obj != null && "tag".equals(obj.get("type")) && obj.get("sha") instanceof String tagSha) {
                obj = objectOf(DepsRegistry.getJson(base + "tags/" + tagSha));   // tag anotada -> desreferencia
            }
            if (obj != null && "commit".equals(obj.get("type")) && obj.get("sha") instanceof String sha
                    && COMMIT.matcher(sha).matches()) {
                return sha;
            }
        } catch (IOException | RuntimeException e) {
            // cai no return null: nao resolvido
        }
        return null;
    }

    private static Map<?, ?> objectOf(HttpResponse<String> res) {
        if (res.statusCode() / 100 != 2) return null;
        Object root = Json.parse(res.body());
        return root instanceof Map<?, ?> m && m.get("object") instanceof Map<?, ?> o ? o : null;
    }
}
