package dev.kof.cli;

import java.util.regex.Pattern;

/**
 * D-ARTIFACT-TRUST (c) — a POLITICA de verificacao do `kof deps resolve`, pura: dado o tipo do
 * pacote, a identidade ESPERADA e o desfecho do verificador, ALLOW / WARN / BLOCK.
 *
 * <p>Contrato (DECISIONS.md §D-ARTIFACT-TRUST item 4): pacote OFICIAL sem evidencia valida =
 * BLOCK (HARD); pacote COMUNITARIO = aviso honesto (R6, nunca silencio). A identidade esperada
 * vem do PEDIDO do usuario e da politica — jamais do que a propria release declara: quem forja
 * o pacote forja tambem a tag, o SHA256SUMS e o texto da release.</p>
 *
 * <p>Codigos: {@code REG005} evidencia ausente · {@code REG006} evidencia invalida (falha
 * criptografica ou identidade/digest/ref/commit que nao batem) · {@code REG007} sem veredito
 * (verificador indisponivel — nunca vira "verificado") · {@code REG008} commit da tag nao
 * resolvido (nao da para amarrar a atestacao ao codigo que a tag nomeia).</p>
 *
 * <p>Perguntas ABERTAS para a mantenedora (regra 6 — este codigo segue a leitura LITERAL do
 * contrato e nao decide por ela): (1) o que torna um pacote "oficial" (hoje: owner na lista de
 * {@link TrustGate}); (2) comunitario com evidencia PRESENTE-mas-invalida (adulteracao) so avisa
 * (leitura literal "comunitario = warning") ou tambem bloqueia; (3) o workflow confiavel
 * ({@link TrustGate#RELEASE_WORKFLOW}) e o nome do asset de evidencia.</p>
 */
final class TrustPolicy {

    private TrustPolicy() {}

    static final String MISSING = "REG005";
    static final String INVALID = "REG006";
    static final String UNVERIFIABLE = "REG007";
    static final String UNPINNED = "REG008";

    private static final Pattern COMMIT = Pattern.compile("[0-9a-fA-F]{40}");

    enum PackageClass { OFFICIAL, COMMUNITY }

    enum Action { ALLOW, WARN, BLOCK }

    record Decision(Action action, String code, String message) {
        static Decision allow() { return new Decision(Action.ALLOW, null, "provenance verified"); }
    }

    /**
     * Identidade esperada. {@code signerRepository}/{@code signerWorkflowPath} null = "nao ha
     * workflow confiavel conhecido" (comunitario); {@code sourceSha} null = commit da tag nao resolvido.
     */
    record Expected(String repository, String signerRepository, String signerWorkflowPath,
                    String sourceRef, String sourceSha, String predicateType) {}

    static Decision decide(PackageClass cls, Expected exp, String actualSha256, boolean evidencePresent,
                           ProvenanceVerifier.Outcome outcome) {
        Action failure = cls == PackageClass.OFFICIAL ? Action.BLOCK : Action.WARN;
        if (!evidencePresent) {
            return new Decision(failure, MISSING, "no provenance evidence published for " + exp.repository()
                    + " (" + exp.sourceRef() + ") — the package is integrity-checked only, not build-attested");
        }
        if (outcome == null) {   // defensivo: sem desfecho = sem veredito, nunca "ok"
            outcome = new ProvenanceVerifier.Unavailable("the verifier produced no outcome");
        }
        if (outcome instanceof ProvenanceVerifier.Unavailable u) {
            return new Decision(failure, UNVERIFIABLE, "provenance could not be verified: " + u.reason());
        }
        if (outcome instanceof ProvenanceVerifier.Rejected r) {
            return new Decision(failure, INVALID, "provenance verification failed: " + r.reason());
        }
        ProvenanceEvidence ev = ((ProvenanceVerifier.Verified) outcome).evidence();
        String mismatch = mismatch(exp, actualSha256, ev);
        if (mismatch != null) {
            return new Decision(failure, INVALID, "provenance does not match the requested package — " + mismatch);
        }
        if (exp.sourceSha() == null) {
            return new Decision(failure, UNPINNED, "the release tag " + exp.sourceRef()
                    + " could not be resolved to a commit, so the attested source cannot be pinned to it");
        }
        return Decision.allow();
    }

    /** Primeira divergencia entre a evidencia (ja verificada) e o esperado; null = tudo bate. */
    private static String mismatch(Expected exp, String actualSha256, ProvenanceEvidence ev) {
        boolean subject = false;
        if (ev.subjectSha256() != null && actualSha256 != null) {
            for (String s : ev.subjectSha256()) {
                if (actualSha256.equalsIgnoreCase(s)) { subject = true; break; }
            }
        }
        if (!subject) {
            return "digest: the attestation does not cover the downloaded artifact (sha256 " + actualSha256 + ")";
        }
        if (!sameIgnoreCase(exp.repository(), ev.repository())) {
            return "repository: attested " + ev.repository() + ", expected " + exp.repository();
        }
        if (exp.signerRepository() != null && !sameIgnoreCase(exp.signerRepository(), ev.signerRepository())) {
            return "workflow repository: signed by " + ev.signerRepository() + ", expected " + exp.signerRepository();
        }
        if (exp.signerWorkflowPath() != null && !exp.signerWorkflowPath().equals(ev.signerWorkflowPath())) {
            return "workflow: signed by " + ev.signerWorkflowPath() + ", expected " + exp.signerWorkflowPath();
        }
        if (!exp.sourceRef().equals(ev.sourceRef())) {
            return "ref: built from " + ev.sourceRef() + ", expected " + exp.sourceRef();
        }
        if (!exp.predicateType().equals(ev.predicateType())) {
            return "predicate: " + ev.predicateType() + " is not build provenance (" + exp.predicateType() + ")";
        }
        if (ev.sourceSha() == null || !COMMIT.matcher(ev.sourceSha()).matches()) {
            return "commit: the attested source sha is not a 40-hex commit";
        }
        if (exp.sourceSha() != null && !exp.sourceSha().equalsIgnoreCase(ev.sourceSha())) {
            return "commit: attested " + ev.sourceSha() + ", but the tag " + exp.sourceRef() + " points to "
                    + exp.sourceSha() + " (tag moved after the build?)";
        }
        return null;
    }

    private static boolean sameIgnoreCase(String a, String b) {
        return a != null && a.equalsIgnoreCase(b);
    }
}
