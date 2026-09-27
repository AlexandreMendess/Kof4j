package dev.kof.compiler.nat;

import dev.kof.compiler.AbiLayout;
import dev.kof.compiler.FfiSignature;
import dev.kof.compiler.FfiStructLayout;
import dev.kof.compiler.IRBasicBlock;
import dev.kof.compiler.IRClass;
import dev.kof.compiler.IRMethod;
import dev.kof.compiler.IRModule;
import dev.kof.compiler.KofCall;
import dev.kof.compiler.KofCallKind;
import dev.kof.compiler.KofOperation;
import dev.kof.compiler.Type;

import java.util.ArrayList;
import java.util.List;

/**
 * #431 (Native FFI): call-site do `extern` no alvo nativo — marshaling SysV
 * (x86-64) direto para uma shared object ligada no link do binário
 * (`call sym@PLT`, o caminho de saída PROVO de §61 e o mesmo mecanismo do
 * consumidor SQLite/DB001). Nenhum dlopen em runtime; nenhum wrapper à mão.
 *
 * <p>Contrato do KofCall (montado em {@code ExpressionMethodCallLowerer}):
 * owner {@code kof.ffi}, methodName {@code "lib::simbolo"}, parameterTypes =
 * os tipos ESCALARES declarados do extern, returnType = tipo do extern. Os
 * argumentos já estão na pilha de operandos (empilhados esquerda→direita,
 * 8 bytes por slot — a mesma convenção dos calls internos {@code kof_*}).
 *
 * <p>Classes SysV: int-class = {Int, Long, Bool, String(char*)} →
 * rdi,rsi,rdx,rcx,r8,r9, depois pilha; float-class = {Float, Double} →
 * xmm0..7, depois pilha. String: o objeto Kof nativo carrega UTF-8
 * NUL-terminado no offset 24 (layout {@code kof_string_from_literal}) — vai
 * {@code obj+24} direto (NULL Kof → NULL C, nunca segfault silencioso).
 * Retorno String = cópia na fronteira p/ um objeto Kof novo (o buffer C NUNCA
 * é free'd — mesmo contrato do downcall JVM {@code strstr}); void não empilha.
 */
final class NativeFfiCall {

    private NativeFfiCall() {}

    static boolean isExternCall(KofCall kc) {
        return kc.kind() == KofCallKind.FUNCTION
                && kc.ownerType() instanceof Type.ClassType ct
                && "ffi".equals(ct.name()) && "kof".equals(ct.packageName())
                && kc.methodName().indexOf("::") >= 0;
    }

    static String libOf(KofCall kc) {
        return kc.methodName().substring(0, kc.methodName().indexOf("::"));
    }

    static String symbolOf(KofCall kc) {
        return kc.methodName().substring(kc.methodName().indexOf("::") + 2);
    }

    /** true se o retorno exige o helper de cópia char*→objeto String. */
    static boolean returnsCstr(KofCall kc) {
        Character c = FfiSignature.charOfType(kc.returnType());
        return c != null && c.charValue() == 'S';
    }

    /** true se algum parâmetro é o marker array-ptr (D6-2/3.7) — pede o helper
     *  de empacotamento `kof_ffi_pack_array` no runtime. */
    static boolean usesArrayParam(KofCall kc) {
        for (Type t : kc.parameterTypes()) {
            if (FfiStructLayout.isArrayPtr(t)) return true;
        }
        return false;
    }

    /** #431: registra um extern no backend (biblioteca p/ o ld + flags dos
     *  helpers). Extraído do `NativeBackend` (gate ≤500 regra 7). */
    static void noteExtern(NativeBackend nb, KofCall kc) {
        nb.ffiLibs.add(libOf(kc));
        if (returnsCstr(kc)) nb.ffiUsesCstr = true;
        if (usesArrayParam(kc)) nb.ffiUsesArray = true;
    }

    /** Emite os helpers de runtime dos extern x86-64 (uma vez por programa). */
    static void emitHelpers(NativeBackend nb, StringBuilder sb) {
        if (nb.ffiUsesCstr) emitX86CstrHelper(sb);
        if (nb.ffiUsesArray) emitX86ArrayPackHelper(sb);
    }

