package dev.kof.compiler.nat;

// S5.4 fatia 2 (db-parity-plan, gaps-db lane, 24/09): o dispatch de
// execute/query do cross — os corpos gerados por aridade (`kof_db_execute[N]`/
// `kof_db_query[N]`) com o ramo mysql ao lado do sqlite. Extraídos da B47
// (que ficou só com resolve/type/connect/close/bind/transaction) porque o
// dispatch mysql estourava o frame de linhas da peça; aqui o ramo mysql é
// magro — monta os binds e delega a `kof_db_mysql_execute_from`/
// `kof_db_mysql_query_from` (substituição client-side B71 + B72/B70).
//
// O ramo sqlite é idêntico ao que vivia na B47 (port Db4/Db5); o ramo mysql
// resolve o fd pelo `kof_db_resolve` (registrado pela B73 como type 2).
public final class NativeRiscvAsmRtB47b {

    private NativeRiscvAsmRtB47b() {}

    /** kof_db_execute[N](id@a0, sql@a1, b1..bN@a2..) -> rows changed.
     *  Dispatch por `kof_db_type`: 2 (mysql) -> B71 substitui + B72;
     *  senão o ramo sqlite (port Db4 KOF_DB_EXEC_N). */
    private static String executeN(int n) {
        String fn = n == 0 ? "kof_db_execute" : "kof_db_execute" + n;
        StringBuilder sb = new StringBuilder();
        sb.append("            .globl ").append(fn).append("\n");
        sb.append(fn).append(":\n");
        sb.append("            addi sp, sp, -112\n");
        sb.append(saveS());
        sb.append("            mv   s0, a1\n");                    // sql
        for (int i = 1; i <= n; i++) sb.append("            mv   s").append(i).append(", a").append(i + 1).append("\n");
        sb.append("            mv   s9, a0\n");                    // id (p/ kof_db_type)
        sb.append("            call kof_db_resolve\n");           // a0 = id
        sb.append("            mv   s5, a0\n");                   // db
        sb.append("            beqz s5, .Lex").append(n).append("_bad\n");
        sb.append("            mv   a0, s9\n");
        sb.append("            call kof_db_type\n");
        sb.append("            li   t0, 2\n");
        sb.append("            beq  a0, t0, .Lex").append(n).append("_mysql\n");
        sb.append("            sd   zero, 0(sp)\n");              // &stmt
        sb.append("            mv   a0, s5\n");
        sb.append("            addi a1, s0, 24\n");
        sb.append("            li   a2, -1\n");
        sb.append("            mv   a3, sp\n");
        sb.append("            li   a4, 0\n");
        sb.append("            call sqlite3_prepare_v2\n");
        for (int i = 1; i <= n; i++) {
            sb.append("            ld   a0, 0(sp)\n");
            sb.append("            li   a1, ").append(i).append("\n");
            sb.append("            mv   a2, s").append(i).append("\n");
            sb.append("            call kof_db_bind\n");
        }
        sb.append("            ld   a0, 0(sp)\n");
        sb.append("            call sqlite3_step\n");
        sb.append("            ld   a0, 0(sp)\n");
        sb.append("            call sqlite3_finalize\n");
        sb.append("            mv   a0, s5\n");
        sb.append("            call sqlite3_changes\n");
        sb.append("            j    .Lex").append(n).append("_out\n");
        sb.append("        .Lex").append(n).append("_mysql:\n");
        sb.append("            mv   a0, s5\n");                   // fd
        sb.append("            mv   a1, s0\n");                   // sql
        for (int i = 1; i <= n; i++) sb.append("            mv   a").append(1 + i).append(", s").append(i).append("\n");
        sb.append("            li   a6, ").append(n).append("\n");
        sb.append("            call kof_db_mysql_execute_from\n");
        sb.append("            j    .Lex").append(n).append("_out\n");
        sb.append("        .Lex").append(n).append("_bad:\n");
        sb.append("            li   a0, 0\n");
        sb.append("        .Lex").append(n).append("_out:\n");
        sb.append(restoreS());
        sb.append("            addi sp, sp, 112\n");
        sb.append("            ret\n\n");
        return sb.toString();
    }

