package dev.kof.compiler.nat;

import dev.kof.compiler.runtime.RuntimeDb1;
import dev.kof.compiler.runtime.RuntimeDb2;
import dev.kof.compiler.runtime.RuntimeDb3;
import dev.kof.compiler.runtime.RuntimeDb4;
import dev.kof.compiler.runtime.RuntimeDb5;
import dev.kof.compiler.runtime.RuntimeDb6;

/**
 * Fatias dos runtimes Native DB/ORM (D-DB-GAPS F1..F3): ordem de emissao
 * dos globls {@code kof_db_*}/{@code kof_orm_*} e as janelas de
 * {@code @@MAGIC@@} (box de erasure §284) por fatia que usa o resolver de
 * ctors. Extraido de {@code NativeBackend} no gate 500 (21/09) — refactor
 * de estrutura, nunca comportamento (regra 3 do freeze): mesma sequencia,
 * mesmos buffers, mesma ordem.
 */
public final class NativeOrmEmit {

    private NativeOrmEmit() {}

    /**
     * Emite as fatias dos runtimes DB/ORM (F1..F3 do GAPS-DB) no buffer.
     * Extraido do backend (gate 500, 21/09): a sequencia de globls e as
     * janelas de @@MAGIC@@ por fatia moram aqui; a ordem nao muda nada
     * (refactor de estrutura, nunca comportamento — regra 3 do freeze).
     */
    static void emitRuntimeSlices(NativeBackend backend, StringBuilder sb) {
        if (backend.usesDb || backend.usesOrm) {
            RuntimeDb1.emit(sb);
            RuntimeDb2.emit(sb);
            RuntimeDb3.emit(sb);
            RuntimeDb4.emit(sb);
            RuntimeDb5.emit(sb);
            RuntimeDb6.emit(sb);
            NativeDbPrepared.emitMysqlPrepared(sb);
        }
        if (backend.usesOrm) {
            dev.kof.compiler.runtime.RuntimeOrm1.emit(sb);
            dev.kof.compiler.runtime.RuntimeOrmMysql.emit(sb);
            dev.kof.compiler.runtime.RuntimeOrm2.emit(sb);
            dev.kof.compiler.runtime.RuntimeOrm2.emitMysql(sb);
            dev.kof.compiler.runtime.RuntimeOrmMysqlDdl.emit(sb);
            StringBuilder o3 = new StringBuilder();
            dev.kof.compiler.runtime.RuntimeOrm3.emit(o3);
            sb.append(o3.toString().replace("@@MAGIC@@",
                    dev.kof.compiler.runtime.RuntimeErasureBox.MAGIC));
            dev.kof.compiler.runtime.RuntimeOrmSchema.emit(sb);
            dev.kof.compiler.runtime.RuntimeOrmBind.emit(sb);
            dev.kof.compiler.runtime.RuntimeOrm4.emit(sb);
            if (!backend.ormCtorClasses.isEmpty()) {
                StringBuilder ofm = new StringBuilder();
                dev.kof.compiler.runtime.RuntimeOrmMysqlFind.emit(ofm);
                sb.append(ofm.toString().replace("@@MAGIC@@",
                        dev.kof.compiler.runtime.RuntimeErasureBox.MAGIC));
                StringBuilder okl = new StringBuilder();
                dev.kof.compiler.runtime.RuntimeOrmMysqlKeyLit.emit(okl);
                sb.append(okl.toString().replace("@@MAGIC@@",
                        dev.kof.compiler.runtime.RuntimeErasureBox.MAGIC));
                StringBuilder oam = new StringBuilder();
                dev.kof.compiler.runtime.RuntimeOrmMysqlAll.emit(oam);
                sb.append(oam.toString().replace("@@MAGIC@@",
                        dev.kof.compiler.runtime.RuntimeErasureBox.MAGIC));
                StringBuilder owm = new StringBuilder();
                dev.kof.compiler.runtime.RuntimeOrmMysqlWhere.emit(owm);
                sb.append(owm.toString().replace("@@MAGIC@@",
                        dev.kof.compiler.runtime.RuntimeErasureBox.MAGIC));
                StringBuilder oom = new StringBuilder();
                dev.kof.compiler.runtime.RuntimeOrmMysqlOp.emit(oom);
                sb.append(oom.toString().replace("@@MAGIC@@",
                        dev.kof.compiler.runtime.RuntimeErasureBox.MAGIC));
                StringBuilder opm = new StringBuilder();
                dev.kof.compiler.runtime.RuntimeOrmMysqlPage.emit(opm);
                sb.append(opm.toString().replace("@@MAGIC@@",
                        dev.kof.compiler.runtime.RuntimeErasureBox.MAGIC));
                StringBuilder o5 = new StringBuilder();
                dev.kof.compiler.runtime.RuntimeOrm5.emit(o5);
                sb.append(o5.toString().replace("@@MAGIC@@",
                        dev.kof.compiler.runtime.RuntimeErasureBox.MAGIC));
                dev.kof.compiler.runtime.RuntimeOrm6.emit(sb);
                StringBuilder o7 = new StringBuilder();
                dev.kof.compiler.runtime.RuntimeOrm7.emit(o7);
                sb.append(o7.toString().replace("@@MAGIC@@",
                        dev.kof.compiler.runtime.RuntimeErasureBox.MAGIC));
                StringBuilder o8 = new StringBuilder();
                dev.kof.compiler.runtime.RuntimeOrm8.emit(o8);
                sb.append(o8.toString().replace("@@MAGIC@@",
                        dev.kof.compiler.runtime.RuntimeErasureBox.MAGIC));
                NativeOrmCtors.emit(backend, sb, backend.ormCtorClasses);
            }
            StringBuilder o9 = new StringBuilder();
            dev.kof.compiler.runtime.RuntimeOrm9.emit(o9);
            sb.append(o9.toString().replace("@@MAGIC@@",
                    dev.kof.compiler.runtime.RuntimeErasureBox.MAGIC));
            dev.kof.compiler.runtime.RuntimeOrm10.emit(sb);
            // kof_orm_save_all sempre despacha p/ mysql (F2d4d): fatias
            // lit/exec/saveAll acompanham o Orm10, fora do gate de
            // ormCtorClasses (o dispatch e incondicional). F2d5: idem para
            // kof_orm_save (save + lastid).
            StringBuilder osl = new StringBuilder();
            dev.kof.compiler.runtime.RuntimeOrmMysqlFieldLit.emit(osl);
            sb.append(osl.toString().replace("@@MAGIC@@",
                    dev.kof.compiler.runtime.RuntimeErasureBox.MAGIC));
            StringBuilder ose = new StringBuilder();
            dev.kof.compiler.runtime.RuntimeOrmMysqlExec.emit(ose);
            sb.append(ose.toString().replace("@@MAGIC@@",
                    dev.kof.compiler.runtime.RuntimeErasureBox.MAGIC));
            StringBuilder osm = new StringBuilder();
            dev.kof.compiler.runtime.RuntimeOrmMysqlSaveAll.emit(osm);
            sb.append(osm.toString().replace("@@MAGIC@@",
                    dev.kof.compiler.runtime.RuntimeErasureBox.MAGIC));
            StringBuilder ols = new StringBuilder();
            dev.kof.compiler.runtime.RuntimeOrmMysqlLastId.emit(ols);
            sb.append(ols.toString().replace("@@MAGIC@@",
                    dev.kof.compiler.runtime.RuntimeErasureBox.MAGIC));
            StringBuilder osv = new StringBuilder();
            dev.kof.compiler.runtime.RuntimeOrmMysqlSave.emit(osv);
            sb.append(osv.toString().replace("@@MAGIC@@",
                    dev.kof.compiler.runtime.RuntimeErasureBox.MAGIC));
            // F2d6: count com filtro (kof_orm_count_where despacha sempre).
            StringBuilder ocw = new StringBuilder();
            dev.kof.compiler.runtime.RuntimeOrmMysqlCountWhere.emit(ocw);
            sb.append(ocw.toString().replace("@@MAGIC@@",
                    dev.kof.compiler.runtime.RuntimeErasureBox.MAGIC));
        }
    }
}
