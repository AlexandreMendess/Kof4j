package dev.kof.compiler.js;
import dev.kof.compiler.KofBinary;
import dev.kof.compiler.KofLoadLiteral;
import dev.kof.compiler.KofUnary;
import dev.kof.compiler.Type;

import java.util.List;

/**
 * JsOperatorEmitter — lowering dos operadores e literais JS: binários (§81
 * BigInt/Long, wrap 32-bit, Bool por conteudo), unarios (#471 I2B/I2S;
 * §81/§167/§181 conversoes) e literais (bool/Long-as-BigInt/Float/Double/
 * String). Extraido do JsCallEmitter no §344 (gate ≤500) — mesma
 * responsabilidade "operadores" da fase 4 do REFACTOR-500; comportamento
 * IDENTICO ao byte-a-byte da extracao (prova: suites JS golden + paridade).
 */
final class JsOperatorEmitter {

    private final JsMethodParser p;

    JsOperatorEmitter(JsMethodParser p) {
        this.p = p;
    }

    JsIr.JsExpression binaryExpr(KofBinary kb, JsIr.JsExpression left, JsIr.JsExpression right) {
        // §81 (5b): binário de LONG no JS = BigInt. Os lados podem chegar como
        // Number (literal Int promovido, var Int) — BigInt() é idempotente e
        // garante a promoção Int->Long do JVM (mistura BigInt/Number lança).
        if (JsTypeMapper.isLongType(kb.operandType())) return JsLongEmitter.longBinaryExpr(kb, left, right);
        return switch (kb.op()) {
            case ADD -> intWrap(kb.operandType(), new JsIr.JsBinary(left, "+", right));
            case SUB -> intWrap(kb.operandType(), new JsIr.JsBinary(left, "-", right));
            case MUL -> intWrap(kb.operandType(), new JsIr.JsBinary(left, "*", right));
            case DIV -> {
                if (JsTypeMapper.isIntFamily(kb.operandType())) {
                    yield intWrap(kb.operandType(),
                            new JsIr.JsBinary(left, "/", divZeroGuard(right)));
                }
                yield new JsIr.JsBinary(left, "/", right);
            }
            case MOD -> JsTypeMapper.isIntFamily(kb.operandType())
                    // §693: o `%` inteiro tem a MESMA armadilha do `/` (no
                    // JVM os dois lançam; no JS o `x % 0` é NaN e o `| 0` do
                    // intWrap o transformava num `0` silencioso).
                    ? intWrap(kb.operandType(), new JsIr.JsBinary(left, "%", divZeroGuard(right)))
                    : new JsIr.JsBinary(left, "%", right);
            case EQ -> JsTypeMapper.isBoolOperand(kb.operandType()) || JsTypeMapper.isBoolLiteral(left) || JsTypeMapper.isBoolLiteral(right)
                    ? boolEq(left, right, true)
                    : new JsIr.JsBinary(left, "===", right);
            case NE -> JsTypeMapper.isBoolOperand(kb.operandType()) || JsTypeMapper.isBoolLiteral(left) || JsTypeMapper.isBoolLiteral(right)
                    ? boolEq(left, right, false)
                    : new JsIr.JsBinary(left, "!==", right);
            case LT -> new JsIr.JsBinary(left, "<", right);
            case LE -> new JsIr.JsBinary(left, "<=", right);
            case GT -> new JsIr.JsBinary(left, ">", right);
            case GE -> new JsIr.JsBinary(left, ">=", right);
            // #486: o `&&`/`||` do JavaScript devolve o OPERANDO (`true && null`
            // → `null`), mas o tipo KOF da expressao logica e `Bool` — um RHS
            // `Bool?` nulo vazava `null` para o consumidor. O `!!` materializa
            // um `Boolean` SEM tocar na avaliacao lazy (o operador nativo segue
            // dentro, entao `false && rhs()` continua nao avaliando `rhs`).
            // Nao usar `kofBoolValueOf`: ele preserva `null` (semantica de
            // `Bool?`), que e justamente o que nao pode escapar aqui. Os
            // caminhos bitwise `&`/`|` ficam intactos (guard `isBoolOperand`).
            case AND -> JsTypeMapper.isBoolOperand(kb.operandType())
                    ? new JsIr.JsUnary("!!", new JsIr.JsBinary(left, "&&", right))
                    : new JsIr.JsBinary(left, "&", right);
            case OR -> JsTypeMapper.isBoolOperand(kb.operandType())
                    ? new JsIr.JsUnary("!!", new JsIr.JsBinary(left, "||", right))
                    : new JsIr.JsBinary(left, "|", right);
            case XOR -> new JsIr.JsBinary(left, "^", right);
            // §167: `int << long` tem tipo int (JLS 15.19) mas o RHS pode
            // chegar como BigInt (literal Long ou var Long) → TypeError no JS.
            // Normaliza o contador p/ Number 32-bit (o JS já mascara em 0x1f).
            case SHL -> new JsIr.JsBinary(JsLongEmitter.int32(left), "<<", JsLongEmitter.toNumber32(right));
            case SHR -> new JsIr.JsBinary(JsLongEmitter.int32(left), ">>", JsLongEmitter.toNumber32(right));
            // §698 face JS: o `>>>` do JS devolve 32 bits SEM SINAL, então
            // `-5 >>> 0` dava 4294967291 em vez de -5 (o JVM devolve o int).
            // O `int32` externo re-interpreta o resultado como signed.
            case USHR -> JsLongEmitter.int32(new JsIr.JsBinary(
                    JsLongEmitter.int32(left), ">>>", JsLongEmitter.toNumber32(right)));
        };
    }

