package dev.kof.cli;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * §438: the cli debug/serve tests spawn child processes — {@code kof debug}
 * launches a debuggee JVM with {@code -agentlib:jdwp=...,suspend=y}, and
 * {@code kof serve} (kof-native app) launches the served JVM. Destroying only
 * the CLI (SIGTERM) does not run its shutdown hook, so the child survives,
 * holds its deleted {@code /tmp/kof-debug-*}/{@code kof-serve-*} dir and the
 * tmpfs never gets the space back (measured: 15 + 6 orphans alive for 1d+,
 * then later suites die with {@code Cota da disco excedida}).
 *
 * <p>Single teardown point: kill the whole tree (descendants first, then the
 * CLI) and wait so the OS can reap it. Use this in every {@code finally}
 * instead of {@code Process#destroy()}.
 */
final class CliProcessTree {

    private CliProcessTree() {}

    static void terminate(Process p) {
        if (p == null) {
            return;
        }
        List<ProcessHandle> descendants;
        try {
            descendants = p.descendants().toList();
        } catch (RuntimeException e) {
            descendants = List.of();
        }
        // SIGTERM first: the CLI's shutdown hook (§438) then kills its debuggee
        // and deletes its temp dir. Only force-kill what refuses to leave.
        p.destroy();
        if (!await(p, 5)) {
            p.destroyForcibly();
        }
        for (ProcessHandle h : descendants) {
            if (h.isAlive()) {
                h.destroyForcibly();
            }
        }
        await(p, 5);
    }

    private static boolean await(Process p, int seconds) {
        try {
            return p.waitFor(seconds, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }
}
