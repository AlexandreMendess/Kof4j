package dev.kof.cli;

import dev.kof.cli.ProvenanceVerifier.Outcome;
import dev.kof.cli.ProvenanceVerifier.Rejected;
import dev.kof.cli.ProvenanceVerifier.Unavailable;
import dev.kof.cli.ProvenanceVerifier.Verified;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * D-ARTIFACT-TRUST (c) — a COLA do `kof deps resolve`: hash do tar.gz REAL baixado, identidade
 * esperada montada do PEDIDO (owner/repo@versao), verificador injetado, politica, aviso/erro.
 * Tarballs reais (o mesmo `CmdDeploy.writeTarGz` do publish). O verificador aqui e um script:
 * devolve a evidencia da atestacao LEGITIMA, como faria o real, para o pacote que for.
 */
class TrustGateTest {

    static final String TAG = "hello-1.2.3";
    static final String COMMIT = "1".repeat(40);
    static final String SLSA = "https://slsa.dev/provenance/v1";
    static final String WORKFLOW = ".github/workflows/release.yml";

    /** Pacote no formato do publish; `jarBody` distingue pacotes (SHA256SUMS sempre CONSISTENTE com o jar). */
    private static Path pack(Path dir, String jarBody) throws Exception {
        Files.createDirectories(dir);
        Path jar = dir.resolve("hello-1.2.3.jar");
        try (JarOutputStream jo = new JarOutputStream(Files.newOutputStream(jar))) {
            jo.putNextEntry(new JarEntry("Hello.txt"));
            jo.write(jarBody.getBytes(StandardCharsets.UTF_8));
            jo.closeEntry();
        }
        Files.writeString(dir.resolve("SHA256SUMS"), CmdDeploy.sha256Hex(jar) + "  " + jar.getFileName() + "\n");
        Path tgz = dir.resolve("hello-1.2.3.tar.gz");
        CmdDeploy.writeTarGz(tgz, dir, List.of(jar.getFileName(), Path.of("SHA256SUMS")), 0644);
        return tgz;
    }

    private static ProvenanceEvidence evidenceFor(String sha256, String repo, String workflowRepo,
                                                  String path, String ref, String commit) {
        return new ProvenanceEvidence(List.of(sha256), repo, workflowRepo, path, ref, commit, SLSA);
    }

    private static ProvenanceEvidence legit(String sha256, String owner) {
        return evidenceFor(sha256, owner + "/hello", owner + "/hello", WORKFLOW, "refs/tags/" + TAG, COMMIT);
    }

    /** Captura System.err durante {@code body}. */
    private static String captureErr(Runnable body) {
        PrintStream old = System.err;
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        System.setErr(new PrintStream(buf, true, StandardCharsets.UTF_8));
        try {
            body.run();
        } finally {
            System.setErr(old);
        }
        return buf.toString(StandardCharsets.UTF_8);
    }

    private static IOException blocked(String owner, Path tgz, Path bundle, ProvenanceVerifier v,
                                       String commit, boolean enforcing) {
        return assertThrows(IOException.class,
                () -> TrustGate.check(owner, "hello", TAG, tgz, bundle, v, commit, enforcing));
    }

    // ---- classificacao oficial x comunitario ----

    @Test
    void officialOwnersAreExactNotLookalikes() {
        assertTrue(TrustGate.isOfficial("KofLang"));
        assertTrue(TrustGate.isOfficial("koflang"), "owners do GitHub sao case-insensitive");
        assertTrue(TrustGate.isOfficial("KOFLANG"));
        for (String impostor : List.of("KofLang-evil", "Kof-Lang", "xKofLang", "KofLang.", "KofLang_", "koflang2",
                "", "evil/KofLang")) {
            assertFalse(TrustGate.isOfficial(impostor), "nao e oficial: '" + impostor + "'");
        }
        assertFalse(TrustGate.isOfficial(null));
    }

    // ---- caminho feliz: evidencia LEGITIMA do pacote LEGITIMO ----

    @Test
    void officialLegitPackageWithLegitEvidencePassesSilently(@TempDir Path tmp) throws Exception {
        Path tgz = pack(tmp.resolve("a"), "legit");
        Path bundle = Files.writeString(tmp.resolve("bundle.jsonl"), "{}");
        List<String> seenShas = new ArrayList<>();
        List<Path> seenBundles = new ArrayList<>();
        ProvenanceVerifier v = (artifact, sha, b) -> {
            seenShas.add(sha);
            seenBundles.add(b);
            return new Verified(legit(sha, "KofLang"));
        };
        String err = captureErr(() -> assertDoesNotThrow(
                () -> TrustGate.check("KofLang", "hello", TAG, tgz, bundle, v, COMMIT, true)));
        assertEquals("", err.strip(), "evidencia valida nao gera ruido");
        assertEquals(List.of(CmdDeploy.sha256Hex(tgz)), seenShas,
                "o verificador recebe o digest do arquivo REAL baixado (nunca um digest lido da release)");
        assertEquals(List.of(bundle), seenBundles);
    }

