package dev.kof.compiler;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Second independent AVIF metadata-OBU reader (plain Java, written from
 *  AV1 5.8.1-5.8.4 + the metadata_type symbol table): the agreement oracle
 *  for slice 2g. Emits the SAME fact strings and the SAME `IMAGE:` refusal
 *  messages as the Kof face. */
final class AvifMetaJavaSupport {

    private AvifMetaJavaSupport() {}

    static List<String> javaMetaFacts(Path file) throws Exception {
        byte[] item = AvifItemsSupport.readItemJava(file, 1);
        List<String> out = new ArrayList<>();
        int pos = 0, n = item.length;
        while (pos < n) {
            int head = item[pos] & 255;
            if ((head >> 7) != 0) throw new AssertionError("IMAGE: avif item obu forbidden bits");
            int type = (head >> 3) & 15;
            int ext = (head >> 2) & 1;
            if ((head & 1) != 0) throw new AssertionError("IMAGE: avif item obu reserved bit set");
            if (((head >> 1) & 1) != 1) throw new AssertionError("IMAGE: avif item obu missing size field");
            int p = pos + 1 + ext;
            int size = 0;
            boolean sized = false;
            for (int i = 0; i < 8; i++) {
                int x = item[p++] & 255;
                size = (size << 7) | (x & 127);
                if ((x & 128) == 0) {
                    sized = true;
                    break;
                }
            }
            if (!sized) throw new AssertionError("IMAGE: avif obu size too long");
            int limit = p + size;
            if (limit > n) throw new AssertionError("IMAGE: avif obu truncated");
            if (type == 5) {
                out.add(metaFacts(item, p, limit));
            }
            pos = limit;
        }
        return out;
    }

    private static String metaFacts(byte[] b, int p, int limit) {
        int mtype = 0;
        boolean coded = false;
        for (int i = 0; i < 8; i++) {
            int x = b[p++] & 255;
            mtype = (mtype << 7) | (x & 127);
            if ((x & 128) == 0) {
                coded = true;
                break;
            }
        }
        if (!coded) throw new AssertionError("IMAGE: avif metadata type too long");
        int payloadBytes = limit - p;
        String name = "userPrivate";
        if (mtype == 0) name = "reserved";
        else if (mtype == 1) name = "hdrCll";
        else if (mtype == 2) name = "hdrMdcv";
        else if (mtype == 3) name = "scalability";
        else if (mtype == 4) name = "timecode";
        else if (mtype == 5) name = "itutT35";
        else if (mtype <= 31) name = "private";
        StringBuilder s = new StringBuilder(mtype + " " + name + " pb=" + payloadBytes);
        if (mtype == 5) {
            if (p + 1 > limit) throw new AssertionError("IMAGE: truncated avif metadata obu");
            int country = b[p++] & 255;
            boolean extended = false;
            if (country == 255) {
                if (p + 1 > limit) throw new AssertionError("IMAGE: truncated avif metadata obu");
                extended = true;
                country = (country << 8) | (b[p++] & 255);
            }
            s.append(" cc=").append(country).append(" ext=").append(extended ? 1 : 0)
             .append(" t35=").append(limit - p);
        } else if (mtype == 1) {
            if (p + 4 > limit) throw new AssertionError("IMAGE: truncated avif metadata obu");
            int cll = ((b[p] & 255) << 8) | (b[p + 1] & 255);
            int fall = ((b[p + 2] & 255) << 8) | (b[p + 3] & 255);
            s.append(" cll=").append(cll).append(" fall=").append(fall);
        } else if (mtype == 2) {
            if (p + 24 > limit) throw new AssertionError("IMAGE: truncated avif metadata obu");
            s.append(" mdcv=");
            for (int i = 0; i < 10; i++) {
                s.append(",");
                if (i < 8) {
                    s.append(((b[p] & 255) << 8) | (b[p + 1] & 255));
                    p += 2;
                } else {
                    s.append(((long) (b[p] & 255) << 24) | ((b[p + 1] & 255) << 16)
                           | ((b[p + 2] & 255) << 8) | (b[p + 3] & 255));
                    p += 4;
                }
            }
        }
        return s.toString();
    }

    static String javaMetaFactsError(Path file) {
        try {
            javaMetaFacts(file);
            return "OK";
        } catch (AssertionError e) {
            return e.getMessage();
        } catch (Exception e) {
            return "IO:" + e;
        }
    }
}
