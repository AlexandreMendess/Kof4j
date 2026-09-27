package dev.kof.cli;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Decodificacao de VALORES do protocolo JDWP (X7-5 split mecanico, regra 7):
 * o nome diz a responsabilidade — variaveis locais de um frame (Method.
 * VariableTable + StackFrame.GetValues) e conteudo de StringReference.
 * Formato (tag -> tam. de valor) copiado de JDWP.java/PacketStream.java da
 * propria JDK 25 (implementacao de referencia do HotSpot). O transporte
 * (sendCommand) fica no JdwpClient; aqui so wire-format e semantica de valor.
 */
final class JdwpValues {

    private final JdwpClient client;

    JdwpValues(JdwpClient client) {
        this.client = client;
    }

    /**
     * Variveis locais reais de um frame: Method.VariableTable (6,2) filtra por
     * visibilidade no codeIndex, StackFrame.GetValues (16,1) le os valores.
     */
    List<Object[]> locals(JdwpClient.FullFrame frame) throws IOException {
        JdwpPacket vt = new JdwpPacket();
        vt.writeReference(frame.typeId());
        vt.writeReference(frame.methodId());
        JdwpPacket reply = client.sendCommand(6, 2, vt); // Method.VariableTable
        // JDK 25 (codigo real do JDWP.java da propria JDK): a resposta de
        // VariableTable = {int argCnt (CONTAGEM DE PALAVRAS dos args, long/double
        // contam 2), int slotCount, slots[]}. NAO ha lista de argumentos aqui —
        // ler uma lista fantasma estourava o pacote. argCnt e so consumido para
        // posicionar o cursor do pacote (o filtro de slots usa o codeIndex).
        reply.readInt();
        int slotCount = reply.readInt();
        List<long[]> slotPos = new ArrayList<>();   // {slot, start, end}
        List<String> slotName = new ArrayList<>();
        List<String> slotSig = new ArrayList<>();
        for (int v = 0; v < slotCount; v++) {
            long start = reply.readLong();          // codeIndex e LONG no JDK 25 (medido)
            String name = reply.readString();
            String sig = reply.readString();
            int len = reply.readInt();
            int slot = reply.readInt();
            if (start <= frame.codeIndex() && frame.codeIndex() < start + len) {
                slotPos.add(new long[]{slot, start, len});
                slotName.add(name);
                slotSig.add(sig);
            }
        }
        if (slotPos.isEmpty()) {
            return List.of();
        }
        List<Object[]> out = new ArrayList<>();
        try {
            JdwpPacket gv = new JdwpPacket();
            gv.writeReference(frame.threadId());
            gv.writeLong(frame.frameId());
            gv.writeInt(slotPos.size());
            for (int i = 0; i < slotPos.size(); i++) {
                gv.writeInt((int) slotPos.get(i)[0]);
                gv.writeByte(sigByte(slotSig.get(i)));
            }
            JdwpPacket vals = client.sendCommand(16, 1, gv); // StackFrame.GetValues
            int n = vals.readInt();
            for (int i = 0; i < n; i++) {
                out.add(new Object[]{slotName.get(i), slotSig.get(i), readTaggedValue(vals, vals.readByte())});
            }
            return out;
        } catch (IOException batchFailed) {
            // Um slot "visivel" mas ainda NAO armazenado (ex.: a declaracao esta
            // na propria linha do breakpoint) faz o GetValues em LOTE inteiro
            // falhar com INVALID_SLOT (35, medido). Le cada slot sozinho e
            // mantem apenas os legiveis: um local que nao da para ler e OMITIDO,
            // nunca inventado (R6). O compilador emite ranges Start=0 para todos
            // os locais hoje (catalogado §385) — quando os ranges ficarem exatos
            // o lote simplesmente nao falha mais.
            for (int i = 0; i < slotPos.size(); i++) {
                try {
                    out.add(readOne(frame, (int) slotPos.get(i)[0], slotSig.get(i), slotName.get(i)));
                } catch (IOException unreadable) {
                    // local declarado mas sem valor neste pc — fora da lista
                }
            }
            return out;
        }
    }

    private Object[] readOne(JdwpClient.FullFrame frame, int slot, String sig, String name) throws IOException {
        JdwpPacket gv = new JdwpPacket();
        gv.writeReference(frame.threadId());
        gv.writeLong(frame.frameId());
        gv.writeInt(1);
        gv.writeInt(slot);
        gv.writeByte(sigByte(sig));
        JdwpPacket vals = client.sendCommand(16, 1, gv); // StackFrame.GetValues
        int n = vals.readInt();
        if (n < 1) {
            throw new IOException("no value for slot " + slot);
        }
        return new Object[]{name, sig, readTaggedValue(vals, vals.readByte())};
    }

    /** StringReference.Value (10,1) — conteudo de um java.lang.String para exibicao. */
    String stringValue(long objectRef) throws IOException {
        JdwpPacket req = new JdwpPacket();
        req.writeReference(objectRef);
        JdwpPacket reply = client.sendCommand(10, 1, req);
        return reply.readString();
    }

    static int sigByte(String signature) {
        if (signature.isEmpty()) {
            return 'I';
        }
        char c = signature.charAt(0);
        return c == 'L' ? 'l' : c;
    }

    static Object readTaggedValue(JdwpPacket p, int tag) throws IOException {
        return switch (tag) {
            case 'Z' -> p.readByte() != 0;                       // boolean = 1 byte
            case 'B' -> (int) p.readByte();
            case 'S' -> (int) p.readShort();
            case 'C' -> (int) p.readShort();
            case 'I', 'F' -> p.readInt();
            case 'J', 'D' -> p.readLong();
            case 'l', '[' -> p.readReference();
            default -> {
                p.readReference();
                yield 0L;
            }
        };
    }
}
