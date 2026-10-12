package dev.kof.compiler;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * JVM-03 (audit 0.6.0, §673): an if/switch-EXPRESSION whose branches are lambdas
 * of the SAME signature joined to {@code Object} (the concrete Lambda0/Lambda1
 * classNames differ), so the call site emitted a phantom
 * {@code invokestatic g:(I)Ljava/lang/Object;} → VerifyError on load. Same-
 * signature lambda branches now unify to the synthetic INTERFACE. Extraído de
 * {@code LambdaE2ETest} (Fase 3, {@code D-TEST-ARCHITECTURE-GO}) — o teto de
 * 500 linhas do arquivo de teste.
 */
class LambdaBranchUnificationE2ETest extends LambdaSupport {

    private static final String IF_EXPR_LAMBDA_BRANCHES = """
            main() {
                var flag = true
                var g = if (flag) (x: Int) -> x + 1 else (x: Int) -> x + 1
                println(g(5))
                var h = if (!flag) (x: Int) -> x * 100 else (x: Int) -> x + 2
                println(h(5))
            }
            """;

    @Test
    void ifExprLambdaBranchesUnifyToSamInterfaceJvm(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, IF_EXPR_LAMBDA_BRANCHES);
        runJvm(source, tempDir.resolve("out"), "6\n7");
    }

    @Test
    void ifExprLambdaBranchesUnifyToSamInterfaceNative(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, IF_EXPR_LAMBDA_BRANCHES);
        runNative(source, tempDir.resolve("out"), "6\n7");
    }

    private static final String SWITCH_EXPR_LAMBDA_BRANCHES = """
            main() {
                var n = 1
                var g = switch (n) {
                    case 1 -> (x: Int) -> x + 10
                    default -> (x: Int) -> x + 20
                }
                println(g(5))
            }
            """;

    @Test
    void switchExprLambdaBranchesUnifyToSamInterfaceJvm(@TempDir Path tempDir) throws IOException {
        Path source = tempDir.resolve("Main.kf");
        Files.writeString(source, SWITCH_EXPR_LAMBDA_BRANCHES);
        runJvm(source, tempDir.resolve("out"), "15");
    }
}
