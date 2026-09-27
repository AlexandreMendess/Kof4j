package dev.kof.cli;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * JdwpClient — minimal raw JDWP wire client (no jdk.jdi dependency).
 *
 * Drives a debuggee JVM launched with -agentlib:jdwp. Used by KofDebug
 * to set breakpoints by Kof source line (the JVM backend emits the
 * LineNumberTable) and to read stack frames.
 */
final class JdwpClient {

    record FullFrame(long frameId, long threadId, long typeId, long methodId, long codeIndex,
                     String methodName, int line) {
    }

    List<FullFrame> framesFull(long threadId, int depth) throws IOException {
        JdwpPacket req = new JdwpPacket();
        req.writeReference(threadId);
        req.writeInt(0);
        // oficial: maxFrames > tamanho do stack = error INVALID_LENGTH (504, medido)
        // — pedir o FrameCount (11,7) primeiro e usar o proprio tamanho.
        JdwpPacket cnt = new JdwpPacket();
        cnt.writeReference(threadId);
        int total = sendCommand(11, 7, cnt).readInt(); // ThreadReference.FrameCount
        req.writeInt(Math.min(Math.max(1, depth), total));
        JdwpPacket reply = sendCommand(11, 6, req); // ThreadReference.Frames
        int count = reply.readInt();
        List<FullFrame> frames = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            long frameId = reply.readReference();
            reply.readByte(); // location tag
            long typeId = reply.readReference();
            long methodId = reply.readReference();
            long codeIndex = reply.readLong();
            // A frame with no debug info (native/JDK methods such as Thread.sleep)
            // has no LineTable/VariableTable: Method.* returns an error (101/511,
            // measured). That is per-frame information, never a reason to abort the
            // whole stack — report name "?" and line -1 for it (R6: honest, never
            // silent) and keep the Kof frames that do resolve.
            String methodName = methodName(typeId, methodId);
            int line = lineAt(typeId, methodId, codeIndex);
            frames.add(new FullFrame(frameId, threadId, typeId, methodId, codeIndex, methodName, line));
        }
        return frames;
    }

    /** Decodificacao de valores vive em JdwpValues (split mecanico, regra 7). */
    List<Object[]> locals(FullFrame frame) throws IOException {
        return values.locals(frame);
    }

    /** StringReference.Value (10,1) — conteudo de um java.lang.String para exibicao. */
    String stringValue(long objectRef) throws IOException {
        return values.stringValue(objectRef);
    }

    /** Type of a loaded class (1,2 era ClassesBySignature; aqui ReferenceType.Signature (2,1)). */
    String typeSignature(long typeId) throws IOException {
        JdwpPacket req = new JdwpPacket();
        req.writeReference(typeId);
        JdwpPacket reply = sendCommand(2, 1, req);
        return reply.readString();
    }

    record FrameInfo(long methodId, String methodName, int line, long codeIndex) {
    }

    private final JdwpValues values = new JdwpValues(this);
    private final String host;
    private final int port;
    private Socket socket;
    private DataInputStream in;
    private DataOutputStream out;
    private int idSeq = 1;
    private int refSize = 8;
    private final Object lock = new Object();
    private final java.util.Map<Integer, JdwpPacket> replies = new java.util.HashMap<>();
    private boolean eventLoopStarted = false;

    JdwpClient(String host, int port) {
        this.host = host;
        this.port = port;
    }

    void connect() throws IOException {
        IOException last = null;
        for (int attempt = 0; attempt < 20; attempt++) {
            try {
                socket = new Socket(host, port);
                socket.setSoTimeout(20000);
                last = null;
                break;
            } catch (IOException e) {
                last = e;
                try {
                    Thread.sleep(200);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        if (last != null) {
            throw last;
        }
        in = new DataInputStream(socket.getInputStream());
        out = new DataOutputStream(socket.getOutputStream());
        out.write("JDWP-Handshake".getBytes(StandardCharsets.US_ASCII));
        out.flush();
        byte[] reply = new byte[14];
        in.readFully(reply);
        if (!"JDWP-Handshake".equals(new String(reply, StandardCharsets.US_ASCII))) {
            throw new IOException("JDWP handshake failed");
        }
        int idsId = sendRaw(1, 7, new JdwpPacket());
        JdwpPacket ids = null;
        while (ids == null) {
            JdwpPacket pkt = readPacketLocked();
            if (pkt.id == idsId && !pkt.eventData) {
                ids = pkt;
            }
        }
        // JDK 25: IDSizes devolve 5 tamanhos (argIDSize saiu, medido no wire via
        // proxy 20/09 — antes ler 6 ROUBAVA 4 bytes do proximo pacote e o
        // ClassesBySignature vinha truncado). Os tamanhos sao sempre de 4 bytes
        // na ordem do JDWP: field, method, object, referenceType, frame[, arg].
        ids.readInt(); // fieldIDSize
        ids.readInt(); // methodIDSize
        ids.readInt(); // objectIDSize
        refSize = ids.readInt(); // referenceTypeIDSize
        // frameIDSize (e argIDSize, <= JDK 24) consumidos APENAS se existem no corpo;
        // o 6o readInt cego roubava 4 bytes do proximo pacote no buffer (JDK 25).
        while (ids.remaining() >= 4) {
            ids.readInt();
        }
    }

    /** VM.Resume */
    void resume() throws IOException {
        sendCommand(1, 9, new JdwpPacket());
    }

    /** VM.Dispose */
    void dispose() throws IOException {
        try {
            sendCommand(1, 6, new JdwpPacket());
        } catch (IOException ignored) {
        }
    }

    /**
     * EventRequest.Set (15,1) for ClassPrepare of the given class.
     * Returns the request id. Starts the event loop.
     */
    long setClassPrepareRequest(String className, EventHandler handler) throws IOException {
        JdwpPacket req = new JdwpPacket();
        req.writeByte(8);   // event kind: ClassPrepare (JDK 25)
        req.writeByte(2);   // suspend policy: ALL
        req.writeInt(1);    // modifier count
        req.writeByte(5);   // ClassMatch
        req.writeString(className);
        sendRaw(15, 1, req);
        // Em modo ATTACH o VM_START ja esta na fila do socket ANDES desta resposta;
        // ler o evento como se fosse reply desalinha todo o protocolo (medido 20/09:
        // OOB em ClassesBySignature com corpo truncado). Eventos sao consumidos sem
        // uso ate a resposta (launch nao precisa deles; o pump trata os posteriores).
        JdwpPacket reply;
        while (true) {
            reply = readPacketLocked();
            if (!reply.eventData) {
                break;
            }
        }
        if (reply.errorCode != 0) {
            throw new IOException("EventRequest.Set error " + reply.errorCode);
        }
        long requestId = reply.readInt();
        eventLoopStarted = true;
        Thread loop = new Thread(() -> eventLoop(handler), "jdwp-events");
        loop.setDaemon(true);
        loop.start();
        return requestId;
    }

    // EventRequest.Set builders + breakpoint resolution live in JdwpEvents
    // (regra 7); the client keeps the transport and the queries.
    private final JdwpEvents events = new JdwpEvents(this);

    void setLineBreakpoint(String className, int line) throws IOException {
        events.setLineBreakpoint(className, line);
    }

    void setLineBreakpoint(long typeId, int line) throws IOException {
        events.setLineBreakpoint(typeId, line);
    }

    void setStepRequest(long threadId, int depth) throws IOException {
        events.setStepRequest(threadId, depth);
    }

    void setExceptionRequest(boolean caught, boolean uncaught) throws IOException {
        events.setExceptionRequest(caught, uncaught);
    }

    void suspendThread(long threadId) throws IOException {
        events.suspendThread(threadId);
    }

    /** Suspend every user thread (skip the JDWP agent's own); return the stop thread. */
    long suspendUserThreads() throws IOException {
        return events.suspendUserThreads();
    }

    /** ThreadReference.Name (11,1) — skip the JDWP agent's own threads on pause. */
    String threadName(long threadId) throws IOException {
        JdwpPacket req = new JdwpPacket();
        req.writeReference(threadId);
        return sendCommand(11, 1, req).readString();
    }

    /** Method.LineTable (6,1): returns flattened [line, codeIndex, ...]. */
    long[] lineTable(long typeId, long methodId) throws IOException {
        JdwpPacket req = new JdwpPacket();
        req.writeReference(typeId);
        req.writeReference(methodId);
        JdwpPacket reply = sendCommand(6, 1, req);
        reply.readLong(); // start
        reply.readLong(); // end
        int count = reply.readInt();
        long[] lines = new long[count * 2];
        for (int i = 0; i < count; i++) {
            lines[i * 2] = reply.readLong(); // code index
            lines[i * 2 + 1] = reply.readInt(); // line code
        }
        return lines;
    }

    long typeIdOfClass(String className) throws IOException {
        // VM.ClassesBySignature (1,2) esta QUEBRADO no JDK 25.0.4 (medido no wire
        // 20/09: responde count=0 + bytes de lixo e o HotSpot congela o comando
        // seguinte; o jdb nao o usa). A rota equivalente viva = VM.Classes (1,3),
        // que devolve tag+id+assinatura+status por classe carregada.
        String descriptor = "L" + className.replace('.', '/') + ";";
        JdwpPacket reply = sendCommand(1, 3, new JdwpPacket()); // VM.Classes
        int count = reply.readInt();
        for (int i = 0; i < count; i++) {
            reply.readByte(); // refTypeTag
            long typeId = reply.readReference();
            String signature = reply.readString();
            reply.readInt(); // status
            if (descriptor.equals(signature)) {
                return typeId;
            }
        }
        throw new IOException("class not loaded: " + className);
    }

    /** VM.AllThreads (1,4). */
    List<Long> allThreads() throws IOException {
        JdwpPacket reply = sendCommand(1, 4, new JdwpPacket());
        int count = reply.readInt();
        List<Long> threads = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            threads.add(reply.readReference());
        }
        return threads;
    }

    /** ThreadReference.Frames (10,6): stack frames of a thread. */
    List<FrameInfo> frames(long threadId, int depth) throws IOException {
        JdwpPacket req = new JdwpPacket();
        req.writeReference(threadId);
        req.writeInt(0);   // startFrame
        req.writeInt(Math.max(1, depth));
        JdwpPacket reply = sendCommand(11, 6, req); // ThreadReference.Frames
        int count = reply.readInt();
        List<FrameInfo> frames = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            reply.readReference(); // frameID
            reply.readByte();      // location tag (1 = ClassType, 2 = InterfaceType)
            long typeId = reply.readReference();
            long methodId = reply.readReference();
            long codeIndex = reply.readLong();
            String methodName = methodName(typeId, methodId);
            int line = lineAt(typeId, methodId, codeIndex);
            if (System.getenv("KOF_DEBUG_TRACE") != null) {
                System.err.println("jdwp frame type=" + typeId + " method=" + methodId
                        + " name=" + methodName + " line=" + line);
            }
            frames.add(new FrameInfo(methodId, methodName, line, codeIndex));
        }
        return frames;
    }

    private String methodName(long typeId, long methodId) throws IOException {
        // The name comes from ReferenceType.Methods (2,5). A Method.VariableTable
        // (6,2) probe used to be issued here with its reply DISCARDED: for frames
        // without debug info (native/JDK methods such as Thread.sleep) it fails
        // (101/511, measured) and aborted the whole stack trace. Never issue a
        // command whose result is not used.
        JdwpPacket req = new JdwpPacket();
        req.writeReference(typeId);
        JdwpPacket methods;
        try {
            methods = sendCommand(2, 5, req);
        } catch (IOException absent) {
            return "?"; // no method table for this frame — never abort the stack
        }
        int count = methods.readInt();
        String name = "?";
        for (int i = 0; i < count; i++) {
            long id = methods.readReference();
            String n = methods.readString();
            methods.readString();
            methods.readInt();
            if (id == methodId) {
                name = n;
                break;
            }
        }
        return name;
    }

    private int lineAt(long typeId, long methodId, long codeIndex) throws IOException {
        long[] lines;
        try {
            lines = lineTable(typeId, methodId);
        } catch (IOException absent) {
            return -1; // native/abstract frame has no LineTable — honest "unknown"
        }
        int best = -1;
        for (int i = 0; i + 1 < lines.length; i += 2) {
            if (lines[i] <= codeIndex) {
                best = (int) lines[i + 1];
            }
        }
        return best;
    }

    private void eventLoop(EventHandler handler) {
        try {
            while (true) {
                JdwpPacket evt = readPacketLocked();
                if (evt.id >= 0 && !evt.eventData) {
                    synchronized (lock) {
                        replies.put(evt.id, evt);
                        lock.notifyAll();
                    }
                    continue;
                }
                evt.readByte(); // suspendPolicy (lido p/ avançar o cursor; valor não usado)
                int eventCount = evt.readInt();
                for (int e = 0; e < eventCount; e++) {
                    // COMPOSITE (JDWP.java 7827): [kind(byte)][requestID(int)][corpo...]
                    int kind = evt.readByte();
                    evt.readInt(); // requestID
                    if (System.getenv("KOF_DEBUG_TRACE") != null) {
                        System.err.println("jdwp event kind=" + kind + " bodyLeft="
                                + evt.remaining() + " hex=" + evt.peekHex(64));
                    }
                    if (kind == 8) { // ClassPrepare: threadID, tag, typeID, signature, status
                        long threadId = evt.readReference();
                        evt.readByte();
                        long typeId = evt.readReference();
                        dispatch(handler, kind, threadId, typeId);
                    } else if (kind == 2 || kind == 1) {
                        // Breakpoint (2) / SingleStep (1): threadID + location(tag, type, method, codeIndex)
                        long threadId = evt.readReference();
                        evt.readByte();       // location tag
                        long typeId = evt.readReference();
                        evt.readReference(); // method
                        evt.readLong();      // codeIndex
                        dispatch(handler, kind, threadId, typeId);
                    } else if (kind == 4) {
                        // Exception: threadID, location(tag,type,method,codeIndex),
                        // exception(tagged value), catchLocation(tagged location).
                        long threadId = evt.readReference();
                        evt.readByte();                 // location tag
                        long typeId = evt.readReference();
                        evt.readReference();            // method
                        evt.readLong();                 // codeIndex
                        int excTag = evt.readByte();    // tagged exception object
                        if (excTag != 0) {
                            evt.readReference();
                        }
                        int catchTag = evt.readByte();  // 0 = not caught here
                        if (catchTag != 0) {
                            evt.readReference();        // catch location type
                            evt.readReference();        // method
                            evt.readLong();             // codeIndex
                        }
                        dispatch(handler, kind, threadId, typeId);
                    } else if (kind == 0) { // VMStart: threadID
                        dispatch(handler, kind, evt.readReference(), 0);
                    }
                }
            }
        } catch (IOException e) {
            handler.onDisconnect();
        } catch (Exception e) {
            e.printStackTrace();
            handler.onDisconnect();
        }
    }

    private void dispatch(EventHandler handler, int kind, long threadId, long typeId) {
        // handlers may issue JDWP commands (breakpoints, resume), which need
        // the event loop to deliver replies — run them off the loop
        Thread t = new Thread(() -> handler.onEvent(kind, threadId, typeId), "jdwp-handler");
        t.setDaemon(true);
        t.start();
    }

    interface EventHandler {
        void onEvent(int kind, long threadId, long typeId);

        default void onDisconnect() {
        }
    }

    /** package-private: o transporte e do cliente; JdwpValues decodifica o conteudo. */
    JdwpPacket sendCommand(int cmdSet, int cmd, JdwpPacket data) throws IOException {
        int myId = sendRaw(cmdSet, cmd, data);
        synchronized (lock) {
            long deadline = System.currentTimeMillis() + 15000;
            while (!replies.containsKey(myId)) {
                long wait = deadline - System.currentTimeMillis();
                if (wait <= 0) {
                    throw new IOException("JDWP timeout waiting for reply " + myId);
                }
                try {
                    lock.wait(wait);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException("interrupted", e);
                }
            }
            JdwpPacket reply = replies.remove(myId);
            if (reply.errorCode != 0) {
                throw new IOException("JDWP error " + reply.errorCode + " on cmd (" + cmdSet + "," + cmd + ")");
            }
            return reply;
        }
    }

    private int sendRaw(int cmdSet, int cmd, JdwpPacket data) throws IOException {
        synchronized (lock) {
            byte[] payload = data.toByteArray();
            int length = 11 + payload.length;
            out.writeInt(length);
            out.writeInt(idSeq);
            out.writeByte(0); // flags: none
            out.writeByte(cmdSet);
            out.writeByte(cmd);
            out.write(payload);
            out.flush();
            return idSeq++;
        }
    }

    private JdwpPacket readPacketLocked() throws IOException {
        int length = in.readInt();
        int id = in.readInt();
        int flags = in.readByte();
        if ((flags & 0xFF) == 0x80) { // reply
            int error = in.readShort();
            byte[] payload = new byte[length - 11];
            in.readFully(payload);
            JdwpPacket rp = new JdwpPacket(id, error, payload);
            rp.eventData = false;
            return rp;
        }
        in.readByte(); // cmdSet
        in.readByte(); // cmd
        byte[] payload = new byte[length - 11];
        in.readFully(payload);
        JdwpPacket ep = new JdwpPacket(id, 0, payload);
        ep.eventData = true;
        return ep;
    }

}