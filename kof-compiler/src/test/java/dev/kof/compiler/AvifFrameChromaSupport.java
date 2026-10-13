package dev.kof.compiler;

/**
 * Frame/sequence fixtures for a NON-MONOCHROME + NON-LOSSLESS reduced frame —
 * the shape the frame-assembly glue needs and no other fixture in this suite
 * provides. Every other sequence writer here emits `mono_chrome = 1`, and the
 * one non-lossless fixture (`q32`) is monochrome.
 *
 * <p>Split out of {@code AvifFrameSupport} (which crossed the size ratchet) so
 * each class stays inside it.
 */
final class AvifFrameChromaSupport {

    private AvifFrameChromaSupport() {}

    /**
     * `color_config` + the sequence tail for a NON-monochrome 4:2:0 profile-0
     * sequence (AV1 5.5.2, libaom `av1_read_color_config`). The existing writers
     * all emit `mono_chrome = 1`, so no fixture in this suite has chroma at all —
     * which is why the frame-assembly glue (which needs the chroma planes) had no
     * stream to be tested against. For profile 0 with
     * `color_description_present = 0` the walk is: `color_range` f(1),
     * `chroma_sample_position` f(2) (read only when both subsamplings are 1),
     * `separate_uv_delta_q` f(1) — the last bit of `color_config`.
     */
    static void colorConfig420(AvifMetadataSupport.Bits w, int colorRange,
                               int chromaSamplePos, int separateUvDeltaQ) {
        w.bits(0, 1);                       // mono_chrome = 0 (profile 0)
        w.bits(0, 1);                       // color_description_present = 0
        w.bits(colorRange, 1);              // color_range
        // profile 0 => subsampling_x = subsampling_y = 1 (no bits)
        w.bits(chromaSamplePos, 2);         // chroma_sample_position
        w.bits(separateUvDeltaQ, 1);        // separate_uv_delta_q
    }

    /**
     * The `sequence_header_obu` for a reduced still picture, 128x128, profile 0,
     * NON-monochrome 4:2:0 — the same header `redSeq128()` writes but with a real
     * chroma plane. `enable_cdef`/`enable_restoration` stay 0, so the frame tail
     * skips the cdef and lr blocks entirely.
     */
    static byte[] redSeq128Color() {
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
        colorConfig420(w, 0, 0, 0);         // NON-monochrome 4:2:0
        w.bits(0, 1);                       // film_grain_params_present
        w.bits(1, 1);                       // trailing one bit
        byte[] payload = w.bytes();
        byte[] out = new byte[2 + payload.length];
        out[0] = 0x0A;
        out[1] = (byte) payload.length;
        System.arraycopy(payload, 0, out, 2, payload.length);
        return out;
    }

    /**
     * The NON-LOSSLESS frame tail (AV1 5.9.2 + libaom `read_uncompressed_header`
     * / `setup_loopfilter`), in libaom's exact order, for a reduced still frame
     * with `numPlanes` planes, `enable_cdef`/`enable_restoration` both 0 and
     * `separate_uv_delta_q = 0`. Every optional block is written explicitly with
     * its "absent" value so the bit positions are pinned:
     *
     *   base_q_idx f(8); y_dc_delta_q flag; diff_uv_delta flag; u_dc_delta_q
     *   flag; u_ac_delta_q flag (V deltas are NOT read when diff_uv_delta == 0);
     *   using_qmatrix; segmentation_enabled; delta_q_present (read only because
     *   base_q_idx != 0 — and delta_lf is then not read);
     *   filter_level[0] f(6), filter_level[1] f(6), sharpness_level f(3),
     *   mode_ref_delta_enabled (no u/v filter levels because both are 0);
     *   tx_mode f(1); reduced_tx_set f(1); trailing one bit.
     */
    static void tailNonLossless(AvifMetadataSupport.Bits w, int baseQ, int numPlanes) {
        w.bits(baseQ, 8);       // base_q_idx
        w.bits(0, 1);           // DeltaQYDc coded flag = 0
        if (numPlanes > 1) {
            w.bits(0, 1);       // diff_uv_delta = 0  -> the V deltas are not read
            w.bits(0, 1);       // DeltaQUDc coded flag = 0
            w.bits(0, 1);       // DeltaQUAc coded flag = 0
        }
        w.bits(0, 1);           // using_qmatrix = 0
        w.bits(0, 1);           // segmentation_enabled = 0
        w.bits(0, 1);           // delta_q_present = 0 (base_q_idx != 0 so it IS read)
        w.bits(0, 6);           // filter_level[0] = 0
        w.bits(0, 6);           // filter_level[1] = 0
        w.bits(0, 3);           // sharpness_level = 0
        w.bits(0, 1);           // mode_ref_delta_enabled = 0
        // enable_cdef = 0 and enable_restoration = 0 in redSeq128Color(), so the
        // cdef_params and lr_params blocks are skipped entirely.
        w.bits(0, 1);           // tx_mode = TX_MODE_SELECT
        w.bits(0, 1);           // reduced_tx_set = 0
    }

    /** A reduced still frame with a NON-lossless tail: the frame the tile streams
     *  this front decodes actually belong to. */
    static byte[] frameReducedQ(int baseQ, int codedW, int codedH,
                                int sbCols, int sbRows, int incCols, int incRows) {
        AvifMetadataSupport.Bits w = new AvifMetadataSupport.Bits();
        w.bits(1, 1);                       // disable_cdf_update
        w.bits(1, 1);                       // allow_screen_content_tools
        w.bits(1, 1);                       // force_integer_mv
        w.bits(0, 1);                       // render_and_frame_size_different = 0
        // allow_intrabc must be 0 here: the spec permits it only for a
        // CodedLossless frame, and Kof refuses it by name.
        w.bits(0, 1);                       // allow_intrabc = 0
        AvifFrameSupport.uniformTiles(w, sbCols, sbRows, 0, incCols, incRows);
        tailNonLossless(w, baseQ, 3);
        w.bits(1, 1);                       // frame_header_obu trailing one bit
        return AvifSeqSupport.obuType(6, w.bytes());
    }
}