    /**
     * Kof Int is a signed 32-bit type; JavaScript numbers are doubles. Wrap
     * int arithmetic with ToInt32 (| 0) to preserve Kof/JVM 32-bit semantics.
     */
    /** §693: a divisão INTEIRA por um zero de RUNTIME. O `/` do JS é a divisão
     *  IEEE do host (`10 / 0` = `Infinity`, e o `| 0` do {@link #intWrap} o
     *  transformava num `0` silencioso) enquanto o JVM e o Script LANÇAM e o
     *  `try`/`catch` do Kof recupera — o contrato. O helper vive num bloco
     *  PRÓPRIO do runtime (a mesma forma do `kofCastFail`: o IR não tem
     *  expressão condicional, e o bloco CORE quebrava o slicing). */
    private JsIr.JsExpression divZeroGuard(JsIr.JsExpression divisor) {
        p.lc.registerRuntime("kofDivZero");
        return new JsIr.JsCall(new JsIr.JsIdentifier("kofDivZero"), List.of(divisor));
    }

    JsIr.JsExpression intWrap(Type operandType, JsIr.JsExpression inner) {
        if (JsTypeMapper.isIntFamily(operandType)) {
            return new JsIr.JsBinary(inner, "|", new JsIr.JsNumber("0"));
        }
        return inner;
    }

