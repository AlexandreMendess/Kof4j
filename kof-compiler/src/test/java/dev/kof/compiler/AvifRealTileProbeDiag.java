package dev.kof.compiler;

/**
 * The AVIF tile probe's DIAGNOSTIC TAIL as a Kof function: the Y and UV
 * sample lines, the V plane's levels and their dequantized values, and the
 * per-block `B` lines. Split out of {@link AvifRealTileProbeProgram} so each
 * Java file stays within the repository's size ratchet — the Kof side is
 * unchanged, because the harness injects this source into the same program.
 *
 * <p>NOTE for editors: this text is injected AFTER {@code String.formatted}
 * runs, so a Kof {@code %} needs no doubling here.
 */
final class AvifRealTileProbeDiag {

    private AvifRealTileProbeDiag() { }

    static String source() {
        return """
void probeTail(Int[] frame, Int stride, Int mw, Int mh,
               Av1ChromaPlanes chroma, List<Int[]> levelsV, List<Int[]> steps,
               Int dcq, Int acq, Int baseQIdx, Int bitDepth,
               Av1TileDecode tile, List<Int> modes, List<Int> txTypesV,
               List<Int[]> levelsU) {
        // The sample positions are clamped to the frame: they were hardcoded for a
    // 64x64 frame and read out of range on anything smaller (a 32x32 frame put
    // the (32,63) sample at index 1186 of a 1156-entry plane+border buffer).
    var sx1 = 32
    if (sx1 >= mw) { sx1 = mw - 1 }
    var sx2 = 63
    if (sx2 >= mw) { sx2 = mw - 1 }
    var sy = 32
    if (sy >= mh) { sy = mh - 1 }
    println("Y 00=" + frame[1 * stride + 1] + " 032=" + frame[1 * stride + (sx1 + 1)] + " 063=" + frame[1 * stride + (sx2 + 1)] + " 320=" + frame[(sy + 1) * stride + 1] + " 3232=" + frame[(sy + 1) * stride + (sx1 + 1)] + " 3263=" + frame[(sy + 1) * stride + (sx2 + 1)])
    // The CHROMA at the same points: CFL acts on the chroma, so a luma oracle
    // alone cannot see it (the 115th pass's instrument gap).
    var cl = ""
    var ci2 = 0
    while (ci2 < levelsV.size) {
        var lv = levelsV.get(ci2)
        var k5 = 0
        while (k5 < lv.size) {
            if (lv[k5] != 0) { cl = cl + " t" + ci2 + "[" + k5 + "]=" + lv[k5] }
            k5 = k5 + 1
        }
        ci2 = ci2 + 1
    }
    // The V transform blocks' TYPES: the 16x16 DCT path is exercised exactly by
    // other fixtures, so a different type here is the discriminator for the +/-1.
    println("VTXT n=" + txTypesV.size + " " + txTypesV)
    // The U plane's levels too: if the U's residual is ZERO then its exactness
    // says nothing about the residual path, and the V is its only witness.
    var cu = ""
    var ui = 0
    while (ui < levelsU.size) {
        var lu = levelsU.get(ui)
        var k7 = 0
        while (k7 < lu.size) {
            if (lu[k7] != 0) { cu = cu + " t" + ui + "[" + k7 + "]=" + lu[k7] }
            k7 = k7 + 1
        }
        ui = ui + 1
    }
    println("ULEV n=" + levelsU.size + cu)
    println("VLEV n=" + levelsV.size + cl)
    // The DEQUANTIZED values of that same V transform block: the three levels
    // above times the quantizer. The dequant is a plain multiply (the AV1's
    // dc_q/ac_q tables already carry the scaling), so a hand computation can
    // say whether the ±1 enters here or in the inverse transform.
    // The DEQUANTIZED values of the first V transform block (the step between the
    // levels and the pixel, where a +/-1 can enter with the entropy intact). Only
    // when the block's shape matches the level array: on a multi-block fixture the
    // first V TX may belong to a later block.
    var dqv = ""
    var bs2 = 9
    var s2 = 0
    while (s2 < steps.size) {
        if (steps.get(s2)[0] == 1) { bs2 = steps.get(s2)[3] }
        s2 = s2 + 1
    }
    var uvTx2 = av1GetMaxUvTxSize(bs2, 1, 1)
    if (levelsV.size > 0 && av1TxWidth(uvTx2) * av1TxHeight(uvTx2) == levelsV.get(0).size) {
        var dq2 = av1Dequant(levelsV.get(0), uvTx2, bitDepth, dcq, acq)
        var k6 = 0
        while (k6 < dq2.size) {
            if (dq2[k6] != 0) { dqv = dqv + " t0[" + k6 + "]=" + dq2[k6] }
            k6 = k6 + 1
        }
    }
    println("DQV dcq=" + dcq + " acq=" + acq + " qIdx=" + baseQIdx + dqv)
    // The V transform block's RECONSTRUCTED RESIDUAL (the inverse transform's
    // own output): av1ReconstructBlock with a ZERO prediction returns it, and
    // it is the one link of the chain with no oracle yet. The U's residual is
    // DC-only, so the AC butterflies are exercised only here.
    var vres = ""
    if (levelsV.size > 0 && txTypesV.size > 0 && av1TxWidth(uvTx2) * av1TxHeight(uvTx2) == levelsV.get(0).size) {
        var z = new Int[av1TxWidth(uvTx2) * av1TxHeight(uvTx2)]
        var rec = av1ReconstructBlock(levelsV.get(0), z, uvTx2, txTypesV.get(0), bitDepth, false, dcq, acq)
        var w2 = av1TxWidth(uvTx2)
        vres = " (0,0)=" + rec[0] + " (0,1)=" + rec[1] + " (1,0)=" + rec[w2]
        var s15 = 15
        if (s15 >= w2) { s15 = w2 - 1 }
        vres = vres + " (1,1)=" + rec[w2 + 1] + " (15,15)=" + rec[w2 * s15 + s15]
    }
    println("VRES" + vres)
    println("UV 00=" + chroma.u[1 * chroma.strideU + 1] + "," + chroma.v[1 * chroma.strideV + 1]
        + " 032=" + chroma.u[1 * chroma.strideU + ((sx1 >> 1) + 1)] + "," + chroma.v[1 * chroma.strideV + ((sx1 >> 1) + 1)]
        + " 063=" + chroma.u[1 * chroma.strideU + ((sx2 >> 1) + 1)] + "," + chroma.v[1 * chroma.strideV + ((sx2 >> 1) + 1)]
        + " 320=" + chroma.u[((sy >> 1) + 1) * chroma.strideU + 1] + "," + chroma.v[((sy >> 1) + 1) * chroma.strideV + 1]
        + " 3232=" + chroma.u[((sy >> 1) + 1) * chroma.strideU + ((sx1 >> 1) + 1)] + "," + chroma.v[((sy >> 1) + 1) * chroma.strideV + ((sx1 >> 1) + 1)]
        + " 3263=" + chroma.u[((sy >> 1) + 1) * chroma.strideU + ((sx2 >> 1) + 1)] + "," + chroma.v[((sy >> 1) + 1) * chroma.strideV + ((sx2 >> 1) + 1)])
    var pos = tile.positions()
    var b2 = 0
    while (b2 < modes.size / 17) {
        var mi = b2 * 17
        println("B " + b2 + " " + pos[b2 * 2] + " " + pos[b2 * 2 + 1] + " " + modes[mi] + " " + modes[mi + 4] + " " + modes[mi + 5] + " " + modes[mi + 6] + " " + modes[mi + 7] + " " + modes[mi + 8])
        b2 = b2 + 1
    }
}
""";
    }
}