    // ---- pacote adulterado ----

    @Test
    void tamperedPackageWithConsistentJarAndSumsIsBlockedByProvenance(@TempDir Path tmp) throws Exception {
        // O ataque que a integridade embutida NAO pega: o atacante troca o jar E refaz o SHA256SUMS
        // dentro do tar.gz (o REG002 confere jar x SUMS do mesmo pacote e passa). So a atestacao,
        // que cobre o digest do tar.gz ORIGINAL, denuncia.
        Path original = pack(tmp.resolve("orig"), "legit");
        Path tampered = pack(tmp.resolve("evil"), "backdoor");
        String originalSha = CmdDeploy.sha256Hex(original);
        assertNotEquals(originalSha, CmdDeploy.sha256Hex(tampered), "pacotes genuinamente distintos");
        ProvenanceVerifier realAttestationOfTheOriginal = (a, sha, b) -> new Verified(legit(originalSha, "KofLang"));
        Path bundle = Files.writeString(tmp.resolve("bundle.jsonl"), "{}");

        assertDoesNotThrow(() -> TrustGate.check("KofLang", "hello", TAG, original, bundle,
                realAttestationOfTheOriginal, COMMIT, true), "controle: o original passa");
        IOException e = blocked("KofLang", tampered, bundle, realAttestationOfTheOriginal, COMMIT, true);
        assertTrue(e.getMessage().startsWith("REG006"), e.getMessage());
        assertTrue(e.getMessage().contains("digest"), e.getMessage());
    }

    // ---- evidencia ausente / verificador sem veredito ----

    @Test
    void officialWithoutEvidenceIsBlockedWhenEnforcing(@TempDir Path tmp) throws Exception {
        Path tgz = pack(tmp.resolve("a"), "legit");
        IOException e = blocked("KofLang", tgz, null, ProvenanceVerifier.NONE, COMMIT, true);
        assertTrue(e.getMessage().startsWith("REG005"), e.getMessage());
    }

    @Test
    void observeModeNeverBlocksButShoutsWhatWouldBeBlocked(@TempDir Path tmp) throws Exception {
        Path tgz = pack(tmp.resolve("a"), "legit");
        String err = captureErr(() -> assertDoesNotThrow(
                () -> TrustGate.check("KofLang", "hello", TAG, tgz, null, ProvenanceVerifier.NONE, COMMIT, false)));
        assertTrue(err.contains("REG005"), "o codigo aparece: " + err);
        assertTrue(err.toLowerCase().contains("would block"), "rollout em observe diz o que bloquearia: " + err);
    }

    @Test
    void communityWithoutEvidenceWarnsAndProceedsInBothModes(@TempDir Path tmp) throws Exception {
        Path tgz = pack(tmp.resolve("a"), "legit");
        for (boolean enforcing : new boolean[]{true, false}) {
            String err = captureErr(() -> assertDoesNotThrow(
                    () -> TrustGate.check("acme", "hello", TAG, tgz, null, ProvenanceVerifier.NONE, COMMIT, enforcing)));
            assertTrue(err.contains("REG005"), "warning honesto (enforcing=" + enforcing + "): " + err);
            assertFalse(err.toLowerCase().contains("would block"), "comunitario nunca bloqueia: " + err);
        }
    }

    @Test
    void noVerifierWiredFailsClosedForOfficialWithEvidencePresent(@TempDir Path tmp) throws Exception {
        Path tgz = pack(tmp.resolve("a"), "legit");
        Path bundle = Files.writeString(tmp.resolve("bundle.jsonl"), "{}");
        IOException e = blocked("KofLang", tgz, bundle, ProvenanceVerifier.NONE, COMMIT, true);
        assertTrue(e.getMessage().startsWith("REG007"), e.getMessage());
    }

    @Test
    void verifierThatCrashesIsUnavailableNotVerified(@TempDir Path tmp) throws Exception {
        Path tgz = pack(tmp.resolve("a"), "legit");
        Path bundle = Files.writeString(tmp.resolve("bundle.jsonl"), "{}");
        ProvenanceVerifier boom = (a, s, b) -> { throw new IOException("gh: exit 127"); };
        IOException e = blocked("KofLang", tgz, bundle, boom, COMMIT, true);
        assertTrue(e.getMessage().startsWith("REG007"), e.getMessage());
        assertTrue(e.getMessage().contains("gh: exit 127"), "a causa aparece: " + e.getMessage());
    }