    /** #431 fatia 2 / D6-2: link-by-use dos externs no cross (mesmo scan do
     *  x86 — `library()` vira input do ld, retorno String/array pede o helper). */
    static void scanExterns(NativeBackend nb, IRModule module) {
        nb.ffiLibs.clear();
        nb.ffiUsesCstr = false;
        nb.ffiUsesArray = false;
        for (IRClass c : module.classes()) {
            for (IRMethod m : c.methods()) {
                for (IRBasicBlock b : m.basicBlocks()) {
                    for (KofOperation op : b.operations()) {
                        if (op instanceof KofCall kc && isExternCall(kc)) noteExtern(nb, kc);
                    }
                }
            }
        }
    }

    private static boolean isFloatClass(char c) { return c == 'f' || c == 'd'; }

    // ── x86-64 (SysV) ────────────────────────────────────────────────
    static void emitX86(NativeBackend nb, StringBuilder sb, KofCall kc) {
        String[] intRegs = {"%rdi", "%rsi", "%rdx", "%rcx", "%r8", "%r9"};
        int n = kc.parameterTypes().size();
        char[] cls = new char[n];
        boolean[] isStruct = new boolean[n];
        Type[] structTypes = new Type[n];
        boolean[] isArray = new boolean[n];
        char[] arrayElem = new char[n];
        for (int i = 0; i < n; i++) {
            Type pt = kc.parameterTypes().get(i);
            if (FfiStructLayout.isArrayPtr(pt)) {
                isArray[i] = true;
                arrayElem[i] = FfiStructLayout.arrayPtrElem(pt);
            } else if (FfiStructLayout.isStructType(pt)) {
                isStruct[i] = true;
                structTypes[i] = pt;
            } else {
                cls[i] = FfiSignature.charOfType(pt);
            }
        }
        Character retC = FfiSignature.charOfType(kc.returnType());
        boolean structRet = retC == null;   // ClassType: `record` por valor (3.7)
        char ret = structRet ? 0 : retC.charValue();
        // 3.7 fatia 2b: retorno struct por MEMORY → sret (ponteiro escondido em
        // rdi). O objeto Kof é alocado ANTES do call (o call C preserva
        // %r12/%r13) e o struct cru vai para um buffer na pilha.
        Type retStructType = null;
        AbiLayout.Layout retLayout = null;
        NativeOpHelpers.Resolved retResolved = null;
        boolean sret = false;
        if (structRet) {
            retResolved = NativeOpHelpers.resolveClass(nb, kc.returnType());
            List<Type> retFts = new ArrayList<>();
            if (retResolved != null) for (var f : retResolved.layout().fields()) retFts.add(f.type());
            retStructType = FfiStructLayout.structType(retFts);
            retLayout = FfiStructLayout.layout(AbiLayout.Abi.SYSV_X86_64, retStructType);
            sret = retLayout.byMemory();
        }
        int intReserved = sret ? 1 : 0;
        // ordinais POR CLASSE na ordem formal (arg0 → reg0 da sua classe). Um
        // struct ocupa um ordinal por eightbyte (INTEGER→reg int, SSE→xmm).
        int[] ord = new int[n];
        int[][] sOrd = new int[n][];
        boolean[][] sFlt = new boolean[n][];
        int nInt = intReserved, nFlt = 0;
        for (int i = 0; i < n; i++) {
            if (isStruct[i]) {
                var cs = FfiStructLayout.layout(AbiLayout.Abi.SYSV_X86_64, structTypes[i]).classes();
                sOrd[i] = new int[cs.size()];
                sFlt[i] = new boolean[cs.size()];
                for (int e = 0; e < cs.size(); e++) {
                    boolean f = cs.get(e) == AbiLayout.ArgClass.SSE;
                    sFlt[i][e] = f;
                    sOrd[i][e] = f ? nFlt++ : nInt++;
                }
            } else if (isArray[i]) {
                ord[i] = nInt++;   // T[]→ptr: um ponteiro INTEGER (D6-2)
            } else if (isFloatClass(cls[i])) {
                ord[i] = nFlt++;
            } else {
                ord[i] = nInt++;
            }
        }
        int spill = (nInt > 6 ? nInt - 6 : 0) + (nFlt > 8 ? nFlt - 8 : 0);
        int seq = nb.inlineSeq++;
        // sret: aloca o objeto ANTES de popar os args (a alocação é um call C e
        // clobberaria os registradores de arg; aqui os args ainda estão na pilha,
        // acima do frame do kof_alloc — preservados). C preserva %r12.
        if (sret) {
            NativeOpHelpers.emitAllocObject(nb, sb, retResolved);
            sb.append("    movq %rax, %r12\n");
        }
        // 0) D6-2/3.7: arrays `T[]`→`ptr` são empacotados (copy-in) num buffer
        //    próprio por arg ANTES de carregar os registradores — o call do
        //    helper clobberaria os args já carregados; o buffer fica no slot
        //    temporário e é lido no passo 1 (ou empilhado como derramado).
        for (int i = 0; i < n; i++) {
            if (!isArray[i]) continue;
            sb.append("    movq ").append(8 * (n - 1 - i)).append("(%rsp), %rdi\n");
            sb.append("    movq $").append(arrayElemSize(arrayElem[i])).append(", %rsi\n");
            sb.append("    call kof_ffi_pack_array\n");
            sb.append("    movq %rax, -").append(256 + i * 8).append("(%rbp)\n");
        }
        for (int i = n - 1; i >= 0; i--) {
            if (isArray[i]) {
                sb.append("    popq %r10\n");   // descarta o objeto; o buffer está no temp
                if (ord[i] < 6) {
                    sb.append("    movq -").append(256 + i * 8).append("(%rbp), ")
                      .append(intRegs[ord[i]]).append("\n");
                }
                continue;
            }
            if (isStruct[i]) {
                // struct por valor: ponteiro do objeto Kof → monta cada eightbyte
                // direto no registrador de destino (shift/or no int, mov na xmm).
                sb.append("    popq %r10\n");
                for (int e = 0; e < sOrd[i].length; e++) {
                    boolean f = sFlt[i][e];
                    String dst = f ? "%xmm" + sOrd[i][e] : intRegs[sOrd[i][e]];
                    FfiStructLayout.emitX86Eightbyte(sb, structTypes[i], e, "%r10", dst, f);
                }
                continue;
            }
            char c = cls[i];
            if (isFloatClass(c)) {
                sb.append("    popq %r11\n");
                if (ord[i] < 8) {
                    sb.append(c == 'f' ? "    movd %r11d, %xmm" : "    movq %r11, %xmm")
                      .append(ord[i]).append("\n");
                } else {
                    sb.append("    movq %r11, -").append(256 + i * 8).append("(%rbp)\n");
                }
            } else if (ord[i] < 6) {
                sb.append("    popq ").append(intRegs[ord[i]]).append("\n");
                if (c == 'S') {
                    // char* = payload UTF-8 do objeto (offset 24); NULL → NULL
                    String lbl = ".Lffi_s" + seq + "_" + i;
                    sb.append("    testq ").append(intRegs[ord[i]]).append(", ").append(intRegs[ord[i]]).append("\n");
                    sb.append("    je ").append(lbl).append("\n");
                    sb.append("    leaq 24(").append(intRegs[ord[i]]).append("), ").append(intRegs[ord[i]]).append("\n");
                    sb.append(lbl).append(":\n");
                }
            } else {
                sb.append("    popq %r11\n");
                if (c == 'S') {
                    String lbl = ".Lffi_s" + seq + "_" + i;
                    sb.append("    testq %r11, %r11\n");
                    sb.append("    je ").append(lbl).append("\n");
                    sb.append("    leaq 24(%r11), %r11\n");
                    sb.append(lbl).append(":\n");
                }
                sb.append("    movq %r11, -").append(256 + i * 8).append("(%rbp)\n");
            }
        }
        // 2) pilha SysV: salva o topo da pilha de operandos, alinha 16,
        //    compensa a paridade do spill e empilha os derramados — o formal
        //    mais à DIREITA primeiro p/ o 1º derramado ficar em 0(%rsp).
        sb.append("    movq %rsp, %rbx\n");
        if (sret) {
            int buf = ((retLayout.size() + 15) & ~15) + 16;
            sb.append("    subq $").append(buf).append(", %rsp\n");
            sb.append("    andq $-16, %rsp\n");
            sb.append("    movq %rsp, %r13\n");
        } else {
            sb.append("    andq $-16, %rsp\n");
        }
        if (spill % 2 != 0) sb.append("    subq $8, %rsp\n");
        for (int i = n - 1; i >= 0; i--) {
            if (!isStruct[i] && (isFloatClass(cls[i]) ? ord[i] >= 8 : ord[i] >= 6)) {
                sb.append("    pushq -").append(256 + i * 8).append("(%rbp)\n");
            }
        }
        if (sret) sb.append("    movq %r13, %rdi\n");   // ponteiro escondido (D6-4)
        // 3) o call direto (PLT → ld.so resolve no exec; sem dlopen — §61)
        sb.append("    call ").append(symbolOf(kc)).append("@PLT\n");
        sb.append("    movq %rbx, %rsp\n");
        // 4) retorno: struct por valor materializa o `record` (register path ou
        //    sret); caso contrário, o escalar/void de sempre → slot de 8 bytes.
        if (structRet) {
            emitX86StructReturn(nb, sb, retResolved, retLayout, sret);
            return;
        }
        switch (ret) {
            case 'v': return;
            case 'i': sb.append("    movslq %eax, %rax\n"); break;
            case 'j': break;
            case 'f': sb.append("    movd %xmm0, %eax\n"); break;
            case 'd': sb.append("    movq %xmm0, %rax\n"); break;
            case 'b': sb.append("    movzbl %al, %eax\n"); break;
            case 'S':
                // C devolve o char* em %rax; o helper recebe p/ %rdi (conv
                // dos helpers kof_* de 1 arg — mesmo movimento que o call faz).
                sb.append("    movq %rax, %rdi\n");
                sb.append("    call kof_ffi_from_cstr\n");
                break;
            default: return;
        }
        sb.append("    pushq %rax\n");
    }

