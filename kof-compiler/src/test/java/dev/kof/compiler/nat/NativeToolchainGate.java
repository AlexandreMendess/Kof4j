package dev.kof.compiler.nat;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Gate de ferramenta nativa para testes (as/ld no PATH). Irmão de escopo de
 * {@code KofDebugNativeTest}/{@code NativeCrossLinkTest} guards pré-existentes:
 * assumeTrue(present()) pula honesto (nunca red falso) quando o host não tem o
 * toolchain x86-64. Criado como resgate do tip test-compile-red (#945
 * follow-up 20/09): os call-sites em BareCollection*E2ETest e os 3 JS-by-node
 * E2ETs referenciam esta classe, mas ela não estava no commit. Se a dona do
 * #945 tiver contrato próprio, ela substitui; o contrato aqui é o medido no
 * uso: boolean, sem throws, "as" E "ld" executáveis no PATH.
 */
public final class NativeToolchainGate {

    private NativeToolchainGate() {}

    public static boolean present() {
        return onPath("as") != null && onPath("ld") != null;
    }

    private static String onPath(String name) {
        String path = System.getenv("PATH");
        if (path == null) {
            return null;
        }
        for (String dir : path.split(File.pathSeparator)) {
            if (dir.isEmpty()) {
                continue;
            }
            Path cand = Path.of(dir, name);
            if (Files.isExecutable(cand)) {
                return cand.toString();
            }
        }
        return null;
    }
}
