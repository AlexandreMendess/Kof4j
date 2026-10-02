package dev.kof.compiler;

import java.nio.file.Files;
import java.nio.file.Path;

/** Fixtures + independent second reader for AVIF slice 2d (frame header prefix). */
final class AvifFrameSupport {

    private AvifFrameSupport() {}

    /**
     * Reduced-frame prefix per AV1 5.9.2/5.9.5/5.9.6 (writer mirrors the
     * spec bit order): disable_cdf, allow_sct, [force_mv], [size override +
     * coded sizes], [superres], render flag [+16+16], [intrabc], trailing.
     * configObu(0,2,...) is 32x32 with wBits=4; sizes stay at max here.
     */
    static byte[] frameReduced(int cdf, int sct, int mv, int render, int rw16,
                               int rh16, int intrabc, int obuType) {
        AvifMetadataSupport.Bits w = new AvifMetadataSupport.Bits();
        w.bits(cdf, 1);
        w.bits(sct, 1);
        if (sct == 1) {
            w.bits(mv, 1);
        }
        w.bits(render, 1);
        if (render == 1) {
            w.bits(rw16, 16);
            w.bits(rh16, 16);
        }
        if (sct == 1) {
            w.bits(intrabc, 1);
        }
        w.bits(1, 1);
        return AvifSeqSupport.obuType(obuType, w.bytes());
    }

    /**
     * Non-reduced prefix over nrMonoObuType(8x8, SELECT sct/mv, order hint
     * off): show_existing, frame_type, show, [showable], [error_resilient],
     * disable_cdf, allow_sct, [force_mv], [refresh], ov, [4+4 coded],
     * render [+16+16], [intrabc], trailing.
     */
    static byte[] frameNr(int type2Bits, int show, int err, int cdf, int sct, int mv,
                          int ov, int codedW, int codedH, int render, int rw16,
                          int rh16, int intrabc) {
        AvifMetadataSupport.Bits w = new AvifMetadataSupport.Bits();
        w.bits(0, 1);                       // show_existing_frame = 0
        w.bits(type2Bits, 2);               // frame_type
        w.bits(show, 1);                    // show_frame
        if (show == 0) {
            w.bits(1, 1);                   // showable_frame
        }
        if (!(type2Bits == 1 && show == 1)) {
            w.bits(err, 1);                 // error_resilient_mode
        }
        w.bits(cdf, 1);
        w.bits(sct, 1);
        if (sct == 1) {
            w.bits(mv, 1);
        }
        w.bits(ov, 1);
        if (!(type2Bits == 1 && show == 1)) {
            w.bits(255, 8);                 // refresh_frame_flags (allFrames)
        }
        if (ov == 1) {
            w.bits(codedW - 1, 3);          // nrMonoObuType 8x8: wBits=2 -> 3 bits
            w.bits(codedH - 1, 3);
        }
        w.bits(render, 1);
        if (render == 1) {
            w.bits(rw16, 16);
            w.bits(rh16, 16);
        }
        if (sct == 1 && (ov == 0 || codedW == 8)) {
            w.bits(intrabc, 1);
        }
        w.bits(1, 1);
        return AvifSeqSupport.obuType(3, w.bytes());
    }

    static byte[] nrSeq() {
        return AvifSeqSupport.nrMonoObuType(1, 0, 2, false, false, false, false,
                                            false, 8, 8);
    }

    static Path fixtures(Path dir) throws Exception {
        Files.createDirectories(dir);
        Files.write(dir.resolve("red-k.avif"), AvifObuSupport.containerWithItem(
                AvifMetadataSupport.concat(java.util.List.of(
                        AvifSeqSupport.delimiter(),
                        AvifMetadataSupport.configObu(0, 2, false, false, true),
                        frameReduced(1, 1, 1, 0, 0, 0, 0, 3)))));
        Files.write(dir.resolve("nr-k.avif"), AvifObuSupport.containerWithItem(
                AvifMetadataSupport.concat(java.util.List.of(
                        AvifSeqSupport.delimiter(),
                        nrSeq(),
                        frameNr(1, 1, 1, 1, 1, 1, 1, 5, 3, 0, 0, 0, 0)))));
        Files.write(dir.resolve("rend.avif"), AvifObuSupport.containerWithItem(
                AvifMetadataSupport.concat(java.util.List.of(
                        AvifSeqSupport.delimiter(),
                        AvifMetadataSupport.configObu(0, 2, false, false, true),
                        frameReduced(1, 1, 1, 1, 10, 5, 0, 3)))));
        return dir;
    }

