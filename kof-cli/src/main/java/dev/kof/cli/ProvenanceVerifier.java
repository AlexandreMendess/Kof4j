package dev.kof.cli;

import java.io.IOException;
import java.nio.file.Path;

/**
 * D-ARTIFACT-TRUST — costura entre o `kof deps resolve` e quem CONFERE criptograficamente
 * a atestação (assinatura, certificado, log de transparencia). Nada de criptografia
 * caseira (R11): a implementacao de producao delega a uma ferramenta auditada (R9).
 * Tres desfechos, e "nao deu para verificar" NUNCA vira "verificado" (R6).
 */
interface ProvenanceVerifier {

    sealed interface Outcome permits Verified, Rejected, Unavailable {}

    /** Assinatura/certificado/log conferem: estas sao as afirmacoes da atestação. */
    record Verified(ProvenanceEvidence evidence) implements Outcome {}

    /** A conferencia criptografica FALHOU (bundle adulterado, assinatura invalida, sem log). */
    record Rejected(String reason) implements Outcome {}

    /** Nao foi possivel verificar (sem ferramenta, offline): nao afirma nada, nem a favor nem contra. */
    record Unavailable(String reason) implements Outcome {}

    Outcome verify(Path artifact, String artifactSha256, Path evidenceBundle) throws IOException;

    /** Nenhum verificador ligado ainda (fila (b)/(c) do contrato): honesto, nunca aprova. */
    ProvenanceVerifier NONE = (artifact, artifactSha256, evidenceBundle) -> new Unavailable(
            "no provenance verifier is wired into this kof build yet (D-ARTIFACT-TRUST): cannot verify "
                    + artifact + " (sha256=" + artifactSha256 + ") against " + evidenceBundle);
}
