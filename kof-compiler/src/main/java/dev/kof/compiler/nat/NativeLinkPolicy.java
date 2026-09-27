package dev.kof.compiler.nat;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import dev.kof.compiler.KofLoadLiteral;
import dev.kof.compiler.KofOperation;

/**
 * Política de link nativo: o gate NATIVE003 do perfil freestanding (B-1) e a
 * detecção de wire MySQL por URL literal (link-by-use). Extraída de
 * NativeBackend pelo gate ≤500 (a classe cruzou 600 com o perfil UEFI, B-2);
 * responsabilidade própria: o que pode linkar, com quais libs, por quê.
 */
final class NativeLinkPolicy {

    private NativeLinkPolicy() {}

    /** Delega o assemble/link ao NativeAssembler (script/objcopy por perfil). */
    static void assemble(NativeBackend backend, Path asmFile, Path binFile) throws IOException {
        // B-1: no perfil freestanding (x86_64) o link é estático e sem libc —
        // qualquer capacidade que precise de libc é RECUSADA com diagnóstico
        // (nunca um link que falha feio nem um binário que resolve em runtime).
        if (backend.freestanding && backend.target == dev.kof.compiler.Target.NATIVE
                && (backend.usesDb || backend.usesOrm || backend.usesMysql || backend.usesConcurrency
                        || backend.usesPow || backend.usesProcess || !backend.ffiLibs.isEmpty())) {
            throw new IOException("NATIVE003: perfil freestanding nao suporta libc (db/mysql/concurrency/pow/ffi/process) "
                    + "neste alvo; use o perfil host ou remova a dependencia");
        }
        // R2 fatia 1 (20/09): -lm AGORA é by-use como sqlite/mariadb/pthread —
        // o shim `call pow` do monolito virou WEAK (RuntimeMath `.weak pow`),
        // então linkar sem libm fecha; usaPow só quando a fonte chama
        // kof_math_pow (scan acima — único caminho ao shim). A história do
        // 7f174a6f (arg morto, link incondicional) mora aqui.
        NativeAssembler.assemble(asmFile, binFile, backend.usesDb || backend.usesOrm,
                backend.usesConcurrency, backend.ffiLibs, backend.usesPow, backend.freestanding);
    }

    /** Detecta o protocolo do URL de conexão quando é um literal em
     *  compile-time (intenção conhecida pelo compilador). Desde §523 (27/09)
     *  o wire mysql é auto-contido e NÃO exige lib no link (S0 recusa NOMEADA
     *  no runtime para o resto); o flag restante serve ao gate freestanding
     *  (sem sockets sem libc). URLs dinâmicos seguem conservadores (true). */
    static boolean connectsToMysql(int callIndex, List<KofOperation> ops) {
        for (int j = callIndex - 1; j >= 0 && j >= callIndex - 8; j--) {
            if (ops.get(j) instanceof KofLoadLiteral lit && lit.value() instanceof String url) {
                String u = url.toLowerCase();
                // §523: sem link-by-use de libmariadb — o wire nunca chamou a
                // lib (zero simbolo, medido via readelf); sem a flag, a prova
                // hermética linka em host sem a lib (CI).
                return u.startsWith("mysql://") || u.startsWith("mariadb://")
                        || u.startsWith("jdbc:mysql://");
            }
        }
        return true;
    }
}
