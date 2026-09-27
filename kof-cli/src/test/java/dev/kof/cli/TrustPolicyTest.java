package dev.kof.cli;

import dev.kof.cli.ProvenanceVerifier.Rejected;
import dev.kof.cli.ProvenanceVerifier.Unavailable;
import dev.kof.cli.ProvenanceVerifier.Verified;
import dev.kof.cli.TrustPolicy.Action;
import dev.kof.cli.TrustPolicy.Decision;
import dev.kof.cli.TrustPolicy.Expected;
import dev.kof.cli.TrustPolicy.PackageClass;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.UnaryOperator;

import static org.junit.jupiter.api.Assertions.*;

/**
 * D-ARTIFACT-TRUST (c) — a POLITICA de verificacao do `kof deps resolve`, pura (sem rede,
 * sem arquivo): dado o pacote (oficial/comunitario), a identidade ESPERADA e o desfecho do
 * verificador, ALLOW / WARN / BLOCK. Casos adversariais escritos RED-first: cada um e uma
 * forma de um atacante (ou de um engano) fazer evidencia VALIDA parecer boa para o
 * artefato/repo/workflow/ref errado. Negativos usam sujeitos genuinamente DISTINTOS
 * (licao do lab de attestation 20/09: digests iguais tornam o negativo falso-verde).
 */
class TrustPolicyTest {

    static final String SHA = "a".repeat(64);
    static final String OTHER_SHA = "b".repeat(64);
    static final String COMMIT = "1".repeat(40);
    static final String OTHER_COMMIT = "2".repeat(40);
    static final String SLSA = "https://slsa.dev/provenance/v1";
    static final String WORKFLOW = ".github/workflows/release.yml";

    static Expected official() {
        return new Expected("KofLang/hello", "KofLang/hello", WORKFLOW,
                "refs/tags/hello-1.2.3", COMMIT, SLSA);
    }

    /** Comunitario: o workflow que assina nao e conhecido — so repo/digest/ref/predicado valem. */
    static Expected community() {
        return new Expected("acme/hello", null, null, "refs/tags/hello-1.2.3", COMMIT, SLSA);
    }

    static ProvenanceEvidence good() {
        return new ProvenanceEvidence(List.of(SHA), "KofLang/hello", "KofLang/hello", WORKFLOW,
                "refs/tags/hello-1.2.3", COMMIT, SLSA);
    }

    static ProvenanceEvidence goodCommunity() {
        return new ProvenanceEvidence(List.of(SHA), "acme/hello", "acme/hello",
                ".github/workflows/anything.yml", "refs/tags/hello-1.2.3", COMMIT, SLSA);
    }

    static Decision official(UnaryOperator<ProvenanceEvidence> tamper) {
        return TrustPolicy.decide(PackageClass.OFFICIAL, official(), SHA, true,
                new Verified(tamper.apply(good())));
    }

    /** Copia com override: campo null = mantem o original. */
    static ProvenanceEvidence with(ProvenanceEvidence e, List<String> subjects, String repo,
                                   String signerRepo, String path, String ref, String sha,
                                   String predicate) {
        return new ProvenanceEvidence(subjects != null ? subjects : e.subjectSha256(),
                repo != null ? repo : e.repository(),
                signerRepo != null ? signerRepo : e.signerRepository(),
                path != null ? path : e.signerWorkflowPath(),
                ref != null ? ref : e.sourceRef(),
                sha != null ? sha : e.sourceSha(),
                predicate != null ? predicate : e.predicateType());
    }

    private static void assertBlock(Decision d, String code, String why) {
        assertEquals(Action.BLOCK, d.action(), why + " => BLOCK, veio " + d);
        assertEquals(code, d.code(), why + " => codigo " + code + ", veio " + d);
        assertFalse(d.message().isBlank(), "toda decisao explica o motivo (R6)");
    }

    // ---- caminho feliz ----

    @Test
    void officialWithFullyMatchingEvidenceIsAllowed() {
        Decision d = official(e -> e);
        assertEquals(Action.ALLOW, d.action(), d.toString());
    }

    @Test
    void multiSubjectBundleContainingTheDigestIsAllowed() {
        assertEquals(Action.ALLOW, official(e -> with(e, List.of(OTHER_SHA, SHA), null, null, null,
                null, null, null)).action());
    }