    static Path errorFixtures(Path dir) throws Exception {
        Files.createDirectories(dir);
        // show-existing: non-reduced first bit = 1
        AvifMetadataSupport.Bits se = new AvifMetadataSupport.Bits();
        se.bits(1, 1);
        se.bits(0, 2);
        se.bits(1, 1);
        se.bits(1, 1);
        Files.write(dir.resolve("showexisting.avif"), AvifObuSupport.containerWithItem(
                AvifMetadataSupport.concat(java.util.List.of(
                        AvifSeqSupport.delimiter(),
                        nrSeq(),
                        AvifSeqSupport.obuType(3, se.bytes())))));
        // inter frame: type bits = 0
        AvifMetadataSupport.Bits it = new AvifMetadataSupport.Bits();
        it.bits(0, 1);
        it.bits(0, 2);
        it.bits(1, 1);
        it.bits(0, 1);
        it.bits(1, 1);
        it.bits(0, 56);
        Files.write(dir.resolve("inter.avif"), AvifObuSupport.containerWithItem(
                AvifMetadataSupport.concat(java.util.List.of(
                        AvifSeqSupport.delimiter(),
                        nrSeq(),
                        AvifSeqSupport.obuType(3, it.bytes())))));
        // intra block copy: reduced, sct=1, last data bit = 1
        Files.write(dir.resolve("intrabc.avif"), AvifObuSupport.containerWithItem(
                AvifMetadataSupport.concat(java.util.List.of(
                        AvifSeqSupport.delimiter(),
                        AvifMetadataSupport.configObu(0, 2, false, false, true),
                        frameReduced(1, 1, 1, 0, 0, 0, 1, 3)))));
        // size-with-refs: INTRA_ONLY, show=1, err=0, ov=1
        Files.write(dir.resolve("sizerefs.avif"), AvifObuSupport.containerWithItem(
                AvifMetadataSupport.concat(java.util.List.of(
                        AvifSeqSupport.delimiter(),
                        nrSeq(),
                        frameNr(2, 1, 0, 1, 0, 0, 1, 5, 3, 0, 0, 0, 0)))));
        // truncated: reduced render=1 claims 32 bits in a 1-byte payload,
        // padding OBU behind keeps the reads inside the item
        AvifMetadataSupport.Bits tr = new AvifMetadataSupport.Bits();
        tr.bits(1, 1);
        tr.bits(1, 1);
        tr.bits(1, 1);
        tr.bits(1, 1);
        tr.bits(0, 4);
        Files.write(dir.resolve("trunc.avif"), AvifObuSupport.containerWithItem(
                AvifMetadataSupport.concat(java.util.List.of(
                        AvifSeqSupport.delimiter(),
                        AvifMetadataSupport.configObu(0, 2, false, false, true),
                        AvifSeqSupport.obuType(3, tr.bytes()),
                        AvifObuSupport.obuRaw(15, new byte[8], true, false, false)))));
        // no frame at all
        Files.write(dir.resolve("noframe.avif"), AvifObuSupport.containerWithItem(
                AvifMetadataSupport.concat(java.util.List.of(
                        AvifSeqSupport.delimiter(),
                        AvifMetadataSupport.configObu(0, 2, false, false, true)))));
        return dir;
    }

    static String probe(Path dir) {
        String base = dir.toString().replace('\\', '/');
        return """
            import image.AvifFrame

            String of(Bool v) {
                if (v) {
                    return "1"
                }
                return "0"
            }

            String facts(AvifFrameHeader f) {
                return "t=" + f.frameType + " show=" + of(f.showFrame)
                    + " err=" + of(f.errorResilient) + " ov=" + of(f.sizeOverride)
                    + " w=" + f.codedWidth + " h=" + f.codedHeight
                    + " rd=" + of(f.renderDifferent) + " rw=" + f.renderWidth
                    + " rh=" + f.renderHeight
            }

            main() {
                var base = "%s"
                println("red-k " + facts(readAvifFrameHeader(base + "/red-k.avif")))
                println("nr-k " + facts(readAvifFrameHeader(base + "/nr-k.avif")))
                println("rend " + facts(readAvifFrameHeader(base + "/rend.avif")))
            }
            """.formatted(base);
    }