    /**
     * 3.7 fatia 2: materializa o `record` devolvido por valor.
     *
     * <p><b>Register path</b> (≤ 16 B): os eightbytes de retorno (rax/rdx +
     * xmm0/xmm1) são salvos na pilha, o objeto Kof é alocado+inicializado e cada
     * campo é extraído do seu eightbyte (shift + extensão pela largura).
     *
     * <p><b>sret</b> (&gt; 16 B, SysV MEMORY — D6-4): o objeto já foi alocado
     * antes do call (em %r12) e o struct cru está no buffer apontado por %r13;
     * cada campo é lido do seu offset C (sem eightbyte).
     */
    private static void emitX86StructReturn(NativeBackend nb, StringBuilder sb,
                                            NativeOpHelpers.Resolved r,
                                            AbiLayout.Layout l, boolean sret) {
        List<Type> ftypes = new ArrayList<>();
        if (r != null) for (var f : r.layout().fields()) ftypes.add(f.type());
        if (sret) {
            for (int i = 0; i < ftypes.size(); i++) {
                AbiLayout.Scalar sc = FfiStructLayout.scalarOf(ftypes.get(i));
                int cOff = l.offsets()[i];
                int kofOff = r != null ? r.layout().fields().get(i).offset() : 16 + 8 * i;
                switch (sc.size) {
                    case 1 -> sb.append("    movzbl ").append(cOff).append("(%r13), %eax\n");
                    case 2 -> sb.append("    movzwl ").append(cOff).append("(%r13), %eax\n");
                    case 4 -> sb.append(sc == AbiLayout.Scalar.INT
                            ? "    movslq " + cOff + "(%r13), %rax\n"
                            : "    movl " + cOff + "(%r13), %eax\n");
                    default -> sb.append("    movq ").append(cOff).append("(%r13), %rax\n");
                }
                sb.append("    movq %rax, ").append(kofOff).append("(%r12)\n");
            }
            sb.append("    pushq %r12\n");
            return;
        }
        List<AbiLayout.ArgClass> classes = l.classes();
        // 1) guarda os eightbytes de retorno (e0 em 0(%rsp) após os pushes)
        sb.append("    movq %rax, %r10\n");
        sb.append("    movq %rdx, %r11\n");
        for (int e = classes.size() - 1; e >= 0; e--) {
            if (classes.get(e) == AbiLayout.ArgClass.SSE) {
                sb.append("    movq %xmm").append(ordinal(classes, e, true)).append(", %rax\n");
                sb.append("    pushq %rax\n");
            } else {
                sb.append("    pushq ").append(ordinal(classes, e, false) == 0 ? "%r10" : "%r11").append("\n");
            }
        }
        // 2) aloca+inicializa o objeto Kof (obj em %rax → %r10)
        NativeOpHelpers.emitAllocObject(nb, sb, r);
        sb.append("    movq %rax, %r10\n");
        // 3) cada campo: do eightbyte cru p/ o slot Kof (largura natural)
        for (int i = 0; i < ftypes.size(); i++) {
            AbiLayout.Scalar sc = FfiStructLayout.scalarOf(ftypes.get(i));
            int cOff = l.offsets()[i];
            int e = cOff / 8;
            int shift = (cOff - e * 8) * 8;
            int kofOff = r != null ? r.layout().fields().get(i).offset() : 16 + 8 * i;
            sb.append("    movq ").append(8 * e).append("(%rsp), %rax\n");
            if (shift > 0) sb.append("    shrq $").append(shift).append(", %rax\n");
            switch (sc.size) {
                case 1 -> sb.append("    movzbl %al, %eax\n");
                case 2 -> sb.append("    movzwl %ax, %eax\n");
                case 4 -> sb.append(sc == AbiLayout.Scalar.INT
                        ? "    movslq %eax, %rax\n" : "    movl %eax, %eax\n");
                default -> { }
            }
            sb.append("    movq %rax, ").append(kofOff).append("(%r10)\n");
        }
        // 4) remove o scratch e empilha o objeto como resultado
        if (!classes.isEmpty()) sb.append("    addq $").append(8 * classes.size()).append(", %rsp\n");
        sb.append("    pushq %r10\n");
    }

