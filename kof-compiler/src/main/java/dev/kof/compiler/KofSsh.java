package dev.kof.compiler;

import java.util.List;

/**
 * kof.ssh — remote command execution over kof.process (universal plan Stage 2,
 * row 2.3; VISION §6.1 option B: "SSH — over kof.process/FFI").
 *
 * <p>Pure lowering: {@code cmd} builds the argv list and {@code run} lowers onto
 * the process layer, exactly like {@code kof.shell} (2.2). The argv is NEVER a
 * string concatenated for a shell to re-parse: the host and the command stay one
 * element each, so metacharacters survive literally (the sh -c injection class).
 * {@code BatchMode=yes} + {@code ConnectTimeout=5} keep the call non-interactive
 * and bounded.
 *
 * <pre>
 *   var r = ssh.run("user@host", "uname -a")
 *   if (ssh.ok(r)) println(r.stdout)
 *   var argv = ssh.cmd("user@host", "uname -a")   // argv builder (testable)
 * </pre>
 *
 * The Result type IS kof.process's Result (one shape, never a fork). JVM and JS
 * are real (the process layer binds on both); Native refuses at compile time with
 * the inherited {@code PROC001} (R7 honest scope), never a silent stub (R6).
 */
public final class KofSsh {

    private KofSsh() {}

    /** Namespace id, literal for the R1 boundary ledger (scripts/stdlib_boundary.txt). */
    static final String NAMESPACE = "kof.ssh";

    /** Names accepted by dispatch (catalog for LSP). */
    static List<String> functions() { return List.of("cmd", "run", "ok"); }

    record SshCall(String function, Type returnType, List<Type> parameterTypes) {
    }

    static SshCall staticCall(String methodName, List<Type> argTypes) {
        switch (methodName) {
            // cmd(host, command) -> argv list (["ssh", "-o", "BatchMode=yes",
            // "-o", "ConnectTimeout=5", host, command]); never a shell string.
            case "cmd" -> {
                if (argTypes.size() != 2) return null;
                if (!BuiltinTypes.isString(argTypes.get(0))) return null;
                if (!BuiltinTypes.isString(argTypes.get(1))) return null;
                return new SshCall("kof_ssh_argv", KofProcess.STRING_LIST,
                        List.of(BuiltinTypes.STRING, BuiltinTypes.STRING));
            }
            // run(host, command) -> ProcessResult (executes the argv above).
            case "run" -> {
                if (argTypes.size() != 2) return null;
                if (!BuiltinTypes.isString(argTypes.get(0))) return null;
                if (!BuiltinTypes.isString(argTypes.get(1))) return null;
                return new SshCall("kof_ssh_run", KofProcess.RESULT,
                        List.of(BuiltinTypes.STRING, BuiltinTypes.STRING));
            }
            // ok(result) -> Bool; pure field/compare on kof.process's Result.
            case "ok" -> {
                if (argTypes.size() != 1) return null;
                if (!KofProcess.isResult(argTypes.get(0))) return null;
                return new SshCall("ok", Type.PrimitiveType.BOOL, List.of(KofProcess.RESULT));
            }
            default -> {
                return null;
            }
        }
    }
}
