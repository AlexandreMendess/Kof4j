package dev.kof.compiler.jvm;

/**
 * §278: runtime de {@code kof.gpu} para o alvo ANDROID — stub SEM FFM.
 *
 * O Android roda o MESMO backend JVM e o mesmo front/IR (o {@code Main.class}
 * e byte-a-byte igual ao do JVM), mas o ART nao tem {@code java.lang.foreign}
 * (Project Panama), entao o bloco FFM de {@link JvmVkRuntime} nem carregaria no
 * dispositivo. Este stub mantem as 13 entry points {@code kof_vk_*}/ {@code
 * kof_mv64_*} com as MESMAS assinaturas e degrada honestamente: {@code
 * available()=false} e dispatch retornando o codigo de fallback que o caller
 * usa para cair no golden CPU — nunca um resultado errado em silencio (R6),
 * nunca uma excecao. Isso espelha o contrato ja aceito dos alvos nativos.
 */
public final class JvmVkStubRuntime {

    private JvmVkStubRuntime() {}

    static String source() {
        return """
                // ── kof.gpu no ANDROID — stub SEM FFM (ART nao tem java.lang.foreign) ──
                // O front/IR e o mesmo do JVM (Main.class byte-a-byte); aqui o runtime
                // degrada honestamente: available=false e dispatch devolve o fallback
                // (nao-zero) p/ o caller usar o golden CPU. Nunca cai, nunca mente.
                public static boolean kof_vk_available() {
                    return false;
                }

                public static String kof_vk_fail_reason() {
                    return "gpu: sem Vulkan/FFM no Android (fallback CPU)";
                }

                public static int kof_vk_dispatch(int[] a, int[] b, int[] c, int m, int n, int k) {
                    return -1;
                }

                public static int kof_vk_dispatch64(long[] a, long[] b, long[] c, int m, int n, int k) {
                    return -1;
                }

                public static int kof_mv64_set_shape(int m, int k) {
                    return -1;
                }

                public static int kof_mv64_load_w(long[] w, int m, int k) {
                    return -1;
                }

                public static int kof_mv64_matvec(long[] x, long[] y, int m, int k) {
                    return -1;
                }

                public static int kof_mv64_wput(int id, long[] w, int m, int k) {
                    return -1;
                }

                public static int kof_mv64_wrun(int id, long[] x, long[] y, int m, int k, long div) {
                    return -1;
                }

                public static int kof_mv64_wput32(int id, int[] w, int m, int k) {
                    return -6;
                }

                public static int kof_mv64_wrun32(int id, long[] x, long[] y, int m, int k, long div) {
                    return -6;
                }

                public static int kof_mv64_wputsp(int id, int[] wh, int[] wl, int m, int k) {
                    return -6;
                }

                public static int kof_mv64_wrunsp(int id, long[] x, long[] y, int m, int k, long div) {
                    return -6;
                }
            }""";
    }
}
