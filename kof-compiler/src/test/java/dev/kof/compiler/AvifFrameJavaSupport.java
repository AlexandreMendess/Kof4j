package dev.kof.compiler;

import java.nio.file.Files;
import java.nio.file.Path;

/** Second independent AVIF frame reader (plain Java, written from AV1 5.5/5.9/5.9.15):
 *  the agreement oracle for slice 2d/2e (split from AvifFrameSupport by the size ratchet). */
final class AvifFrameJavaSupport {

    private AvifFrameJavaSupport() {}

    // --- second independent reader (plain Java, written from AV1 5.5/5.9) ---

    static String javaFrameFacts(Path file) throws Exception {
        byte[] item = AvifItemsSupport.readItemJava(file, 1);
        int pos = 0;
        int n = item.length;
        boolean haveSeq = false;
        int reduced = 0, maxW = 0, maxH = 0, wB = 0, hB = 0, ohBits = 0;
        int forceSct = 2, forceMv = 2, delta = 0, add = 0;
        boolean frameIds = false, superres = false, use128 = false, refMvs = false;
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
                int profile = AvifSeqSupport.readBits(item, bits, 3); bits += 3;
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
                        bits += 12;                         // op idc (f(12))
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
                use128 = AvifSeqSupport.readBits(item, bits, 1) == 1;
                bits += 3;                                  // + filter/edge
                if (reduced == 0) {
                    bits += 4;                              // inter-block caps
                    int oh = AvifSeqSupport.readBits(item, bits, 1); bits += 1;
                    if (oh == 1) {
                        AvifSeqSupport.readBits(item, bits, 1);
                        refMvs = AvifSeqSupport.readBits(item, bits + 1, 1) == 1;
                        bits += 2;                          // jnt comp + rf mvs
                    }
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
                int frameType = 0, show = 1, err = 1, sct = forceSct, ov = 0;
                if (reduced == 0) {
                    if (AvifSeqSupport.readBits(item, bits, 1) == 1) {
                        throw new AssertionError("showexisting");
                    }
                    bits += 1;
                    frameType = AvifSeqSupport.readBits(item, bits, 2); bits += 2;
                    if (frameType == 1 || frameType == 3) throw new AssertionError("inter");
                    show = AvifSeqSupport.readBits(item, bits, 1); bits += 1;
                    if (show == 0) bits += 1;
                    if (frameType == 0 && show == 1) {
                        err = 1;
                    } else {
                        err = AvifSeqSupport.readBits(item, bits, 1); bits += 1;
                    }
                }
                int cdfv = AvifSeqSupport.readBits(item, bits, 1); bits += 1;
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
                if (reduced == 0 && !(frameType == 0 && show == 1)) {
                    if (AvifSeqSupport.readBits(item, bits, 8) != 255) {
                        throw new AssertionError("refrefresh");
                    }
                    bits += 8;                              // refresh (allFrames)
                }
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
                    int ibc = AvifSeqSupport.readBits(item, bits, 1);
                    bits += 1;
                    if (ibc == 1) {
                        throw new AssertionError("intrabc");
                    }
                }
                // read_interpolation_filter / is_motion_mode_switchable /
                // use_ref_frame_mvs are read only off-intra (5.9.2); intra
                // frames (the only ones this reader accepts) skip them.
                if (reduced == 0 && cdfv == 0) bits += 1;   // disable_frame_end_update_cdf
                // tile_info() uniform path (5.9.15), counts per the loops
                int sbShift = use128 ? 5 : 4;
                int sbSize = sbShift + 2;
                int miCols = (codedW + 3) >> 2;
                int miRows = (codedH + 3) >> 2;
                int sbCols = (miCols + (1 << sbShift) - 1) >> sbShift;
                int sbRows = (miRows + (1 << sbShift) - 1) >> sbShift;
                int minCols = tileLog2(4096 >> sbSize, sbCols);
                int maxCols = tileLog2(1, Math.min(sbCols, 64));
                int maxRows = tileLog2(1, Math.min(sbRows, 64));
                int minTiles = Math.max(minCols, tileLog2(4096 * 2304 >> (2 * sbSize), sbRows * sbCols));
                if (AvifSeqSupport.readBits(item, bits, 1) == 0) {
                    throw new AssertionError("sizelist");
                }
                bits += 1;
                int colsLog2 = minCols;
                boolean stop = false;
                while (colsLog2 < maxCols && !stop) {
                    if (bits > limit * 8) throw new AssertionError("trunc");
                    if (AvifSeqSupport.readBits(item, bits, 1) == 0) {
                        stop = true;
                    } else {
                        colsLog2 += 1;
                    }
                    bits += 1;
                }
                int rowsLog2 = Math.max(minTiles - colsLog2, 0);
                stop = false;
                while (rowsLog2 < maxRows && !stop) {
                    if (bits > limit * 8) throw new AssertionError("trunc");
                    if (AvifSeqSupport.readBits(item, bits, 1) == 0) {
                        stop = true;
                    } else {
                        rowsLog2 += 1;
                    }
                    bits += 1;
                }
                if (colsLog2 > 0 || rowsLog2 > 0) {
                    bits += colsLog2 + rowsLog2;
                    bits += 2;                              // tile_size_bytes_minus_1
                }
                if (bits > limit * 8) throw new AssertionError("trunc");
                int tileWidthSb = (sbCols + (1 << colsLog2) - 1) >> colsLog2;
                int tileHeightSb = (sbRows + (1 << rowsLog2) - 1) >> rowsLog2;
                int tiles = 0;
                for (int start = 0; start < sbCols; start += tileWidthSb) tiles++;
                int trows = 0;
                for (int start = 0; start < sbRows; start += tileHeightSb) trows++;
                return "t=" + frameType + " show=" + show + " err=" + err + " ov=" + ov
                        + " w=" + codedW + " h=" + codedH + " rd=" + rd
                        + " rw=" + rW + " rh=" + rH + " tiles=" + tiles + "x" + trows;
            }
            pos = limit;
        }
        throw new AssertionError("noframe");
    }

    private static int tileLog2(int u, int v) {
        int k = 0;
        while ((u << k) < v) k++;
        return k;
    }

    static String javaFrameFactsError(Path file) throws Exception {
        try {
            return javaFrameFacts(file);
        } catch (AssertionError e) {
            return "REFUSED:" + e.getMessage();
        }
    }
}
