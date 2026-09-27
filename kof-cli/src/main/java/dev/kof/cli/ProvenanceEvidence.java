package dev.kof.cli;

import java.util.List;

/**
 * D-ARTIFACT-TRUST — o que uma atestação de proveniência de build AFIRMA sobre um
 * artefato, em propriedades NEUTRAS (o contrato nao nomeia fornecedor: R11/D-KOF-FIRST).
 * Um {@link ProvenanceVerifier} produz isto DEPOIS de conferir assinatura/certificado/log;
 * a {@link TrustPolicy} compara cada campo com a identidade ESPERADA — que vem do pedido
 * do usuario (owner/repo@versao) e da politica, nunca do que a propria release declara.
 *
 * @param subjectSha256      digests (hex) dos artefatos cobertos pela atestação
 * @param repository         repositorio-fonte atestado (`owner/repo`)
 * @param signerRepository   repositorio que hospeda o workflow que assinou (`owner/repo`)
 * @param signerWorkflowPath caminho do workflow que assinou (ex.: `.github/workflows/release.yml`)
 * @param sourceRef          ref de origem do build (ex.: `refs/tags/hello-1.2.3`)
 * @param sourceSha          commit de origem do build (40 hex)
 * @param predicateType      tipo do predicado (ex.: `https://slsa.dev/provenance/v1`)
 */
record ProvenanceEvidence(List<String> subjectSha256, String repository, String signerRepository,
                          String signerWorkflowPath, String sourceRef, String sourceSha,
                          String predicateType) {
}