    static String errorProbe(Path dir) {
        String base = dir.toString().replace('\\', '/');
        return """
            import image.AvifFrame

            main() {
                var base = "%s"
                try {
                    readAvifFrameHeader(base + "/showexisting.avif")
                } catch (String e) {
                    println(e)
                }
                try {
                    readAvifFrameHeader(base + "/inter.avif")
                } catch (String e) {
                    println(e)
                }
                try {
                    readAvifFrameHeader(base + "/intrabc.avif")
                } catch (String e) {
                    println(e)
                }
                try {
                    readAvifFrameHeader(base + "/sizerefs.avif")
                } catch (String e) {
                    println(e)
                }
                try {
                    readAvifFrameHeader(base + "/trunc.avif")
                } catch (String e) {
                    println(e)
                }
                try {
                    readAvifFrameHeader(base + "/noframe.avif")
                } catch (String e) {
                    println(e)
                }
            }
            """.formatted(base);
    }

    // --- second independent reader (plain Java, written from AV1 5.5/5.9) ---

    static String javaFrameFacts(Path file) throws Exception {
        byte[] item = AvifItemsSupport.readItemJava(file, 1);
        int pos = 0;
        int n = item.length;
        boolean haveSeq = false;
        int reduced = 0, maxW = 0, maxH = 0, wB = 0, hB = 0, ohBits = 0;
        int forceSct = 2, forceMv = 2, delta = 0, add = 0;
        boolean frameIds = false, superres = false;
        while (pos < n) {
            int head = item[pos] & 255;
            if ((head >> 7) != 0) throw new AssertionError("forbidden");
            int type = (head >> 3) & 15;
            int ext = (head >> 2) & 1;
            if (((head >> 1) & 1) != 1) throw new AssertionError("nosize");
            if ((head & 1) != 0) throw new AssertionError("resbit");
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
            if (!sized) throw new AssertionError("size");
            int limit = p + size;
            if (limit > n) throw new AssertionError("obutrunc");
            if (type == 1 && !haveSeq) {
                int bits = p * 8;
                int profile = AvifSeqSupport.readBits(item, bits, 2); bits += 2;
                if (profile > 2) throw new AssertionError("prof3");
                bits += 1;                                  // still_picture
                reduced = AvifSeqSupport.readBits(item, bits, 1); bits += 1;
                if (reduced == 1) {
                    bits += 5;                              // seq_level_idx
                } else {
                    int timing = AvifSeqSupport.readBits(item, bits, 1); bits += 1;
                    if (timing == 1) throw new AssertionError("timing");
                    int delay = AvifSeqSupport.readBits(item, bits, 1); bits += 1;
                    int cnt = AvifSeqSupport.readBits(item, bits, 5); bits += 5;
                    for (int i = 0; i <= cnt; i++) {
                        bits += 16;                         // op idc
                        int lv = AvifSeqSupport.readBits(item, bits, 5); bits += 5;
                        if (lv > 7) bits += 1;              // seq_tier
                        if (delay == 1) bits += 1;          // per-op delay flag
                    }
                }
                wB = AvifSeqSupport.readBits(item, bits, 4) + 1; bits += 4;
                hB = AvifSeqSupport.readBits(item, bits, 4) + 1; bits += 4;
                maxW = AvifSeqSupport.readBits(item, bits, wB) + 1; bits += wB;
                maxH = AvifSeqSupport.readBits(item, bits, hB) + 1; bits += hB;
                if (reduced == 0) {
                    frameIds = AvifSeqSupport.readBits(item, bits, 1) == 1; bits += 1;
                    if (frameIds) {
                        delta = AvifSeqSupport.readBits(item, bits, 4); bits += 4;
                        add = AvifSeqSupport.readBits(item, bits, 3); bits += 3;
                    }
                }
                bits += 3;                                  // 128/filter/edge
                if (reduced == 0) {
                    bits += 4;                              // inter-block caps
                    int oh = AvifSeqSupport.readBits(item, bits, 1); bits += 1;
                    if (oh == 1) bits += 2;                 // jnt comp + rf mvs
                    int csct = AvifSeqSupport.readBits(item, bits, 1); bits += 1;
                    if (csct == 0) {
                        forceSct = AvifSeqSupport.readBits(item, bits, 1);
                        bits += 1;
                    } else {
                        forceSct = 2;
                    }
                    if (forceSct > 0) {
                        int cmv = AvifSeqSupport.readBits(item, bits, 1); bits += 1;
                        if (cmv == 0) {
                            forceMv = AvifSeqSupport.readBits(item, bits, 1);
                            bits += 1;
                        } else {
                            forceMv = 2;
                        }
                    }
                    if (oh == 1) {
                        ohBits = AvifSeqSupport.readBits(item, bits, 3) + 1;
                        bits += 3;
                    }
                }
                superres = AvifSeqSupport.readBits(item, bits, 1) == 1;
                bits += 3;                                  // + cdef/restoration
                haveSeq = true;
            } else if ((type == 3 || type == 6) && haveSeq) {
                int bits = p * 8;
                int frameType = 1, show = 1, err = 1, sct = forceSct, ov = 0;
                if (reduced == 0) {
                    if (AvifSeqSupport.readBits(item, bits, 1) == 1) {
                        throw new AssertionError("showexisting");
                    }
                    bits += 1;
                    frameType = AvifSeqSupport.readBits(item, bits, 2); bits += 2;
                    if (frameType == 0 || frameType == 3) throw new AssertionError("inter");
                    show = AvifSeqSupport.readBits(item, bits, 1); bits += 1;
                    if (show == 0) bits += 1;
                    if (frameType == 1 && show == 1) {
                        err = 1;
                    } else {
                        err = AvifSeqSupport.readBits(item, bits, 1); bits += 1;
                    }
                }
                bits += 1;                                  // disable_cdf_update
                if (forceSct == 2) {
                    sct = AvifSeqSupport.readBits(item, bits, 1); bits += 1;
                }
                if (sct == 1 && forceMv == 2) {
                    bits += 1;                              // force_integer_mv
                }
                if (frameIds) {
                    bits += delta + add + 3;
                }
                if (reduced == 0) {
                    ov = AvifSeqSupport.readBits(item, bits, 1); bits += 1;
                }
                bits += ohBits;                             // order_hint
                if (reduced == 0 && !(frameType == 1 && show == 1)) {
                    if (AvifSeqSupport.readBits(item, bits, 8) != 255) {
                        throw new AssertionError("refrefresh");
                    }
                    bits += 8;                              // refresh (allFrames)
                }
                if (ov == 1 && err == 0) throw new AssertionError("sizerefs");
                int codedW = maxW, codedH = maxH;
                if (ov == 1) {
                    codedW = AvifSeqSupport.readBits(item, bits, wB) + 1; bits += wB;
                    codedH = AvifSeqSupport.readBits(item, bits, hB) + 1; bits += hB;
                }
                if (superres) {
                    if (AvifSeqSupport.readBits(item, bits, 1) == 1) {
                        throw new AssertionError("superres");
                    }
                    bits += 1;
                }
                int rd = AvifSeqSupport.readBits(item, bits, 1); bits += 1;
                int rW = codedW, rH = codedH;
                if (rd == 1) {
                    rW = AvifSeqSupport.readBits(item, bits, 16) + 1; bits += 16;
                    rH = AvifSeqSupport.readBits(item, bits, 16) + 1; bits += 16;
                }
                if (bits > limit * 8) throw new AssertionError("trunc");
                if (sct == 1 && codedW == maxW) {
                    if (AvifSeqSupport.readBits(item, bits, 1) == 1) {
                        throw new AssertionError("intrabc");
                    }
                }
                return "t=" + frameType + " show=" + show + " err=" + err + " ov=" + ov
                        + " w=" + codedW + " h=" + codedH + " rd=" + rd
                        + " rw=" + rW + " rh=" + rH;
            }
            pos = limit;
        }
        throw new AssertionError("noframe");
    }

    static String javaFrameFactsError(Path file) throws Exception {
        try {
            return javaFrameFacts(file);
        } catch (AssertionError e) {
            return "REFUSED:" + e.getMessage();
        }
    }
}
