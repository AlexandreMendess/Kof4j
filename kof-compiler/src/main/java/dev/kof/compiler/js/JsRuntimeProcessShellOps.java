package dev.kof.compiler.js;
import dev.kof.compiler.KofCall;

import java.util.List;

/**
 * JsRuntimeProcessShellOps — lowering das ops de processo/shell (kof.process
 * run/exit/spawn + handle ops, kof.shell argv/runWith) para os shims JS da
 * tabela Io (REFACTOR do gate 500: extraído de JsRuntimeOps 19/09, fatia
 * platform spawn-JS — o ficheiro mãe estava a 23 linhas do crítico).
 * Browser sem kof_platform: os shims Io delegam ao Proxy honesto da tabela
 * (R7/R6) — nunca ReferenceError cru.
 */
final class JsRuntimeProcessShellOps {

    private JsRuntimeProcessShellOps() {
    }

    static boolean handle(JsMethodParser p, List<Object> stack, KofCall kc,
                          List<dev.kof.compiler.js.JsIr.JsExpression> args) {
        String name = kc.methodName();
        if (name.equals("kof_process_run")) {
            p.lc.registerIoRuntime("kofProcessRun");
            stack.add(new JsIr.JsCall(new JsIr.JsIdentifier("kofProcessRun"), args));
            return true;
        }
        if (name.equals("kof_process_exit")) {
            // sentinel capturado pelo KofJsRunner — nunca use System.exit
            // dentro da engine (mataria o processo hospedeiro)
            p.lc.registerIoRuntime("kofProcessExit");
            stack.add(new JsIr.JsCall(new JsIr.JsIdentifier("kofProcessExit"), args));
            return true;
        }
        if (name.equals("kof_shell_argv")) {
            // kof.shell cmd(program, args) — argv builder (Stage 2 / 2.2)
            p.lc.registerIoRuntime("kofShellArgv");
            stack.add(new JsIr.JsCall(new JsIr.JsIdentifier("kofShellArgv"), args));
            return true;
        }
        if (name.equals("kof_ssh_argv")) {
            // kof.ssh cmd(host, command) — argv builder (Stage 2 / 2.3)
            p.lc.registerIoRuntime("kofSshArgv");
            stack.add(new JsIr.JsCall(new JsIr.JsIdentifier("kofSshArgv"), args));
            return true;
        }
        if (name.equals("kof_ssh_run")) {
            // kof.ssh run(host, command) — executes over the process layer
            p.lc.registerIoRuntime("kofSshRun");
            stack.add(new JsIr.JsCall(new JsIr.JsIdentifier("kofSshRun"), args));
            return true;
        }
        if (name.equals("kof_shell_runwith")) {
            // kof.shell runWith(argv, cwd, env) — 2.2.3 (JVM + JS host binding)
            p.lc.registerIoRuntime("kofShellRunWith");
            stack.add(new JsIr.JsCall(new JsIr.JsIdentifier("kofShellRunWith"), args));
            return true;
        }
        if (name.equals("kof_shell_pipeline")) {
            // kof.shell pipeline(stages) — cadeia real no host (pump threads),
            // espelho do kof_shell_pipeline JVM (20/09; fecha o residual 2.2)
            p.lc.registerIoRuntime("kofShellPipeline");
            stack.add(new JsIr.JsCall(new JsIr.JsIdentifier("kofShellPipeline"), args));
            return true;
        }
        if (name.equals("kof_process_spawn")) {
            // F10 no JS: host KofJsProcessBridge (spawn + handle ops com o
            // contrato exato do binding JVM — paridade por construção).
            p.lc.registerIoRuntime("kofProcessSpawn");
            stack.add(new JsIr.JsCall(new JsIr.JsIdentifier("kofProcessSpawn"), args));
            return true;
        }
        if (name.equals("kof_spawn_write") || name.equals("kof_spawn_read_line")
                || name.equals("kof_spawn_exit_code") || name.equals("kof_spawn_kill")
                || name.equals("kof_spawn_alive")) {
            String shim = switch (name) {
                case "kof_spawn_write" -> "kofSpawnWrite";
                case "kof_spawn_read_line" -> "kofSpawnReadLine";
                case "kof_spawn_exit_code" -> "kofSpawnExitCode";
                case "kof_spawn_kill" -> "kofSpawnKill";
                default -> "kofSpawnAlive";
            };
            p.lc.registerIoRuntime(shim);
            stack.add(new JsIr.JsCall(new JsIr.JsIdentifier(shim), args));
            return true;
        }
        return false;
    }
}