    @Test
    void verifierThatThrowsUncheckedIsAlsoFailClosed(@TempDir Path tmp) throws Exception {
        Path tgz = pack(tmp.resolve("a"), "legit");
        Path bundle = Files.writeString(tmp.resolve("bundle.jsonl"), "{}");
        ProvenanceVerifier boom = (a, s, b) -> { throw new IllegalStateException("parser blew up"); };
        IOException e = blocked("KofLang", tgz, bundle, boom, COMMIT, true);
        assertTrue(e.getMessage().startsWith("REG007"), e.getMessage());
    }

    @Test
    void cryptographicRejectionBlocksReg006(@TempDir Path tmp) throws Exception {
        Path tgz = pack(tmp.resolve("a"), "legit");
        Path bundle = Files.writeString(tmp.resolve("bundle.jsonl"), "{}");
        Outcome rejected = new Rejected("no matching attestation for digest");
        IOException e = blocked("KofLang", tgz, bundle, (a, s, b) -> rejected, COMMIT, true);
        assertTrue(e.getMessage().startsWith("REG006"), e.getMessage());
        assertTrue(e.getMessage().contains("no matching attestation"), e.getMessage());
    }

    @Test
    void unavailableVerifierReasonIsSurfaced(@TempDir Path tmp) throws Exception {
        Path tgz = pack(tmp.resolve("a"), "legit");
        Path bundle = Files.writeString(tmp.resolve("bundle.jsonl"), "{}");
        IOException e = blocked("KofLang", tgz, bundle, (a, s, b) -> new Unavailable("offline"), COMMIT, true);
        assertTrue(e.getMessage().contains("offline"), e.getMessage());
    }

    // ---- identidade esperada vem do PEDIDO, nao da evidencia ----

    @Test
    void evidenceForAnotherRepoWorkflowRefOrCommitIsBlocked(@TempDir Path tmp) throws Exception {
        Path tgz = pack(tmp.resolve("a"), "legit");
        String sha = CmdDeploy.sha256Hex(tgz);
        Path bundle = Files.writeString(tmp.resolve("bundle.jsonl"), "{}");
        List<ProvenanceEvidence> wrong = List.of(
                evidenceFor(sha, "evil/hello", "KofLang/hello", WORKFLOW, "refs/tags/" + TAG, COMMIT),
                evidenceFor(sha, "KofLang/hello", "evil/hello", WORKFLOW, "refs/tags/" + TAG, COMMIT),
                evidenceFor(sha, "KofLang/hello", "KofLang/hello", ".github/workflows/evil.yml", "refs/tags/" + TAG, COMMIT),
                evidenceFor(sha, "KofLang/hello", "KofLang/hello", WORKFLOW, "refs/heads/main", COMMIT),
                evidenceFor(sha, "KofLang/hello", "KofLang/hello", WORKFLOW, "refs/tags/hello-0.0.1", COMMIT),
                evidenceFor(sha, "KofLang/hello", "KofLang/hello", WORKFLOW, "refs/tags/" + TAG, "2".repeat(40)));
        for (ProvenanceEvidence ev : wrong) {
            IOException e = blocked("KofLang", tgz, bundle, (a, s, b) -> new Verified(ev), COMMIT, true);
            assertTrue(e.getMessage().startsWith("REG006"), ev + " => " + e.getMessage());
        }
    }

    @Test
    void officialWhoseTagCommitCouldNotBeResolvedFailsClosed(@TempDir Path tmp) throws Exception {
        Path tgz = pack(tmp.resolve("a"), "legit");
        Path bundle = Files.writeString(tmp.resolve("bundle.jsonl"), "{}");
        String sha = CmdDeploy.sha256Hex(tgz);
        IOException e = blocked("KofLang", tgz, bundle, (a, s, b) -> new Verified(legit(sha, "KofLang")), null, true);
        assertTrue(e.getMessage().startsWith("REG008"), e.getMessage());
    }

    @Test
    void communityWithLegitEvidenceOfItsOwnRepoPassesSilentlyWhateverItsWorkflow(@TempDir Path tmp) throws Exception {
        Path tgz = pack(tmp.resolve("a"), "legit");
        Path bundle = Files.writeString(tmp.resolve("bundle.jsonl"), "{}");
        String sha = CmdDeploy.sha256Hex(tgz);
        ProvenanceEvidence ev = evidenceFor(sha, "acme/hello", "acme/hello", ".github/workflows/build.yml",
                "refs/tags/" + TAG, COMMIT);
        String err = captureErr(() -> assertDoesNotThrow(
                () -> TrustGate.check("acme", "hello", TAG, tgz, bundle, (a, s, b) -> new Verified(ev), COMMIT, true)));
        assertEquals("", err.strip(), err);
    }
}