    /** kof_db_query[N](id@a0, sql@a1, b1..bN@a2.., className@a(2+N)) -> List*.
     *  Dispatch por `kof_db_type`: 2 -> B71 + B70; senão o ramo sqlite (Db5). */
    private static String queryN(int n) {
        String fn = n == 0 ? "kof_db_query0" : "kof_db_query" + n;
        StringBuilder sb = new StringBuilder();
        sb.append("            .globl ").append(fn).append("\n");
        sb.append(fn).append(":\n");
        sb.append("            addi sp, sp, -112\n");
        sb.append(saveS());
        sb.append("            mv   s0, a1\n");
        for (int i = 1; i <= n; i++) sb.append("            mv   s").append(i).append(", a").append(i + 1).append("\n");
        sb.append("            mv   s9, a0\n");
        sb.append("            call kof_db_resolve\n");
        sb.append("            mv   s5, a0\n");
        sb.append("            beqz s5, .Lq").append(n).append("_bad\n");
        sb.append("            mv   a0, s9\n");
        sb.append("            call kof_db_type\n");
        sb.append("            li   t0, 2\n");
        sb.append("            beq  a0, t0, .Lq").append(n).append("_mysql\n");
        sb.append("            sd   zero, 0(sp)\n");
        sb.append("            mv   a0, s5\n");
        sb.append("            addi a1, s0, 24\n");
        sb.append("            li   a2, -1\n");
        sb.append("            mv   a3, sp\n");
        sb.append("            li   a4, 0\n");
        sb.append("            call sqlite3_prepare_v2\n");
        for (int i = 1; i <= n; i++) {
            sb.append("            ld   a0, 0(sp)\n");
            sb.append("            li   a1, ").append(i).append("\n");
            sb.append("            mv   a2, s").append(i).append("\n");
            sb.append("            call kof_db_bind\n");
        }
        sb.append("            call kof_list_new\n");
        sb.append("            mv   s6, a0\n");
        sb.append("        .Lq").append(n).append("_row:\n");
        sb.append("            ld   a0, 0(sp)\n");
        sb.append("            call sqlite3_step\n");
        sb.append("            li   t0, 100\n");                  // SQLITE_ROW
        sb.append("            bne  a0, t0, .Lq").append(n).append("_done\n");
        sb.append("            call kof_json_builder_new\n");
        sb.append("            mv   s7, a0\n");
        sb.append("            mv   a0, s7\n");
        sb.append("            li   a1, 123\n");                  // '{'
        sb.append("            call kof_json_builder_char\n");
        sb.append("            li   s8, 0\n");                    // col idx
        sb.append("        .Lq").append(n).append("_col:\n");
        sb.append("            ld   a0, 0(sp)\n");
        sb.append("            call sqlite3_column_count\n");
        sb.append("            bge  s8, a0, .Lq").append(n).append("_endrow\n");
        sb.append("            beqz s8, .Lq").append(n).append("_nocomma\n");
        sb.append("            mv   a0, s7\n");
        sb.append("            li   a1, 44\n");                   // ','
        sb.append("            call kof_json_builder_char\n");
        sb.append("        .Lq").append(n).append("_nocomma:\n");
        // nome da coluna: char* -> strlen -> KofStr -> escape JSON -> builder
        sb.append("            ld   a0, 0(sp)\n");
        sb.append("            mv   a1, s8\n");
        sb.append("            call sqlite3_column_name\n");
        sb.append("            sd   a0, 80(sp)\n");
        sb.append("            call kof_io_strlen\n");
        sb.append("            mv   a1, a0\n");
        sb.append("            ld   a0, 80(sp)\n");
        sb.append("            call kof_io_make_string\n");
        sb.append("            call kof_json_encode_string\n");
        sb.append("            mv   a1, a0\n");
        sb.append("            mv   a0, s7\n");
        sb.append("            call kof_json_builder_str\n");
        sb.append("            mv   a0, s7\n");
        sb.append("            li   a1, 58\n");                   // ':'
        sb.append("            call kof_json_builder_char\n");
        // valor pelo tipo da coluna
        sb.append("            ld   a0, 0(sp)\n");
        sb.append("            mv   a1, s8\n");
        sb.append("            call sqlite3_column_type\n");
        sb.append("            li   t0, 1\n");                    // SQLITE_INTEGER
        sb.append("            beq  a0, t0, .Lq").append(n).append("_int\n");
        sb.append("            li   t0, 3\n");                    // SQLITE_TEXT
        sb.append("            beq  a0, t0, .Lq").append(n).append("_text\n");
        // NULL -> literal `null` (divergência corrigida vs x86 — cabeçalho)
        sb.append("            la   a0, .Ldb_null\n");
        sb.append("            li   a1, 4\n");
        sb.append("            call kof_io_make_string\n");
        sb.append("            j    .Lq").append(n).append("_val\n");
        sb.append("        .Lq").append(n).append("_int:\n");
        sb.append("            ld   a0, 0(sp)\n");
        sb.append("            mv   a1, s8\n");
        sb.append("            call sqlite3_column_int\n");
        sb.append("            call kof_json_encode_int\n");
        sb.append("            j    .Lq").append(n).append("_val\n");
        sb.append("        .Lq").append(n).append("_text:\n");
        sb.append("            ld   a0, 0(sp)\n");
        sb.append("            mv   a1, s8\n");
        sb.append("            call sqlite3_column_text\n");
        sb.append("            sd   a0, 80(sp)\n");
        sb.append("            call kof_io_strlen\n");
        sb.append("            mv   a1, a0\n");
        sb.append("            ld   a0, 80(sp)\n");
        sb.append("            call kof_io_make_string\n");
        sb.append("            call kof_json_encode_string\n");
        sb.append("        .Lq").append(n).append("_val:\n");
        sb.append("            mv   a1, a0\n");
        sb.append("            mv   a0, s7\n");
        sb.append("            call kof_json_builder_str\n");
        sb.append("            addi s8, s8, 1\n");
        sb.append("            j    .Lq").append(n).append("_col\n");
        sb.append("        .Lq").append(n).append("_endrow:\n");
        sb.append("            mv   a0, s7\n");
        sb.append("            li   a1, 125\n");                  // '}'
        sb.append("            call kof_json_builder_char\n");
        sb.append("            mv   a0, s7\n");
        sb.append("            call kof_json_builder_result\n");
        sb.append("            mv   a1, a0\n");
        sb.append("            mv   a0, s6\n");
        sb.append("            call kof_list_add\n");
        sb.append("            j    .Lq").append(n).append("_row\n");
        sb.append("        .Lq").append(n).append("_done:\n");
        sb.append("            ld   a0, 0(sp)\n");
        sb.append("            call sqlite3_finalize\n");
        sb.append("            mv   a0, s6\n");
        sb.append("            j    .Lq").append(n).append("_out\n");
        sb.append("        .Lq").append(n).append("_mysql:\n");
        sb.append("            mv   a0, s5\n");
        sb.append("            mv   a1, s0\n");
        for (int i = 1; i <= n; i++) sb.append("            mv   a").append(1 + i).append(", s").append(i).append("\n");
        sb.append("            li   a6, ").append(n).append("\n");
        sb.append("            call kof_db_mysql_query_from\n");
        sb.append("            j    .Lq").append(n).append("_out\n");
        sb.append("        .Lq").append(n).append("_bad:\n");
        sb.append("            li   a0, 0\n");
        sb.append("        .Lq").append(n).append("_out:\n");
        sb.append(restoreS());
        sb.append("            addi sp, sp, 112\n");
        sb.append("            ret\n\n");
        return sb.toString();
    }