    private static int ordinal(List<AbiLayout.ArgClass> classes, int e, boolean sse) {
        int n = 0;
        for (int i = 0; i < e; i++) {
            boolean isSse = classes.get(i) == AbiLayout.ArgClass.SSE;
            if (isSse == sse) n++;
        }
        return n;
    }


    /** Helper char*→String (cópia UTF-8 crua na fronteira — o buffer C nunca é
     *  liberado; NULL → 0 = null Kof). Definido UMA vez por programa quando um
     *  extern retorna String; o PRÓPRIO call-site o referencia (a poda de
     *  runtime é por texto do programa — o helper vive no texto do programa). */
    static void emitX86CstrHelper(StringBuilder sb) {
        sb.append("""
                kof_ffi_from_cstr:
                    testq %rdi, %rdi
                    je .Lffc_null
                    pushq %r12
                    pushq %r13
                    pushq %r14
                    movq %rdi, %r12
                    xorq %rcx, %rcx
                .Lffc_scan:
                    cmpb $0, (%r12,%rcx)
                    je .Lffc_got
                    incq %rcx
                    jmp .Lffc_scan
                .Lffc_got:
                    movq %rcx, %r13
                    leal 25(%r13), %edi
                    call kof_alloc
                    movq %rax, %r14
                    movl $1, 0(%r14)
                    movl $0, 4(%r14)
                    movq $0, 8(%r14)
                    movl %r13d, 16(%r14)
                    movl $0, 20(%r14)
                    leaq 24(%r14), %rdi
                    movq %r12, %rsi
                    movl %r13d, %edx
                    call kof_memcpy
                    movb $0, 24(%r14,%r13)
                    movq %r14, %rax
                    popq %r14
                    popq %r13
                    popq %r12
                    ret
                .Lffc_null:
                    xorl %eax, %eax
                    ret
                """);
    }

