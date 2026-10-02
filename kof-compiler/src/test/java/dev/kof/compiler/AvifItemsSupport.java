package dev.kof.compiler;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Fixtures + independent second reader for AVIF slice 2a (item location). */
final class AvifItemsSupport {

    private AvifItemsSupport() {}

    static byte[] colorBytes() {
        byte[] a = new byte[40];
        for (int i = 0; i < a.length; i++) {
            a[i] = (byte) ((i * 7) % 251);
        }
        return a;
    }

    static byte[] alphaBytes() {
        byte[] a = new byte[20];
        for (int i = 0; i < a.length; i++) {
            a[i] = (byte) (((200 - 3 * i) % 256) & 255);
        }
        return a;
    }

    static String fact(byte[] a) {
        long sum = 0;
        for (byte x : a) {
            sum += x & 255;
        }
        return "len=" + a.length + " first=" + (a[0] & 255)
                + " last=" + (a[a.length - 1] & 255) + " sum=" + sum;
    }

    /** iloc full-box, entries (id, construction, base, offset, length). */
    static byte[] iloc(int ver, List<int[]> entries) {
        int n = entries.size();
        byte[] body = new byte[9 + n * 18];
        body[0] = (byte) ver;
        body[4] = 0x44;
        body[5] = 0x40;
        body[7] = (byte) (n >>> 8);
        body[8] = (byte) n;
        int p = 9;
        for (int[] e : entries) {
            p = put16(body, p, e[0]);
            p = put16(body, p, (e[1] & 7) << 13);
            p = put16(body, p, 1);
            p = put32(body, p, e[2]);
            p = put32(body, p, e[3]);
            p = put32(body, p, e[4]);
        }
        return AvifMetadataSupport.box("iloc", java.util.Arrays.copyOf(body, p));
    }

    private static int put16(byte[] b, int p, int v) {
        b[p] = (byte) (v >>> 8);
        b[p + 1] = (byte) v;
        return p + 2;
    }

    private static int put32(byte[] b, int p, int v) {
        b[p] = (byte) (v >>> 24);
        b[p + 1] = (byte) (v >>> 16);
        b[p + 2] = (byte) (v >>> 8);
        b[p + 3] = (byte) v;
        return p + 4;
    }

    static byte[] ftyp() {
        return AvifMetadataSupport.box("ftyp", AvifMetadataSupport.concat(List.of(
                "avif".getBytes(StandardCharsets.US_ASCII),
                new byte[]{0, 0, 0, 0},
                "avif".getBytes(StandardCharsets.US_ASCII))));
    }

    static byte[] meta(List<byte[]> children) {
        return AvifMetadataSupport.box("meta", AvifMetadataSupport.concatWith(
                new byte[]{0, 0, 0, 0}, children));
    }

    static byte[] baseMeta(int itemCount, List<byte[]> extra) {
        byte[] infe1 = AvifMetadataSupport.infe(1, "av01");
        byte[] iinf;
        if (itemCount == 2) {
            iinf = AvifMetadataSupport.iinf(2,
                    List.of(infe1, AvifMetadataSupport.infe(2, "av01")));
        } else {
            iinf = AvifMetadataSupport.iinf(1, List.of(infe1));
        }
        var children = new java.util.ArrayList<byte[]>();
        children.add(AvifMetadataSupport.pitm(1));
        children.add(iinf);
        children.add(AvifMetadataSupport.hdlr());
        children.addAll(extra);
        return meta(children);
    }