    /** Save dos s-regs no frame de 112 (s0..s9 @8..96, ra @88). */
    private static String saveS() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i <= 8; i++) sb.append("            sd   s").append(i).append(", ").append(8 + i * 8).append("(sp)\n");
        sb.append("            sd   ra, 88(sp)\n");
        sb.append("            sd   s9, 96(sp)\n");
        return sb.toString();
    }

    private static String restoreS() {
        StringBuilder sb = new StringBuilder();
        sb.append("            ld   s9, 96(sp)\n");
        sb.append("            ld   ra, 88(sp)\n");
        for (int i = 0; i <= 8; i++) sb.append("            ld   s").append(i).append(", ").append(8 + i * 8).append("(sp)\n");
        return sb.toString();
    }

    /** kof_db_mysql_execute_from(a0=fd, a1=sql, a2..a5=b1..b4, a6=n) -> rows.
     *  Substitui os n binds (render B71 + replace_q) na sql e chama a B72. */
    private static final String MYSQL_EXECUTE_FROM = """
            .globl kof_db_mysql_execute_from
            kof_db_mysql_execute_from:
                addi sp, sp, -80
                sd   ra, 72(sp)
                sd   s0, 0(sp)
                sd   s1, 8(sp)
                sd   s2, 16(sp)
                sd   s3, 24(sp)
                sd   a2, 40(sp)
                sd   a3, 48(sp)
                sd   a4, 56(sp)
                sd   a5, 64(sp)
                mv   s0, a0
                mv   s1, a1
                mv   s2, a6
                li   s3, 0
            .Lmx_loop:
                bge  s3, s2, .Lmx_call
                slli t0, s3, 3
                add  t0, sp, t0
                ld   a0, 40(t0)
                call kof_db_mysql_render
                mv   a1, a0
                mv   a0, s1
                call kof_db_mysql_replace_q
                mv   s1, a0
                addi s3, s3, 1
                j    .Lmx_loop
            .Lmx_call:
                mv   a0, s0
                mv   a1, s1
                call kof_db_mysql_execute
                ld   s3, 24(sp)
                ld   s2, 16(sp)
                ld   s1, 8(sp)
                ld   s0, 0(sp)
                ld   ra, 72(sp)
                addi sp, sp, 80
                ret
            """;

    /** kof_db_mysql_query_from(a0=fd, a1=sql, a2..a5=b1..b4, a6=n) -> List*. */
    private static final String MYSQL_QUERY_FROM = """
            .globl kof_db_mysql_query_from
            kof_db_mysql_query_from:
                addi sp, sp, -80
                sd   ra, 72(sp)
                sd   s0, 0(sp)
                sd   s1, 8(sp)
                sd   s2, 16(sp)
                sd   s3, 24(sp)
                sd   a2, 40(sp)
                sd   a3, 48(sp)
                sd   a4, 56(sp)
                sd   a5, 64(sp)
                mv   s0, a0
                mv   s1, a1
                mv   s2, a6
                li   s3, 0
            .Lmq_loop:
                bge  s3, s2, .Lmq_call
                slli t0, s3, 3
                add  t0, sp, t0
                ld   a0, 40(t0)
                call kof_db_mysql_render
                mv   a1, a0
                mv   a0, s1
                call kof_db_mysql_replace_q
                mv   s1, a0
                addi s3, s3, 1
                j    .Lmq_loop
            .Lmq_call:
                mv   a0, s0
                mv   a1, s1
                call kof_db_mysql_query
                ld   s3, 24(sp)
                ld   s2, 16(sp)
                ld   s1, 8(sp)
                ld   s0, 0(sp)
                ld   ra, 72(sp)
                addi sp, sp, 80
                ret
            """;

    static final String RISCV_RUNTIME_ASM_B_47B;

    static {
        StringBuilder sb = new StringBuilder();
        sb.append("            .section .text\n\n");
        for (int n = 0; n <= 4; n++) sb.append(executeN(n));
        for (int n = 0; n <= 4; n++) sb.append(queryN(n));
        sb.append(MYSQL_EXECUTE_FROM).append("\n");
        sb.append(MYSQL_QUERY_FROM);
        RISCV_RUNTIME_ASM_B_47B = sb.toString();
    }
}