    /**
     * §93 paridade Bool no JS: uma expressão Bool pode chegar como 1/0 (funções
     * stdlib, instanceof, predicados de coleção) ou true/false (literais). O
     * === cru faz 1===true ser false. Normaliza ambos os lados com !! (ToBoolean)
     * para casar a semântica de conteúdo do == de Kof com JVM/Native (que usam Z).
     */
JsIr.JsExpression boolEq(JsIr.JsExpression left, JsIr.JsExpression right, boolean eq) {
        JsIr.JsExpression l = new JsIr.JsUnary("!!", left);
        JsIr.JsExpression r = new JsIr.JsUnary("!!", right);
        return new JsIr.JsBinary(l, eq ? "===" : "!==", r);
    }

JsIr.JsExpression unaryExpr(KofUnary ku, JsIr.JsExpression operand) {
        return switch (ku.op()) {
            case NEG -> JsTypeMapper.isLongType(ku.operandType())
                    ? JsLongEmitter.wrap64(new JsIr.JsUnary("-", JsLongEmitter.longOperand(operand)))
                    : new JsIr.JsUnary("-", operand);
            case NOT -> new JsIr.JsConditional(operand, new JsIr.JsNumber("0"), new JsIr.JsNumber("1"));
            case I2F, I2D, L2F, L2D, F2D, D2F -> operand;
            // §697 face JS: `as Char` MASCA para 16 bits ZERO-estendidos. O
            // I2C estava na lista de IDENTIDADE acima, então o cast era um
            // no-op no JS: `(70000 as Char) as Int` dava 70000 (o JVM/Script/
            // nativo dão 4464 = 70000 & 0xFFFF) e `(-1 as Char) as Int` dava
            // -1 em vez de 65535. O `&` do JS trunca em 32 bits signed, então
            // o resultado fica em 0..65535 — que é o Char.
            case I2C -> new JsIr.JsBinary(operand, "&", new JsIr.JsNumber("65535"));
            // #471: i2b/i2s no JS = wrap signed 8/16 bits em Number 32-bit
            // (mesmo truque de mask do kofArraySet §184; a pilha aqui ja e
            // int de 32 bits — L2I/F2I/D2I rodam antes).
            case I2B -> new JsIr.JsBinary(
                    new JsIr.JsBinary(new JsIr.JsBinary(operand, "<<",
                            new JsIr.JsNumber("24")), ">>", new JsIr.JsNumber("24")),
                    "|", new JsIr.JsNumber("0"));
            case I2S -> new JsIr.JsBinary(
                    new JsIr.JsBinary(new JsIr.JsBinary(operand, "<<",
                            new JsIr.JsNumber("16")), ">>", new JsIr.JsNumber("16")),
                    "|", new JsIr.JsNumber("0"));
            case I2L -> new JsIr.JsCall(new JsIr.JsIdentifier("BigInt"), List.of(operand));   // §81
            // §81/§167: Long(BigInt)->Int — truncamento EXATO sobre BigInt
            // (BigInt.asIntN(32,...) faz o wrap signed do JVM; Number() direto
            // perderia precisão >2^53 e daria 0 onde o JVM dá 1). O resultado
            // volta a Number: Int no JS é Number, e um BigInt fluindo p/
            // aritmética Int lançava `Cannot mix BigInt and other types` (§167).
            case L2I -> new JsIr.JsCall(new JsIr.JsIdentifier("Number"),
                    List.of(new JsIr.JsCall(
                            new JsIr.JsMember(new JsIr.JsIdentifier("BigInt"), "asIntN"),
                            List.of(new JsIr.JsNumber("32"), operand))));
            // §181 (13/09): saturação JLS 5.1.3 via helpers do runtime —
            // Math.trunc cru divergia do JVM (3e9, NaN, Infinity).
            // registerRuntime é OBRIGATÓRIO (sem isso o helper não entra no
            // kof-runtime.mjs — ReferenceError na execução).
            case D2I -> {
                p.lc.registerRuntime("kofD2I");
                yield new JsIr.JsCall(new JsIr.JsIdentifier("kofD2I"), List.of(operand));
            }
            case F2I -> {
                p.lc.registerRuntime("kofF2I");
                yield new JsIr.JsCall(new JsIr.JsIdentifier("kofF2I"), List.of(operand));
            }
            case D2L -> {
                p.lc.registerRuntime("kofD2L");
                yield new JsIr.JsCall(new JsIr.JsIdentifier("kofD2L"), List.of(operand));
            }
            case F2L -> {
                p.lc.registerRuntime("kofF2L");
                yield new JsIr.JsCall(new JsIr.JsIdentifier("kofF2L"), List.of(operand));
            }
        };
    }

JsIr.JsExpression literalExpr(KofLoadLiteral lit) {
        if (lit.type() instanceof Type.PrimitiveType pt
                && "bool".equals(Type.canonicalPrimitiveName(pt.name()))) {
            Object v = lit.value();
            return new JsIr.JsIdentifier((v instanceof Integer i && i != 0) ? "true" : "false");
        }
        if (lit.value() instanceof Integer i) return new JsIr.JsNumber(Integer.toString(i));
        // §81 (5b, 13/09): Long no JS = BigInt (paridade 64-bit real); o
        // sufixo `n` fabrica o literal BigInt.
        if (lit.value() instanceof Long l) return new JsIr.JsNumber(Long.toString(l) + "n");
        if (lit.value() instanceof Float f) return new JsIr.JsNumber(Float.toString(f));
        if (lit.value() instanceof Double d) return new JsIr.JsNumber(Double.toString(d));
        if (lit.value() instanceof String s) return new JsIr.JsString(s);
        return new JsIr.JsNull();
    }
}
