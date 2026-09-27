package dev.kof.cli;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * D-ARTIFACT-TRUST (c) — a COLA entre o `kof deps resolve` e a {@link TrustPolicy}: hash do
 * tar.gz REAL baixado, identidade esperada montada do PEDIDO (owner/repo@versao), verificador
 * injetado, e o desfecho — erro (BLOCK, nada e instalado) ou aviso (WARN, sempre visivel: R6).
 *
 * <p><b>Rollout honesto.</b> Enquanto o pipeline de release (fila (b)) nao publica atestacao,
 * exigir evidencia quebraria todo consumo de pacote oficial. Por isso o modo padrao e OBSERVE:
 * nunca bloqueia, mas GRITA "would block" para o que bloquearia. {@link #ENFORCE_BY_DEFAULT} vira
 * {@code true} quando (b) pousar e o E2E real passar. O ambiente so consegue APERTAR
 * ({@code KOF_DEPS_TRUST=enforce}, {@code KOF_DEPS_OFFICIAL_OWNERS=a,b}) — nao ha chave que afrouxe.</p>
 */
final class TrustGate {

    private TrustGate() {}

    /** Modo padrao: false = OBSERVE (avisa o que bloquearia); true = ENFORCE (o contrato de 1.0). */
    static final boolean ENFORCE_BY_DEFAULT = false;

    /** Workflow confiavel para atestar release de pacote oficial (a confirmar com a lane CI). */
    static final String RELEASE_WORKFLOW = ".github/workflows/release.yml";

    static final String PROVENANCE_PREDICATE = "https://slsa.dev/provenance/v1";

    private static final Set<String> OFFICIAL_OWNERS = Set.of("koflang");

    /** Owner oficial: comparacao EXATA, sem case (owners do GitHub nao diferenciam), sem parecidos. */
    static boolean isOfficial(String owner) {
        if (owner == null || owner.isEmpty()) return false;
        String o = owner.toLowerCase(Locale.ROOT);
        return OFFICIAL_OWNERS.contains(o) || extraOfficialOwners().contains(o);
    }

    /** So ADICIONA (aperta): mais donos passam a exigir evidencia. */
    private static Set<String> extraOfficialOwners() {
        Set<String> out = new HashSet<>();
        String env = System.getenv("KOF_DEPS_OFFICIAL_OWNERS");
        if (env == null) return out;
        for (String s : env.split(",")) {
            String t = s.trim().toLowerCase(Locale.ROOT);
            if (t.matches("[a-z0-9._-]+")) out.add(t);
        }
        return out;
    }

    static boolean enforcing() {
        return ENFORCE_BY_DEFAULT || "enforce".equalsIgnoreCase(System.getenv("KOF_DEPS_TRUST"));
    }

    /** O verificador de producao. Ainda nenhum: {@link ProvenanceVerifier#NONE} nunca aprova. */
    static ProvenanceVerifier verifier() {
        return ProvenanceVerifier.NONE;
    }

    /**
     * Confere o pacote baixado ANTES de extrair/instalar qualquer coisa.
     *
     * @param evidenceBundle bundle de atestacao baixado da release, ou null se a release nao publica
     * @param tagCommit      commit para o qual a tag da release aponta, ou null se nao resolvido
     * @throws IOException {@code REG005..REG008} quando a decisao e BLOCK e o modo e ENFORCE
     */
    static void check(String owner, String repo, String tag, Path tarball, Path evidenceBundle,
                      ProvenanceVerifier verifier, String tagCommit, boolean enforcing) throws IOException {
        boolean official = isOfficial(owner);
        String sha = CmdDeploy.sha256Hex(tarball);
        String repository = owner + "/" + repo;
        TrustPolicy.Expected exp = new TrustPolicy.Expected(repository,
                official ? repository : null,
                official ? RELEASE_WORKFLOW : null,
                "refs/tags/" + tag, tagCommit, PROVENANCE_PREDICATE);
        ProvenanceVerifier.Outcome outcome = null;
        if (evidenceBundle != null) {
            try {
                outcome = verifier.verify(tarball, sha, evidenceBundle);
            } catch (IOException | RuntimeException e) {
                outcome = new ProvenanceVerifier.Unavailable("the verifier failed: " + e.getMessage());
            }
        }
        TrustPolicy.Decision d = TrustPolicy.decide(
                official ? TrustPolicy.PackageClass.OFFICIAL : TrustPolicy.PackageClass.COMMUNITY,
                exp, sha, evidenceBundle != null, outcome);
        switch (d.action()) {
            case ALLOW -> { }
            case WARN -> System.err.println("deps: warning " + d.code() + ": " + repository + "@" + tag
                    + " — " + d.message());
            case BLOCK -> {
                if (enforcing) {
                    throw new IOException(d.code() + ": refusing to install " + repository + "@" + tag
                            + " — " + d.message());
                }
                System.err.println("deps: warning " + d.code() + ": would block under enforcement — "
                        + repository + "@" + tag + " — " + d.message());
            }
        }
    }
}
