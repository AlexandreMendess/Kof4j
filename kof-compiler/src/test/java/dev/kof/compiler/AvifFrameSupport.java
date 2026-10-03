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
    /**
     * Reduced-frame prefix per AV1 5.9.2/5.9.5/5.9.6/5.9.10/5.9.15 (writer
     * mirrors the spec bit order, no guards where the spec has none):
     * disable_cdf, allow_sct, [force_mv], [size override + coded sizes],
     * render flag [+16+16], [intrabc], is_filter_switchable,
     * is_motion_mode_switchable, uniform_tile_spacing_flag,
     * [inc_tile_cols_log2, inc_tile_rows_log2], trailing one bit.
     * configObu(0,2,...) is 32x32 (sbCols=sbRows=1 -> maxCols=maxRows=1,
     * minCols=0, minTiles=0): the single loop iteration reads a STOP bit;
     * a 1 there reaches the cap (no further stop). redSeq128() (128x128,
     * sbCols=8) allows incs up to 3 with stops at the cap ends.
     */
    static byte[] frameReduced(int cdf, int sct, int mv, int ov, int codedW, int codedH,
                               int render, int rw16, int rh16, int intrabc, int obuType,
                               int uniform, int incCols, int stopCols,
                               int incRows, int stopRows) {
        AvifMetadataSupport.Bits w = new AvifMetadataSupport.Bits();
        w.bits(cdf, 1);
        w.bits(sct, 1);
        if (sct == 1) {
            w.bits(mv, 1);
        }
        // 5.9.2: reduced_still_picture_header INFERS frame_size_override_flag
        // = 0 (no bit); SWITCH infers 1; only other forms read it.
        w.bits(render, 1);
        if (render == 1) {
            w.bits(rw16, 16);
            w.bits(rh16, 16);
        }
        if (sct == 1) {
            w.bits(intrabc, 1);
        }
        w.bits(1, 1);                       // is_filter_switchable (5.9.10)
        w.bits(1, 1);                       // is_motion_mode_switchable
        if (uniform >= 0) {
            w.bits(uniform, 1);
            if (uniform == 1) {
                for (int i = 0; i < incCols; i++) {
                    w.bits(1, 1);
                }
                if (stopCols == 1) {
                    w.bits(0, 1);                           // cols loop stop (only if cap not reached)
                }
                for (int i = 0; i < incRows; i++) {
                    w.bits(1, 1);
                }
                if (stopRows == 1) {
                    w.bits(0, 1);                           // rows loop stop
                }
                if (incCols + incRows > 0) {
                    w.bits(0, 2);                           // context_update_tile_id
                    w.bits(0, 2);                           // tile_size_bytes_minus_1
                }
            }
        }
        w.bits(1, 1);
        return AvifSeqSupport.obuType(obuType, w.bytes());
    }

    /**
     * Non-reduced prefix over nrMonoObuType(8x8, SELECT sct/mv, order hint
     * off, ref-frame-MV off): show_existing, frame_type, show, [showable],
     * [error_resilient], disable_cdf, allow_sct, [force_mv], [ids], ov,
     * [refresh], [4+4 coded], render [+16+16], [intrabc], switchable,
     * motion, [frame-end cdf], uniform [+incs], trailing.
     * nr8: sbCols=sbRows=2 -> minCols=0, maxCols=maxRows=1, minTiles=0.
     */
    static byte[] frameNr(int type2Bits, int show, int err, int cdf, int sct, int mv,
                          int ov, int codedW, int codedH, int render, int rw16,
                          int rh16, int intrabc, int uniform, int incCols) {
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
        w.bits(1, 1);                       // is_filter_switchable (5.9.10)
        w.bits(1, 1);                       // is_motion_mode_switchable
        if (cdf == 0) {
            w.bits(0, 1);                   // disable_frame_end_update_cdf = 0
        }
        if (uniform >= 0) {
            w.bits(uniform, 1);
            for (int i = 0; i < incCols; i++) {
                w.bits(1, 1);
            }
        }
        w.bits(1, 1);
        return AvifSeqSupport.obuType(3, w.bytes());
    }

    /** Reduced OBU_SEQUENCE_HEADER with 128x128 max frame (wBits/hBits=6) —
     *  the 32x32 default gives sbCols=sbRows=1, so a multi-tile fixture
     *  needs >= 65 coded samples per axis (spec 5.9.15 loops). */
    static byte[] redSeq128() {
        AvifMetadataSupport.Bits w = new AvifMetadataSupport.Bits();
        w.bits(0, 3);                       // seq_profile
        w.bits(1, 1);                       // still_picture
        w.bits(1, 1);                       // reduced_still_picture_header
        w.bits(2, 5);                       // seq_level_idx
        w.bits(6, 4);                       // frame_width_bits_minus_1
        w.bits(6, 4);                       // frame_height_bits_minus_1
        w.bits(127, 7);                     // max_frame_width_minus_1 (128)
        w.bits(127, 7);                     // max_frame_height_minus_1 (128)
        w.bits(0, 3);                       // 128x128 / filter_intra / intra_edge
        w.bits(0, 3);                       // superres / cdef / restoration
        w.bits(0, 1);                       // high_bitdepth
        w.bits(1, 1);                       // mono_chrome (profile 0)
        w.bits(0, 1);                       // color_description_present
        w.bits(0, 1);                       // color_range (mono)
        w.bits(0, 1);                       // film_grain_params_present
        w.bits(1, 1);                       // trailing one bit
        byte[] payload = w.bytes();
        byte[] out = new byte[2 + payload.length];
        out[0] = 0x0A;
        out[1] = (byte) payload.length;
        System.arraycopy(payload, 0, out, 2, payload.length);
        return out;
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
                        frameReduced(1, 1, 1, 0, 0, 0, 0, 0, 0, 0, 3, 1, 0, 1, 0, 1)))));
        Files.write(dir.resolve("nr-k.avif"), AvifObuSupport.containerWithItem(
                AvifMetadataSupport.concat(java.util.List.of(
                        AvifSeqSupport.delimiter(),
                        nrSeq(),
                        frameNr(1, 1, 1, 1, 1, 1, 1, 5, 3, 0, 0, 0, 0, 1, 0)))));
        Files.write(dir.resolve("rend.avif"), AvifObuSupport.containerWithItem(
                AvifMetadataSupport.concat(java.util.List.of(
                        AvifSeqSupport.delimiter(),
                        AvifMetadataSupport.configObu(0, 2, false, false, true),
                        frameReduced(1, 1, 1, 0, 0, 0, 1, 10, 5, 0, 3, 1, 0, 1, 0, 1)))));
        Files.write(dir.resolve("tile4.avif"), AvifObuSupport.containerWithItem(
                AvifMetadataSupport.concat(java.util.List.of(
                        AvifSeqSupport.delimiter(),
                        redSeq128(),
                        frameReduced(1, 1, 1, 0, 0, 0, 0, 0, 0, 0, 3, 1, 1, 0, 1, 0)))));
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
                        frameReduced(1, 1, 1, 0, 0, 0, 0, 0, 0, 1, 3, 1, 0, 1, 0, 1)))));
        // size-with-refs: INTRA_ONLY, show=1, err=0, ov=1
        Files.write(dir.resolve("sizerefs.avif"), AvifObuSupport.containerWithItem(
                AvifMetadataSupport.concat(java.util.List.of(
                        AvifSeqSupport.delimiter(),
                        nrSeq(),
                        frameNr(2, 1, 0, 1, 0, 0, 1, 5, 3, 0, 0, 0, 0, 1, 0)))));
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
        // tile size list: reduced KEY, uniform_tile_spacing_flag = 0
        Files.write(dir.resolve("sizelist.avif"), AvifObuSupport.containerWithItem(
                AvifMetadataSupport.concat(java.util.List.of(
                        AvifSeqSupport.delimiter(),
                        AvifMetadataSupport.configObu(0, 2, false, false, true),
                        frameReduced(1, 1, 1, 0, 0, 0, 0, 0, 0, 0, 3, 0, 0, 0, 0, 0)))));
        // frame-level interpolation filter: is_filter_switchable = 0
        AvifMetadataSupport.Bits ip = new AvifMetadataSupport.Bits();
        ip.bits(1, 1);                      // disable_cdf
        ip.bits(1, 1);                      // allow_sct
        ip.bits(1, 1);                      // force_integer_mv
        ip.bits(0, 1);                      // render = same
        ip.bits(0, 1);                      // allow_intrabc = 0
        ip.bits(0, 1);                      // is_filter_switchable = 0
        ip.bits(0, 2);                      // interpolation_filter (refused)")
        ip.bits(0, 0);
        Files.write(dir.resolve("interp.avif"), AvifObuSupport.containerWithItem(
                AvifMetadataSupport.concat(java.util.List.of(
                        AvifSeqSupport.delimiter(),
                        AvifMetadataSupport.configObu(0, 2, false, false, true),
                        AvifSeqSupport.obuType(3, ip.bytes())))));
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
                    + " tiles=" + f.tileColumns + "x" + f.tileRows
            }

            main() {
                var base = "%s"
                println("red-k " + facts(readAvifFrameHeader(base + "/red-k.avif")))
                println("nr-k " + facts(readAvifFrameHeader(base + "/nr-k.avif")))
                println("rend " + facts(readAvifFrameHeader(base + "/rend.avif")))
                println("tile4 " + facts(readAvifFrameHeader(base + "/tile4.avif")))
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
                try {
                    readAvifFrameHeader(base + "/sizelist.avif")
                } catch (String e) {
                    println(e)
                }
                try {
                    readAvifFrameHeader(base + "/interp.avif")
                } catch (String e) {
                    println(e)
                }
            }
            """.formatted(base);
    }
}