    static Path fixtures(Path dir) throws Exception {
        Files.createDirectories(dir);
        byte[] color = colorBytes();
        byte[] alpha = alphaBytes();

        // mdat.avif — construction 0, both items inside mdat (absolute).
        byte[] f = ftyp();
        byte[] mdat = AvifMetadataSupport.box("mdat",
                AvifMetadataSupport.concat(List.of(color, alpha)));
        byte[] ilocA = iloc(1, List.of(
                new int[]{1, 0, 0, 0, color.length},
                new int[]{2, 0, 0, 0, alpha.length}));
        byte[] metaA = baseMeta(2, List.of(ilocA));
        int payloadAt = f.length + metaA.length + 8;
        metaA = baseMeta(2, List.of(iloc(1, List.of(
                new int[]{1, 0, 0, payloadAt, color.length},
                new int[]{2, 0, 0, payloadAt + color.length, alpha.length}))));
        Files.write(dir.resolve("mdat.avif"),
                AvifMetadataSupport.concat(List.of(f, metaA, mdat)));

        // idat.avif — construction 1, item 1 relative to the idat payload.
        byte[] idat = AvifMetadataSupport.box("idat", color);
        byte[] metaB = baseMeta(1, List.of(iloc(1, List.of(
                new int[]{1, 1, 0, 0, color.length}))));
        Files.write(dir.resolve("idat.avif"),
                AvifMetadataSupport.concat(List.of(f, metaB, idat)));

        // trunc.avif — extent points beyond the read prefix.
        byte[] metaC = baseMeta(1, List.of(iloc(1, List.of(
                new int[]{1, 0, 0, 100000, 10}))));
        Files.write(dir.resolve("trunc.avif"),
                AvifMetadataSupport.concat(List.of(f, metaC,
                        AvifMetadataSupport.box("mdat", alpha))));

        // v2.avif — iloc version 2.
        byte[] metaD = baseMeta(1, List.of(iloc(2, List.of(
                new int[]{1, 0, 0, 0, 4}))));
        Files.write(dir.resolve("v2.avif"),
                AvifMetadataSupport.concat(List.of(f, metaD, mdat)));

        // noloc.avif — no iloc at all.
        Files.write(dir.resolve("noloc.avif"),
                AvifMetadataSupport.concat(List.of(f, baseMeta(1, List.of()), mdat)));
        return dir;
    }

    // --- second independent reader (plain Java, written from ISO 14496-12) --

    static byte[] readItemJava(Path file, int itemId) throws Exception {
        byte[] b = Files.readAllBytes(file);
        int meta = AvifMetadataSupport.findBox(b, 0, b.length, "meta");
        assertTrue(meta >= 0, "meta");
        int iloc = AvifMetadataSupport.findBox(b, meta + 12,
                AvifMetadataSupport.boxEnd(b, meta), "iloc");
        assertTrue(iloc >= 0, "iloc");
        assertTrue((b[iloc + 8] & 255) == 1, "iloc v1");
        assertTrue((b[iloc + 12] & 255) == 0x44, "offset/length size 4");
        assertTrue((b[iloc + 13] & 255) == 0x40, "base size 4");
        assertTrue((b[iloc + 14] & 255) == 0, "index size 0");
        int count = AvifMetadataSupport.be16(b, iloc + 15);
        int p = iloc + 17;
        for (int i = 0; i < count; i++) {
            int id = AvifMetadataSupport.be16(b, p);
            int word = AvifMetadataSupport.be16(b, p + 2);
            int ec = AvifMetadataSupport.be16(b, p + 4);
            int base = AvifMetadataSupport.be32(b, p + 6);
            int off = AvifMetadataSupport.be32(b, p + 10);
            int len = AvifMetadataSupport.be32(b, p + 14);
            if (id == itemId) {
                int constr = word >> 13;
                int at = constr == 1
                        ? AvifMetadataSupport.findBox(b, 0, b.length, "idat") + 8 + base + off
                        : base + off;
                assertTrue(ec == 1, "single extent");
                byte[] out = new byte[len];
                System.arraycopy(b, at, out, 0, len);
                return out;
            }
            p = p + 6 + 4 + ec * 8;
        }
        throw new AssertionError("item not found");
    }

    private static void assertTrue(boolean v, String m) {
        org.junit.jupiter.api.Assertions.assertTrue(v, m);
    }

    static String itemProbe(Path dir) {
        String base = dir.toString().replace('\\', '/');
        return """
            import image.AvifItems

            String fact(Int[] a) {
                Int sum = 0
                for (var x in a) {
                    sum = sum + x
                }
                return "len=" + a.length + " first=" + a[0]
                    + " last=" + a[a.length - 1] + " sum=" + sum
            }

            main() {
                var base = "%s"
                println("mdat1 " + fact(readAvifItemBytes(base + "/mdat.avif", 1)))
                println("mdat2 " + fact(readAvifItemBytes(base + "/mdat.avif", 2)))
                println("idat1 " + fact(readAvifItemBytes(base + "/idat.avif", 1)))
            }
            """.formatted(base);
    }

    static String errorProbe(Path dir) {
        String base = dir.toString().replace('\\', '/');
        return """
            import image.AvifItems

            main() {
                var base = "%s"
                try {
                    readAvifItemBytes(base + "/trunc.avif", 1)
                } catch (String e) {
                    println(e)
                }
                try {
                    readAvifItemBytes(base + "/v2.avif", 1)
                } catch (String e) {
                    println(e)
                }
                try {
                    readAvifItemBytes(base + "/noloc.avif", 1)
                } catch (String e) {
                    println(e)
                }
                try {
                    readAvifItemBytes(base + "/mdat.avif", 9)
                } catch (String e) {
                    println(e)
                }
            }
            """.formatted(base);
    }
}
