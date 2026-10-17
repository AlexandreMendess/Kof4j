package dev.kof.compiler;

/**
 * The AV1 tile-walk instrument for the committed real 64x64 testsrc fixture
 * (`avif_real_64.avif`), split out of {@code AvifRealFileE2ETest} (which hit the
 * test-size ratchet). It walks the tile with the decoder bound and reports the
 * block grid, the payload CONSUMPTION, the per-transform-block non-zero counts,
 * the reconstructed Y samples and the per-block reader positions.
 *
 * <p>MEASURED 15/10 (the OPEN `3bd` symptom): `blocks=21 covered=256 frame=256
 * overlap=0 symBits=862 payloadBits=2344 txb=63 nzTot=102
 * nzHead=0,0,26,8,0,0,2,0,...`. The walk consumes only 862 of the 2344 payload
 * bits (37%) — `ffprobe` puts the frame OBU at 311 bytes = 293 of tile data
 * after the 18 header bytes, so the decoder really stops 63% short. ffmpeg's
 * reference Y for the same fixture is (0,0)=16 (0,32)=41 (0,63)=235 (32,0)=16
 * (32,32)=210 (32,63)=16, while Kof reconstructs 128 2 10 128 127 83 — `128` is
 * the bare DC prediction, i.e. block 0's residual was skipped. So the WRONG
 * decode and the under-consumption are the same event.
 *
 * <p>`palettePossible` is wired from the frame's `allow_screen_content_tools`
 * (`avif_real_cb8.avif` and `avif_real_64.avif` set it, `cb16`/`solid` do not): with
 * the flag hardcoded to 0 the screen-content fixtures desynced at the palette
 * syntax (cb8: 23/152 bits, `all_zero=1` everywhere); wired from the frame they
 * read the palette size symbol and consume 76/152 bits with 6 non-zero coefficients.
 *
 * <p>What does NOT discriminate: an exact block tiling is GUARANTEED BY
 * CONSTRUCTION (`decode_partition` partitions the fixed frame rectangle; a
 * corrupted stream still tiles), so it is not evidence the stream is in sync.
 * What IS validated: corrupting an UNREAD tail byte (285) leaves the dump
 * identical while a CONSUMED byte (10) changes it — so `position()` is a
 * consumed counter and the shortfall is real.
 */
final class AvifRealTileProbe {

    private AvifRealTileProbe() {}

/**
 * Instrument over the committed real 64x64 testsrc fixture
 * (`avif_real_64.avif`), walking the tile with the decoder bound. It reports
 * the block grid, the payload CONSUMPTION and the per-transform-block
 * non-zero counts, and asserts only that the walk does not read PAST the
 * payload.
     *
 * <p>MEASURED 15/10 (the OPEN `3bd` symptom): `blocks=21 covered=256
 * frame=256 overlap=0 symBits=862 payloadBits=2344 txb=63 nzTot=102
 * nzHead=0,0,26,8,0,0,2,0,...`. The walk consumes only 862 of the 2344
 * payload bits (37%) — a correct tile decode consumes essentially the whole
 * payload, and `ffprobe` puts the frame OBU at 311 bytes (293 of tile data
 * after the 18 header bytes, matching the payload) — so ~63% of the stream is
 * read as `all_zero` and skipped. THAT is the discriminating datum.
     *
 * <p>What does NOT discriminate, and it RETRACTS the 25th pass's "the
 * partition is exonerated": the grid always TILES the frame. Perturbing ONE
 * payload byte and re-walking still yields `covered=256 overlap=0` (while
 * `blocks` moves 21 -> 25 and `symBits` 862 -> 1302), because
 * `decode_partition` partitions the fixed frame rectangle BY CONSTRUCTION —
 * any symbols yield a valid tiling. An exact tiling is therefore NOT evidence
 * the stream is in sync; only the consumption is.
     *
 * <p>The consumption reading is itself validated: corrupting a byte in the
 * UNREAD tail (byte 285) leaves the whole dump IDENTICAL to the baseline,
 * while corrupting a CONSUMED byte (byte 10) changes it (blocks 21 -> 25,
 * symBits 862 -> 1302). So {@code position()} is a consumed counter and the
 * 63% shortfall is real, not a reader-window artifact.
 */
    /** The probe's `blocks=...` summary line (the trace line comes first). */
    static String summary(String out) {
        for (var l : out.split("\n")) {
            if (l.startsWith("blocks=")) return l;
        }
        throw new IllegalStateException("no summary line in: " + out);
    }

