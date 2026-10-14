package dev.kof.compiler;

/**
 * Probe source for AVIF slice 3av (the frame assembly,
 * {@code libs/image/Av1FrameAssembly.kf}).
 *
 * <p>There is no libaom oracle here, and saying so plainly matters. The assembly
 * is pure ADDRESSING — strip the one-sample border, keep the plane extents —
 * and the driver's own output is already pinned against libaom by
 * {@code Av1DecodeTileReconE2ETest}/{@code Av1DecodeChromaE2ETest}. What this
 * probe pins is the property those goldens do NOT cover: the plane buffers carry
 * a border, so a correct strip must read the interior and a wrong one reads the
 * border. The probe therefore fills every buffer with a value that is a
 * deliberate function of its POSITION, which makes any off-by-one or wrong-stride
 * read produce a different number rather than the same one by luck.
 */
final class Av1FrameAssemblySupport {

    private Av1FrameAssemblySupport() {}

    /** Expected output of {@link #kofProbe()}; every value position-derived. */
    static final String GOLDEN =
        "S 1 2 3 4 5 6\n"
        + "F 4x4 cw=2 ch=2 y=0 1 2 3 4 5 6 7 8 9 10 11 12 13 14 15 u=1 2 3 4 v=5 6 7 8\n"
        + "M 4x4 cw=0 ch=0 usz=0 vsz=0\n"
        + "T 11 12 13 21 22 23 31 32 33";

    /** Kof probe exercising the strip, the multi-plane assembly and the monochrome gate. */
    static String kofProbe() {
        return PROBE;
    }

    private static final String PROBE = """
import image.Av1DecodeChroma
import image.Av1FrameAssembly

main() {
    strip()
    assembled()
    monochrome()
    ragged()
}

// A 3x2 interior inside a 5x4 buffer (stride 5). Position-derived contents: the
// interior cells are 1..6 and the border is deliberately 100+, so reading the
// border instead of the interior is visible immediately.
void strip() {
    var buf = new Int[20]
    var i = 0
    while (i < 20) {
        buf[i] = 100 + i
        i = i + 1
    }
    // interior at rows 1..2, cols 1..3 -> buf[6],buf[7],buf[8],buf[11],buf[12],buf[13]
    buf[6] = 1
    buf[7] = 2
    buf[8] = 3
    buf[11] = 4
    buf[12] = 5
    buf[13] = 6
    var out = av1StripPlaneBorder(buf, 5, 3, 2)
    var s = "S"
    i = 0
    while (i < out.size) {
        s = s + " " + out[i]
        i = i + 1
    }
    println(s)
}

// The full frame: luma flat already, chroma still bordered. U is 2x2 in a 4x4
// buffer, V likewise but offset by 4 so a U/V swap is visible.
void assembled() {
    var luma = new Int[16]
    var i = 0
    while (i < 16) {
        luma[i] = i
        i = i + 1
    }
    var bu = new Int[16]
    var bv = new Int[16]
    i = 0
    while (i < 16) {
        bu[i] = 200 + i
        bv[i] = 300 + i
        i = i + 1
    }
    bu[5] = 1
    bu[6] = 2
    bu[9] = 3
    bu[10] = 4
    bv[5] = 5
    bv[6] = 6
    bv[9] = 7
    bv[10] = 8
    var chroma = Av1ChromaPlanes(bu, 4, 2, 2, new Int[2], new Int[2], bv, 4, 2, 2, new Int[2], new Int[2])
    var f = av1AssembleFrame(luma, 4, 4, chroma, 1)
    var s = "F " + f.width + "x" + f.height + " cw=" + f.chromaWidth + " ch=" + f.chromaHeight
    s = s + " y=" + join(f.y)
    s = s + " u=" + join(f.u)
    s = s + " v=" + join(f.v)
    println(s)
}

// hasChroma = 0: the planes stay EMPTY rather than fabricating data the stream
// never carried (libaom's `xd->is_chroma_ref`).
void monochrome() {
    var chroma = Av1ChromaPlanes(new Int[1], 1, 0, 0, new Int[1], new Int[1], new Int[1], 1, 0, 0, new Int[1], new Int[1])
    var f = av1AssembleFrame(new Int[16], 4, 4, chroma, 0)
    println("M " + f.width + "x" + f.height + " cw=" + f.chromaWidth + " ch=" + f.chromaHeight + " usz=" + f.u.size + " vsz=" + f.v.size)
}

// A square plane whose stride is NOT width + 2 (6 for a width of 3): the strip
// must honour the stride it is GIVEN, not assume the buffer's natural one. The
// interior value is `row * 10 + col`, so a transposed read (col * 10 + row)
// produces different numbers and a wrong stride lands on border values.
void ragged() {
    var buf = new Int[24]
    var i = 0
    while (i < 24) {
        buf[i] = 100 + i
        i = i + 1
    }
    // interior 3x3 at stride 6: rows 1..3, cols 1..3
    var r = 1
    while (r < 4) {
        var c = 1
        while (c < 4) {
            buf[r * 6 + c] = r * 10 + c
            c = c + 1
        }
        r = r + 1
    }
    var out = av1StripPlaneBorder(buf, 6, 3, 3)
    println("T " + join(out))
}

String join(Int[] a) {
    var s = ""
    var i = 0
    while (i < a.size) {
        if (i > 0) {
            s = s + " "
        }
        s = s + a[i]
        i = i + 1
    }
    return s
}
""";
}