    /** Largura (bytes) do elemento de um array escalar — igual à largura C do
     *  char ('b'→1, 'i'/'f'→4, 'j'/'d'→8), então o pack é um memcpy direto. */
    private static int arrayElemSize(char elem) {
        return switch (elem) {
            case 'b' -> 1;
            case 'i', 'f' -> 4;
            default -> 8;
        };
    }

    /**
     * D6-2/3.7: empacota um array Kof de escalares num buffer C contíguo —
     * copy-in por chamada, paridade com o JVM (o array Kof nunca é mutado pela
     * C; escritas são descartadas). {@code %rdi} = objeto array, {@code %rsi} =
     * tamanho do elemento em bytes; retorno {@code %rax} = buffer
     * ({@code kof_alloc}). Layout Kof do array: len em 16(obj), payload em 24.
     * Definido uma vez por programa quando um extern recebe array (o call-site o
     * referencia).
     */
    static void emitX86ArrayPackHelper(StringBuilder sb) {
        sb.append("""
                .globl kof_ffi_pack_array
                .type kof_ffi_pack_array, @function
                kof_ffi_pack_array:
                    pushq %rbx
                    pushq %r12
                    pushq %r13
                    pushq %r14
                    subq $8, %rsp
                    movq %rdi, %rbx
                    movq %rsi, %r13
                    movl 16(%rbx), %r12d
                    movq %r12, %rdi
                    imulq %r13, %rdi
                    testq %rdi, %rdi
                    jne .Lfpa_alloc
                    movq $8, %rdi
                .Lfpa_alloc:
                    call kof_alloc
                    movq %rax, %r14
                    leaq 24(%rbx), %rsi
                    movq %r14, %rdi
                    movq %r12, %rdx
                    imulq %r13, %rdx
                    testq %rdx, %rdx
                    je .Lfpa_done
                    call kof_memcpy
                .Lfpa_done:
                    movq %r14, %rax
                    addq $8, %rsp
                    popq %r14
                    popq %r13
                    popq %r12
                    popq %rbx
                    ret
                """);
    }

}