    @Test
    void digestComparisonIsCaseInsensitiveHex() {
        assertEquals(Action.ALLOW, TrustPolicy.decide(PackageClass.OFFICIAL, official(),
                SHA.toUpperCase(), true, new Verified(good())).action());
    }

    // ---- evidencia AUSENTE ----

    @Test
    void officialWithoutEvidenceIsHardBlockedReg005() {
        assertBlock(TrustPolicy.decide(PackageClass.OFFICIAL, official(), SHA, false, null),
                "REG005", "oficial sem evidencia");
    }

    @Test
    void communityWithoutEvidenceIsAnHonestWarningNeverSilent() {
        Decision d = TrustPolicy.decide(PackageClass.COMMUNITY, community(), SHA, false, null);
        assertEquals(Action.WARN, d.action(), d.toString());
        assertEquals("REG005", d.code());
        assertFalse(d.message().isBlank(), "warning sem texto seria silencio (R6)");
    }

    // ---- verificador sem veredito / com veredito negativo ----

    @Test
    void unavailableVerifierFailsClosedForOfficialReg007() {
        assertBlock(TrustPolicy.decide(PackageClass.OFFICIAL, official(), SHA, true,
                new Unavailable("gh not installed")), "REG007", "verificador indisponivel");
    }

    @Test
    void unavailableVerifierNeverPassesAsVerifiedForCommunityEither() {
        Decision d = TrustPolicy.decide(PackageClass.COMMUNITY, community(), SHA, true,
                new Unavailable("offline"));
        assertEquals(Action.WARN, d.action());
        assertEquals("REG007", d.code());
    }

    @Test
    void rejectedCryptographicVerificationBlocksOfficialReg006() {
        assertBlock(TrustPolicy.decide(PackageClass.OFFICIAL, official(), SHA, true,
                new Rejected("signature invalid")), "REG006", "assinatura invalida");
    }

    // ---- adversariais: evidencia valida para o artefato/identidade ERRADOS ----

    @Test
    void digestDivergentIsBlocked() {
        // pacote adulterado apos a atestacao: a evidencia e do digest ORIGINAL (SHA); o baixado difere
        Decision d = TrustPolicy.decide(PackageClass.OFFICIAL, official(), OTHER_SHA, true,
                new Verified(good()));
        assertBlock(d, "REG006", "digest divergente");
        assertTrue(d.message().contains("digest"), d.message());
    }

    @Test
    void evidenceWithNoSubjectsIsBlocked() {
        assertBlock(official(e -> with(e, List.of(), null, null, null, null, null, null)),
                "REG006", "evidencia sem sujeito");
    }

    @Test
    void subjectPrefixOrSuffixIsNotADigestMatch() {
        assertBlock(official(e -> with(e, List.of(SHA.substring(0, 63)), null, null, null, null, null, null)),
                "REG006", "digest truncado");
        assertBlock(official(e -> with(e, List.of(SHA + "0"), null, null, null, null, null, null)),
                "REG006", "digest com sufixo");
    }

    @Test
    void wrongRepositoryIsBlocked() {
        assertBlock(official(e -> with(e, null, "evil/hello", null, null, null, null, null)),
                "REG006", "repositorio-fonte errado (fork)");
        assertBlock(official(e -> with(e, null, "KofLang/other", null, null, null, null, null)),
                "REG006", "outro repo da mesma org (replay de evidencia de outro pacote)");
    }

    @Test
    void repositoryComparisonIsCaseInsensitiveAsOnGitHub() {
        assertEquals(Action.ALLOW, official(e -> with(e, null, "koflang/HELLO", "KOFLANG/hello",
                null, null, null, null)).action());
    }

    @Test
    void wrongWorkflowIsBlocked() {
        assertBlock(official(e -> with(e, null, null, null, ".github/workflows/evil.yml", null, null, null)),
                "REG006", "workflow diferente do confiavel");
        assertBlock(official(e -> with(e, null, null, null, ".github/workflows/release.yml.evil", null, null, null)),
                "REG006", "sufixo colado ao nome do workflow");
        assertBlock(official(e -> with(e, null, null, null, ".github/workflows/Release.yml", null, null, null)),
                "REG006", "caminho de workflow e case-sensitive");
        assertBlock(official(e -> with(e, null, null, null, ".github/workflows/../workflows/release.yml", null, null, null)),
                "REG006", "caminho nao canonico nao vale");
    }