    static String probe(String path) {
        return """
import image.Av1Block
import image.Av1DecodeChroma
import image.Av1DecodeTileRecon
import image.Av1IntraBlock
import image.Av1ModeInfo
import image.Av1Partition
import image.Av1Quant
import image.Av1Symbol
import image.Av1TxType
import image.AvifFrame
import image.AvifGroup
import image.AvifObu

main() {
    var path = "%s"
    var h = readAvifFrameHeader(path)
    var seq = readAvifItemObus(path).seq
    var data = readAvifTilePayloads(path).get(0)
    var mw = h.codedWidth
    var mh = h.codedHeight
    var miw = mw >> 2
    var mih = mh >> 2
    var txMode = 1
    if (h.txModeSelect == 1) { txMode = 2 }
    var stride = mw + 2
    var frame = new Int[stride * (mh + 2)]
    var cw = mw >> 1
    var ch = mh >> 1
    var cstride = cw + 2
    var chroma = Av1ChromaPlanes(new Int[cstride * (ch + 2)], cstride, cw, ch, new Int[miw], new Int[mih], new Int[cstride * (ch + 2)], cstride, cw, ch, new Int[miw], new Int[mih])
    var sym = Av1Symbol(data)
    sym.enableTrace()
    var store = Av1CoeffCdfStore(h.baseQIdx)
    var txStore = Av1TxType(sym)
    var qt = Av1QuantTables()
    var dcq = qt.dcQ(seq.bitDepth, h.baseQIdx)
    var acq = qt.acQ(seq.bitDepth, h.baseQIdx)
    var fi = 0
    if (seq.enableFilterIntra) { fi = 1 }
    var de = 1
    if (seq.enableIntraEdgeFilter) { de = 0 }
    var ec = 0
    if (seq.enableCdef) { ec = 1 }
    var modes = listOf<Int>()
    var levels = listOf<Int[]>()
    var pal = 0
    if (h.allowScreenContent) { pal = 1 }
    var cfg = Av1ModeTail(12, h.cdefBits, ec, h.lossless, h.deltaQPresent, h.deltaQRes, h.deltaLfPresent, h.deltaLfMulti, h.deltaLfRes, 3)
    var tile = Av1TileDecode(frame, stride, mw, mh, cfg, h.lossless, 1, 3, fi, pal, seq.bitDepth, txMode, h.baseQIdx, 0, 0, 0, 0, dcq, acq, de, 0, h.reducedTxSet, sym, store, txStore, new Int[miw * mih], new Int[miw], new Int[mih], levels, listOf<Int>(), modes, chroma, 1, 1, listOf<Int[]>(), listOf<Int[]>(), listOf<Int>(), listOf<Int>())
    var walk = Av1Partition(data, miw, mih)
    walk.useReader(sym)
    walk.bindDecoder(tile)
    var steps = walk.walk()
    var cover = new Int[miw * mih]
    var blocks = 0
    var overlap = 0
    var i = 0
    while (i < steps.size) {
        var s = steps.get(i)
        if (s[0] == 1) {
            var w = av1BlockNum4x4Wide(s[3])
            var bh = av1BlockNum4x4High(s[3])
            blocks = blocks + 1
            var rr = 0
            while (rr < bh) {
                var cc = 0
                while (cc < w) {
                    var idx = (s[1] + rr) * miw + (s[2] + cc)
                    if (idx >= 0 && idx < cover.size) {
                        if (cover[idx] == 1) { overlap = overlap + 1 }
                        cover[idx] = 1
                    }
                    cc = cc + 1
                }
                rr = rr + 1
            }
        }
        i = i + 1
    }
    var covered = 0
    var k = 0
    while (k < cover.size) {
        covered = covered + cover[k]
        k = k + 1
    }
    var totNz = 0
    var head = ""
    var b = 0
    while (b < levels.size) {
        var lv = levels.get(b)
        var nz = 0
        var q = 0
        while (q < lv.size) {
            if (lv[q] != 0) { nz = nz + 1 }
            q = q + 1
        }
        totNz = totNz + nz
        if (b < 12) {
            if (b > 0) { head = head + "," }
            head = head + nz
        }
        b = b + 1
    }
    var ph = ""
    var pi = 0
    while (pi < data.size && pi < 12) {
        if (pi > 0) { ph = ph + "," }
        ph = ph + data[pi]
        pi = pi + 1
    }
    var lvOut = ""
    var ln = 0
    var li = 0
    while (li < levels.size && ln < 3) {
        var lv = levels.get(li)
        var nzq = 0
        var q2 = 0
        while (q2 < lv.size) {
            if (lv[q2] != 0) { nzq = nzq + 1 }
            q2 = q2 + 1
        }
        if (nzq > 0) {
            if (ln > 0) { lvOut = lvOut + " | " }
            lvOut = lvOut + "tx" + li + ":"
            var q3 = 0
            while (q3 < lv.size && q3 < 10) {
                lvOut = lvOut + lv[q3]
                q3 = q3 + 1
            }
            ln = ln + 1
        }
        li = li + 1
    }
    println("LEVELS " + lvOut)
    var bs1 = 0
    var st = 0
    var yline = ""
    while (st < steps.size) {
        var s = steps.get(st)
        if (s[0] == 1) {
            if (bs1 < 12) {
                if (bs1 > 0) { yline = yline + " " }
                yline = yline + "(" + s[1] + "," + s[2] + ",bs" + s[3] + ")=" + frame[(s[1] * 4 + 1) * stride + (s[2] * 4 + 1)]
            }
            bs1 = bs1 + 1
        }
        st = st + 1
    }
    println("BLKY " + yline)
    var pl = tile.predBlocks()
    var pbl = 0
    var pline = ""
    while (pbl < pl.size / 7 && pbl < 12) {
        if (pbl > 0) { pline = pline + " " }
        pline = pline + "tx" + pl[pbl * 7] + "(r" + pl[pbl * 7 + 1] + ",c" + pl[pbl * 7 + 2] + ")+" + pl[pbl * 7 + 3] + "," + pl[pbl * 7 + 4] + " p=" + pl[pbl * 7 + 5] + "/r=" + pl[pbl * 7 + 6]
        pbl = pbl + 1
    }
    println("PREDBLK " + pline)
    // Per-block STAGE reader positions: body end > palette-token end > tx-size end
    // > residual end. It shows which stage of a block eats the bits.
    var sg = tile.stages()
    var po2 = tile.positions()
    var si = 0
    var sline2 = ""
    while (si < sg.size / 2 && si < 4) {
        sline2 = sline2 + " b" + si + ":" + sg[si * 2] + ">" + sg[si * 2 + 1] + ">" + po2[si * 2] + ">" + po2[si * 2 + 1]
        si = si + 1
    }
    println("STAGES" + sline2)
    // Per-block palette MODE info: does the stream even signal a palette here?
    var pi2 = tile.palInfos()
    var pil = 0
    var piline = ""
    while (pil < pi2.size / 2 && pil < 6) {
        if (pil > 0) { piline = piline + " " }
        piline = piline + "b" + pil + ":palY" + pi2[pil * 2] + "/palUv" + pi2[pil * 2 + 1]
        pil = pil + 1
    }
    println("PALINFO " + piline)
    // For the FIRST palette block: its colours and its first 8 decoded map indices,
    // beside the reference's two values.
    var fi2 = 0
    var found = 0
    var fline = ""
    var st3 = 0
    while (st3 < steps.size) {
        var s3 = steps.get(st3)
        if (s3[0] == 1) {
            if (found == 0 && pi2[fi2 * 2] > 0) {
                fline = fline + "pal(r" + s3[1] + ",c" + s3[2] + ",bs" + s3[3] + ",n" + pi2[fi2 * 2] + ")"
                var k3 = 0
                while (k3 < pi2[fi2 * 2]) {
                    fline = fline + " col" + k3 + "=" + tile.palColorAt(s3[1], s3[2], k3)
                    k3 = k3 + 1
                }
                var k4 = 0
                while (k4 < 8) {
                    fline = fline + " idx" + k4 + "=" + tile.palIndexAt(s3[1], s3[2], k4)
                    k4 = k4 + 1
                }
                found = 1
            }
            fi2 = fi2 + 1
        }
        st3 = st3 + 1
    }
    println("PALBLK " + fline)
    // The reader position at each block's ENTRY: after the partition symbols,
    // before the mode info. blockEntry - previous residual end = the partition cost.
    var bs2 = tile.blockStarts()
    var bl = 0
    var bline = ""
    while (bl < bs2.size && bl < 6) {
        if (bl > 0) { bline = bline + " " }
        bline = bline + "b" + bl + ":" + bs2[bl]
        bl = bl + 1
    }
    println("BSTART " + bline)
    // The mode-info BODY field positions (4 per block): which field eats the bits.
    var bf = tile.bodyFields()
    var bfl = 0
    var bfline = ""
    while (bfl < bf.size && bfl < 16) {
        if (bfl > 0) { bfline = bfline + " " }
        bfline = bfline + bf[bfl]
        bfl = bfl + 1
    }
    println("BODYF " + bfline)
    // How many symbols the palette-token stage of block 1 read (its window comes
    // from STAGES: body end .. palette end), to compare against the colour map's
    // expected `rows * cols - 1` index symbols.
    if (sg.size >= 4) {
        var tsl = sym.log()
        var pcount = 0
        var plast = 0
        var ti = 0
        while (ti < tsl.size / 3) {
            var tpos = tsl[ti * 3 + 2]
            if (tpos > sg[2] && tpos < sg[3]) { pcount = pcount + 1 }
            if (tpos > sg[2] && tpos <= sg[3]) { plast = tpos }
            ti = ti + 1
        }
        println("PALTOK window=" + sg[2] + ".." + sg[3] + " bits=" + (sg[3] - sg[2]) + " symbols=" + pcount + " lastPos=" + plast + " total=" + (tsl.size / 3))
    }
    // Experiment: reproduce block 1's BOTTOM transform block (8x8 at pixel
    // origin (8,8)) by calling the predictor DIRECTLY with explicit arguments,
    // for all four (aboveRight, belowLeft) combinations — so a wrong
    // availability is separated from a wrong angle application without
    // touching the driver.
    var bmi = 17
    if (modes.size >= 34) {
        var bMode = modes[bmi + 5]
        var bAng = modes[bmi + 6]
        var bFI = modes[bmi + 11]
        var bFIM = modes[bmi + 12]
        var combos = ""
        var ar = 0
        while (ar <= 1) {
            var bl = 0
            while (bl <= 1) {
                var pp = av1IntraPredictBlock(frame, stride, 9, 9, 8, 8, mw, mh, 1, 1, ar, bl, bMode, bAng, bFI, bFIM, seq.bitDepth, 0, true)
                combos = combos + " ar" + ar + "bl" + bl + "=" + pp[0]
                bl = bl + 1
            }
            ar = ar + 1
    }
    println("PREDTEST mode=" + bMode + " ang=" + bAng + " fi=" + bFI + " fim=" + bFIM + combos)
    println("PREDREF above=" + frame[8 * stride + 9] + "," + frame[8 * stride + 10] + "," + frame[8 * stride + 11] + " left=" + frame[9 * stride + 8] + "," + frame[10 * stride + 8] + "," + frame[11 * stride + 8] + " corner=" + frame[8 * stride + 8])
    }
    // A PATCH of the FINAL frame (an 18x18 window at the origin, rows/cols
    // 0..17): it shows where a block's write landed a value the reference
    // never has, which localises an out-of-extent or desynced write.
    var pmaxr = 17
    if (pmaxr >= mh) { pmaxr = mh - 1 }
    var pmaxc = 17
    if (pmaxc >= mw) { pmaxc = mw - 1 }
    var py2 = 0
    while (py2 <= pmaxr) {
        var prow = ""
        var px2 = 0
        while (px2 <= pmaxc) {
            prow = prow + frame[(py2 + 1) * stride + (px2 + 1)] + " "
            px2 = px2 + 1
        }
        println("PATCH r" + py2 + " " + prow)
        py2 = py2 + 1
    }
    println("SEG segEnabled=" + h.segEnabled + " altQ0=" + h.segAltQ[0] + " altQ1=" + h.segAltQ[1])
    println("PAYLOAD " + data.size + " " + ph + " path=" + path)
    var sl = sym.log()
    var sc = 0
    var sline = ""
    while (sc < sl.size / 3 && sc < 24) {
        if (sc > 0) { sline = sline + " " }
        sline = sline + sl[sc * 3] + ":" + sl[sc * 3 + 1] + "@" + sl[sc * 3 + 2]
        sc = sc + 1
    }
    println("TRACE n=" + (sl.size / 3) + " " + sline)
    println("blocks=" + blocks + " covered=" + covered + " frame=" + (miw * mih) + " overlap=" + overlap + " symBits=" + walk.position() + " payloadBits=" + (data.size * 8) + " txb=" + levels.size + " nzTot=" + totNz + " nzHead=" + head)
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
    var pos = tile.positions()
    var b2 = 0
    while (b2 < modes.size / 17) {
        var mi = b2 * 17
        println("B " + b2 + " " + pos[b2 * 2] + " " + pos[b2 * 2 + 1] + " " + modes[mi] + " " + modes[mi + 4] + " " + modes[mi + 5] + " " + modes[mi + 6] + " " + modes[mi + 7] + " " + modes[mi + 8])
        b2 = b2 + 1
    }
}
""".formatted(path);
    }
}
