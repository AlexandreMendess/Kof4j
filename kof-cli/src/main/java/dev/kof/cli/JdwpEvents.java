package dev.kof.cli;

import java.io.IOException;

/**
 * EventRequest.Set (15,1) builders + breakpoint resolution (regra 7: nome pela
 * responsabilidade). O transporte (sendCommand) e as consultas ficam no
 * {@link JdwpClient}; aqui so a montagem dos pedidos de evento (breakpoint por
 * linha, SingleStep, Exception) e o controle de thread do DAP `pause`.
 */
final class JdwpEvents {

    private final JdwpClient client;

    JdwpEvents(JdwpClient client) {
        this.client = client;
    }

    /**
     * EventRequest.Set (15,1) for a line breakpoint in the given class.
     * The class must be prepared; line maps through the Kof LineNumberTable.
     */
    void setLineBreakpoint(String className, int line) throws IOException {
        setLineBreakpoint(client.typeIdOfClass(className), line);
    }

    void setLineBreakpoint(long typeId, int line) throws IOException {
        long methodId = methodWithLine(typeId, line);
        long[] lines = client.lineTable(typeId, methodId);
        long codeIndex = -1;
        for (int i = 0; i + 1 < lines.length; i += 2) {
            if (lines[i + 1] == line) {
                codeIndex = lines[i];
                break;
            }
        }
        if (codeIndex < 0) {
            codeIndex = lines[0];
        }
        JdwpPacket req = new JdwpPacket();
        req.writeByte(2);   // event kind: Breakpoint
        req.writeByte(2);   // suspend policy: ALL
        req.writeInt(1);    // modifier count
        req.writeByte(7);   // LocationOnly
        req.writeByte(1);   // location tag: ClassType
        req.writeReference(typeId);
        req.writeReference(methodId);
        req.writeLong(codeIndex);
        client.sendCommand(15, 1, req).skipRemaining();
    }

    /**
     * EventRequest.Set (15,1) for a line SingleStep in the given thread.
     * {@code depth}: 0 = STEP_INTO, 1 = STEP_OVER, 2 = STEP_OUT (JDWP Step
     * modifier kind 10; size 1 = STEP_LINE). The request is auto-deleted by the
     * VM once the step event is generated.
     */
    void setStepRequest(long threadId, int depth) throws IOException {
        JdwpPacket req = new JdwpPacket();
        req.writeByte(1);   // event kind: SingleStep
        req.writeByte(2);   // suspend policy: ALL
        req.writeInt(1);    // modifier count
        req.writeByte(10);  // Step modifier
        req.writeReference(threadId);
        req.writeInt(1);    // size: STEP_LINE
        req.writeInt(depth); // depth: 0 into, 1 over, 2 out
        client.sendCommand(15, 1, req).skipRemaining();
    }

    /**
     * EventRequest.Set (15,1) for the Exception event (kind 4) with the
     * {@code ExceptionOnly} modifier (kind 8): {@code refType} 0 = all classes,
     * {@code caught}/{@code uncaught} choose the faces (DAP
     * {@code setExceptionBreakpoints}).
     */
    void setExceptionRequest(boolean caught, boolean uncaught) throws IOException {
        JdwpPacket req = new JdwpPacket();
        req.writeByte(4);   // event kind: Exception
        req.writeByte(2);   // suspend policy: ALL
        req.writeInt(1);    // modifier count
        req.writeByte(8);   // ExceptionOnly modifier
        req.writeReference(0); // refType: 0 = all classes
        req.writeByte(caught ? 1 : 0);
        req.writeByte(uncaught ? 1 : 0);
        client.sendCommand(15, 1, req).skipRemaining();
    }

    /** ThreadReference.Suspend (11,2) — DAP `pause` on the JVM (no event fires). */
    void suspendThread(long threadId) throws IOException {
        JdwpPacket req = new JdwpPacket();
        req.writeReference(threadId);
        client.sendCommand(11, 2, req).skipRemaining();
    }

    /**
     * Suspend the USER threads and return the one to report as stopped. The
     * JDWP agent's own threads (names starting with "JDWP") are NEVER suspended:
     * suspending the debug-transport thread freezes the protocol itself (measured
     * — the next JDWP command times out and the session dies). The "main" thread
     * is reported; failing that, the first user thread. A thread that dies between
     * AllThreads and Name/Suspend is skipped, never fatal.
     */
    long suspendUserThreads() throws IOException {
        long main = -1;
        long first = -1;
        for (long tid : client.allThreads()) {
            String name;
            try {
                name = client.threadName(tid);
            } catch (IOException gone) {
                continue; // thread died between AllThreads and Name
            }
            if (name != null && name.startsWith("JDWP")) {
                continue;
            }
            if (first < 0) {
                first = tid;
            }
            if ("main".equals(name) && main < 0) {
                main = tid;
            }
            try {
                client.suspendThread(tid);
            } catch (IOException gone) {
                // already gone — nothing to pause
            }
        }
        return main >= 0 ? main : first;
    }

    /** ReferenceType.Methods (2,5): pick the method whose LineTable holds {@code line}. */
    private long methodWithLine(long typeId, int line) throws IOException {
        JdwpPacket req = new JdwpPacket();
        req.writeReference(typeId);
        JdwpPacket reply = client.sendCommand(2, 5, req);
        int count = reply.readInt();
        for (int i = 0; i < count; i++) {
            reply.readReference();
            reply.readString();
            reply.readString();
            reply.readInt(); // modifiers
        }
        long bestMethod = findMethodWithLine(typeId, line);
        if (bestMethod == 0) {
            throw new IOException("no method contains line " + line);
        }
        return bestMethod;
    }

    private long findMethodWithLine(long typeId, int line) throws IOException {
        JdwpPacket req = new JdwpPacket();
        req.writeReference(typeId);
        JdwpPacket reply = client.sendCommand(2, 5, req);
        int count = reply.readInt();
        for (int i = 0; i < count; i++) {
            long methodId = reply.readReference();
            String name = reply.readString();
            reply.readString();
            reply.readInt();
            if ("<init>".equals(name) || "<clinit>".equals(name)) continue;
            try {
                long[] lines = client.lineTable(typeId, methodId);
                for (int li = 0; li + 1 < lines.length; li += 2) {
                    if (lines[li + 1] == line) {
                        return methodId;
                    }
                }
            } catch (IOException e) {
                if (System.getenv("KOF_DEBUG_TRACE") != null) {
                    System.err.println("kof debug: lineTable(" + name + "): " + e.getMessage());
                }
            }
        }
        return 0;
    }
}