    @Test
    void workflowOfAnotherRepositoryIsBlocked() {
        // o MESMO nome de arquivo, mas hospedado num repo do atacante
        assertBlock(official(e -> with(e, null, null, "evil/hello", null, null, null, null)),
                "REG006", "signer em outro repositorio");
    }

    @Test
    void wrongRefIsBlocked() {
        assertBlock(official(e -> with(e, null, null, null, null, "refs/heads/main", null, null)),
                "REG006", "build de branch, nao de tag de release");
        assertBlock(official(e -> with(e, null, null, null, null, "refs/tags/hello-1.0.0", null, null)),
                "REG006", "replay de atestacao de OUTRA versao");
        assertBlock(official(e -> with(e, null, null, null, null, "refs/tags/hello-1.2.3-evil", null, null)),
                "REG006", "tag com sufixo");
    }

    @Test
    void wrongSourceShaIsBlocked() {
        assertBlock(official(e -> with(e, null, null, null, null, null, OTHER_COMMIT, null)),
                "REG006", "tag movida apos o build: commit atestado != commit da tag");
    }

    @Test
    void officialWithUnresolvedTagCommitFailsClosedReg008() {
        Expected noCommit = new Expected("KofLang/hello", "KofLang/hello", WORKFLOW,
                "refs/tags/hello-1.2.3", null, SLSA);
        assertBlock(TrustPolicy.decide(PackageClass.OFFICIAL, noCommit, SHA, true, new Verified(good())),
                "REG008", "commit da tag nao resolvido nao pode virar 'confere'");
    }

    @Test
    void malformedSourceShaIsBlockedEvenWhenItEqualsTheExpectedOne() {
        // isola a checagem de FORMATO: esperado == atestado (os dois malformados), logo a
        // comparacao de commit sozinha aprovaria. Sem ela, "deadbeef" passaria como commit.
        Expected malformedBoth = new Expected("KofLang/hello", "KofLang/hello", WORKFLOW,
                "refs/tags/hello-1.2.3", "deadbeef", SLSA);
        ProvenanceEvidence ev = with(good(), null, null, null, null, null, "deadbeef", null);
        assertBlock(TrustPolicy.decide(PackageClass.OFFICIAL, malformedBoth, SHA, true, new Verified(ev)),
                "REG006", "sha de origem nao e 40 hex");
        assertBlock(official(e -> with(e, null, null, null, null, null, "deadbeef", null)),
                "REG006", "sha malformado que ainda por cima diverge");
    }

    @Test
    void wrongPredicateTypeIsBlocked() {
        assertBlock(official(e -> with(e, null, null, null, null, null, null, "https://example.com/other/v1")),
                "REG006", "predicado que nao e proveniencia de build");
    }

    // ---- comunitario: warning honesto (contrato), nunca silencio, nunca "verificado" falso ----

    @Test
    void communityWithValidEvidenceIsAllowed() {
        assertEquals(Action.ALLOW, TrustPolicy.decide(PackageClass.COMMUNITY, community(), SHA, true,
                new Verified(goodCommunity())).action());
    }

    @Test
    void communityWithInvalidEvidenceWarnsButNamesTheProblem() {
        Decision d = TrustPolicy.decide(PackageClass.COMMUNITY, community(), OTHER_SHA, true,
                new Verified(goodCommunity()));
        assertEquals(Action.WARN, d.action(), d.toString());
        assertEquals("REG006", d.code());
        assertTrue(d.message().contains("digest"), d.message());
    }

    @Test
    void communityWithUnresolvedTagCommitWarnsReg008() {
        Expected noCommit = new Expected("acme/hello", null, null, "refs/tags/hello-1.2.3", null, SLSA);
        Decision d = TrustPolicy.decide(PackageClass.COMMUNITY, noCommit, SHA, true,
                new Verified(goodCommunity()));
        assertEquals(Action.WARN, d.action());
        assertEquals("REG008", d.code());
    }

    @Test
    void communityWithMismatchedRepoStillWarns() {
        ProvenanceEvidence e = with(goodCommunity(), null, "evil/hello", null, null, null, null, null);
        Decision d = TrustPolicy.decide(PackageClass.COMMUNITY, community(), SHA, true, new Verified(e));
        assertEquals(Action.WARN, d.action());
        assertEquals("REG006", d.code());
    }
}
