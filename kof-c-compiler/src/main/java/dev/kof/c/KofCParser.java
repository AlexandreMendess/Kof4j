package dev.kof.c;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class KofCParser {
    /** Register-based argument budget shared by the three ISAs (x86_64 SysV has 6). */
    static final int MAX_ARGS = 6;
    /** Largest struct accepted BY VALUE as a parameter: six eightbytes — the ISA argument-register budget (48 B). */
    static final int MAX_STRUCT_ARG_BYTES = 48;
    /** Largest struct RETURNED by value: two eightbytes — accumulator + second return register (16 B). */
    static final int MAX_STRUCT_RET_BYTES = 16;
    private static final List<String> PRINT_BUILTINS = List.of("print", "print_int", "kof_print");

    private final List<KofCToken> toks;
    private int pos = 0;
    private final List<String> errors = new ArrayList<>();

    public KofCParser(List<KofCToken> toks) { this.toks = toks; }

    public boolean hasErrors() { return !errors.isEmpty(); }
    public List<String> errors() { return List.copyOf(errors); }

    public KofCAst.Program parseProgram() {
        List<KofCAst.StructDecl> structs = new ArrayList<>();
        List<KofCAst.VarDecl> globals = new ArrayList<>();
        List<KofCAst.FuncDecl> funcs = new ArrayList<>();
        List<KofCAst.Prototype> prototypes = new ArrayList<>();
        while (!check(KofCTokenType.EOF)) {
            if (check(KofCTokenType.STRUCT)) {
                if (checkAt(1, KofCTokenType.IDENTIFIER) && checkAt(2, KofCTokenType.LBRACE)) {
                    structs.add(parseStruct());
                } else if (checkAt(1, KofCTokenType.IDENTIFIER) && checkAt(2, KofCTokenType.IDENTIFIER) && checkAt(3, KofCTokenType.LPAREN)) {
                    addCallable(parseCallable("struct " + toks.get(pos + 1).text()), funcs, prototypes);
                } else if (checkAt(1, KofCTokenType.IDENTIFIER) && checkAt(2, KofCTokenType.IDENTIFIER)) {
                    globals.add(parseGlobal("struct"));
                } else {
                    error("Expected struct definition or struct variable");
                    advance();
                }
            } else if (check(KofCTokenType.INT)) {
                if (checkAt(1, KofCTokenType.IDENTIFIER) && checkAt(2, KofCTokenType.LPAREN)) {
                    addCallable(parseCallable("int"), funcs, prototypes);
                } else if (checkAt(1, KofCTokenType.IDENTIFIER) && checkAt(2, KofCTokenType.SEMI)) {
                    globals.add(parseGlobal("int"));
                } else {
                    error("Expected global variable or function declaration");
                    advance();
                }
            } else if (check(KofCTokenType.VOID)) {
                addCallable(parseCallable("void"), funcs, prototypes);
            } else {
                error("Unexpected token " + peek().text());
                advance();
            }
        }
        KofCAst.Program program = new KofCAst.Program(structs, globals, funcs, prototypes);
        validate(program);
        return program;
    }

    private void addCallable(Object decl, List<KofCAst.FuncDecl> funcs, List<KofCAst.Prototype> prototypes) {
        if (decl instanceof KofCAst.FuncDecl f) funcs.add(f);
        else if (decl instanceof KofCAst.Prototype p) prototypes.add(p);
    }

    private KofCAst.StructDecl parseStruct() {
        expect(KofCTokenType.STRUCT);
        String name = expect(KofCTokenType.IDENTIFIER).text();
        expect(KofCTokenType.LBRACE);
        List<KofCAst.Param> fields = new ArrayList<>();
        while (!check(KofCTokenType.RBRACE) && !check(KofCTokenType.EOF)) {
            int before = pos;
            String ftype = parseTypeName();
            String fname = expect(KofCTokenType.IDENTIFIER).text();
            expect(KofCTokenType.SEMI);
            fields.add(new KofCAst.Param(ftype, fname));
            if (pos == before) { advance(); } // progress guard (malformed field)
        }
        expect(KofCTokenType.RBRACE);
        expect(KofCTokenType.SEMI);
        return new KofCAst.StructDecl(name, fields);
    }

    /** Global declaration: {@code int g;} or {@code struct S s;}. */
    private KofCAst.VarDecl parseGlobal(String kind) {
        String type;
        if (kind.equals("int")) { advance(); type = "int"; }
        else type = parseTypeName();
        String name = expect(KofCTokenType.IDENTIFIER).text();
        expect(KofCTokenType.SEMI);
        return new KofCAst.VarDecl(type, name);
    }

    /** Definition ({@code int f(...) { ... }}) or prototype ({@code int f(...);}). */
    private Object parseCallable(String retType) {
        advance(); // type
        if (retType.startsWith("struct ")) advance(); // segundo token do tipo struct
        String name = expect(KofCTokenType.IDENTIFIER).text();
        expect(KofCTokenType.LPAREN);
        List<KofCAst.Param> params = parseParams();
        expect(KofCTokenType.RPAREN);
        if (check(KofCTokenType.SEMI)) { // prototype — resolved at link
            advance();
            return new KofCAst.Prototype(name, retType, params);
        }
        List<KofCAst.Stmt> body = parseBlock();
        return new KofCAst.FuncDecl(name, retType, params, body);
    }

    private List<KofCAst.Param> parseParams() {
        List<KofCAst.Param> params = new ArrayList<>();
        if (check(KofCTokenType.RPAREN)) return params;
        if (check(KofCTokenType.VOID) && checkAt(1, KofCTokenType.RPAREN)) { advance(); return params; }
        while (true) {
            String ptype = parseTypeName();
            String pname = expect(KofCTokenType.IDENTIFIER).text();
            params.add(new KofCAst.Param(ptype, pname));
            if (check(KofCTokenType.COMMA)) { advance(); continue; }
            break;
        }
        if (params.size() > MAX_ARGS) {
            error("Too many parameters (" + params.size() + "); the subset supports at most " + MAX_ARGS);
        }
        return params;
    }

    /** Type name: {@code int} or {@code struct X}. */
    private String parseTypeName() {
        if (check(KofCTokenType.INT)) { advance(); return "int"; }
        if (check(KofCTokenType.STRUCT)) {
            advance();
            String n = expect(KofCTokenType.IDENTIFIER).text();
            return "struct " + n;
        }
        error("Expected a type name");
        return "int";
    }

    private List<KofCAst.Stmt> parseBlock() {
        expect(KofCTokenType.LBRACE);
        List<KofCAst.Stmt> body = new ArrayList<>();
        while (!check(KofCTokenType.RBRACE) && !check(KofCTokenType.EOF)) {
            body.add(parseStmt());
        }
        expect(KofCTokenType.RBRACE);
        return body;
    }

    private KofCAst.Stmt parseStmt() {
        if (check(KofCTokenType.IF)) return parseIf();
        if (check(KofCTokenType.WHILE)) return parseWhile();
        if (check(KofCTokenType.ASM)) {
            advance();
            int v = parseIntLiteral();
            expect(KofCTokenType.SEMI);
            return new KofCAst.AsmStmt(v);
        }
        if (check(KofCTokenType.RETURN)) {
            advance();
            if (check(KofCTokenType.SEMI)) { advance(); return new KofCAst.ReturnStmt(null); }
            KofCAst.Expr value = parseExpr();
            expect(KofCTokenType.SEMI);
            return new KofCAst.ReturnStmt(value);
        }
        // local declaration: int x; | struct S s;
        if ((check(KofCTokenType.INT) && checkAt(1, KofCTokenType.IDENTIFIER) && checkAt(2, KofCTokenType.SEMI))
                || (check(KofCTokenType.STRUCT) && checkAt(1, KofCTokenType.IDENTIFIER)
                    && checkAt(2, KofCTokenType.IDENTIFIER) && checkAt(3, KofCTokenType.SEMI))) {
            String type = parseTypeName();
            String name = expect(KofCTokenType.IDENTIFIER).text();
            expect(KofCTokenType.SEMI);
            return new KofCAst.LocalDeclStmt(type, name);
        }
        // assignment (optionally through a pointer): [*(int*)]? ident[.field]? = expr ;
        int save = pos;
        boolean deref = false;
        if (isDerefAhead()) { deref = true; consumeDeref(); }
        if (check(KofCTokenType.IDENTIFIER)) {
            String target = peek().text();
            if (checkAt(1, KofCTokenType.DOT)) {
                advance(); // ident
                advance(); // .
                String field = expect(KofCTokenType.IDENTIFIER).text();
                if (check(KofCTokenType.EQUAL)) {
                    advance();
                    KofCAst.Expr expr = parseExpr();
                    expect(KofCTokenType.SEMI);
                    return new KofCAst.FieldAssignStmt(target, field, expr);
                }
                pos = save;
            } else if (checkAt(1, KofCTokenType.EQUAL)) {
                advance(); // ident
                expect(KofCTokenType.EQUAL);
                KofCAst.Expr expr = parseExpr();
                expect(KofCTokenType.SEMI);
                return new KofCAst.AssignStmt(deref, target, expr);
            } else {
                pos = save;
            }
        } else {
            pos = save;
        }
        // expression statement (a call in practice)
        KofCAst.Expr expr = parseExpr();
        expect(KofCTokenType.SEMI);
        return new KofCAst.ExprStmt(expr);
    }

    private KofCAst.IfStmt parseIf() {
        expect(KofCTokenType.IF);
        expect(KofCTokenType.LPAREN);
        KofCAst.Expr cond = parseExpr();
        expect(KofCTokenType.RPAREN);
        List<KofCAst.Stmt> body = parseBlock();
        return new KofCAst.IfStmt(cond, body);
    }

    private KofCAst.WhileStmt parseWhile() {
        expect(KofCTokenType.WHILE);
        expect(KofCTokenType.LPAREN);
        KofCAst.Expr cond = parseExpr();
        expect(KofCTokenType.RPAREN);
        List<KofCAst.Stmt> body = parseBlock();
        return new KofCAst.WhileStmt(cond, body);
    }

    // expr = unary (op unary)?
    private KofCAst.Expr parseExpr() {
        KofCAst.Expr left = parseUnary();
        String op = parseOp();
        if (op != null) {
            KofCAst.Expr right = parseUnary();
            left = new KofCAst.BinaryExpr(left, op, right);
        }
        return left;
    }

    private String parseOp() {
        KofCToken t = peek();
        return switch (t.type()) {
            case PLUS -> { advance(); yield "+"; }
            case MINUS -> { advance(); yield "-"; }
            case AMP -> { advance(); yield "&"; }
            case PIPE -> { advance(); yield "|"; }
            case CARET -> { advance(); yield "^"; }
            case LESS_LESS -> { advance(); yield "<<"; }
            case GREATER_GREATER -> { advance(); yield ">>"; }
            case EQUAL_EQUAL -> { advance(); yield "=="; }
            case BANG_EQUAL -> { advance(); yield "!="; }
            case LESS -> { advance(); yield "<"; }
            case GREATER -> { advance(); yield ">"; }
            case LESS_EQUAL -> { advance(); yield "<="; }
            case GREATER_EQUAL -> { advance(); yield ">="; }
            default -> null;
        };
    }

    private KofCAst.Expr parseUnary() {
        if (isDerefAhead()) {
            consumeDeref();
            String ident = expect(KofCTokenType.IDENTIFIER).text();
            return new KofCAst.UnaryDeref(ident);
        }
        if (check(KofCTokenType.AMP)) {
            advance();
            String ident = expect(KofCTokenType.IDENTIFIER).text();
            return new KofCAst.UnaryAddr(ident);
        }
        if (check(KofCTokenType.LPAREN)) {
            advance();
            KofCAst.Expr inner = parseExpr();
            expect(KofCTokenType.RPAREN);
            return new KofCAst.ParenExpr(inner);
        }
        if (check(KofCTokenType.IDENTIFIER)) {
            String name = advance().text();
            if (check(KofCTokenType.LPAREN)) return parseCall(name);
            if (check(KofCTokenType.DOT)) {
                advance();
                String field = expect(KofCTokenType.IDENTIFIER).text();
                return new KofCAst.FieldExpr(name, field);
            }
            return new KofCAst.IdentExpr(name);
        }
        if (check(KofCTokenType.INTEGER)) {
            int v = parseIntLiteral();
            return new KofCAst.IntExpr(v);
        }
        error("Unexpected unary " + peek().text());
        advance();
        return new KofCAst.IntExpr(0);
    }

    private KofCAst.CallExpr parseCall(String name) {
        expect(KofCTokenType.LPAREN);
        List<KofCAst.Expr> args = new ArrayList<>();
        if (!check(KofCTokenType.RPAREN)) {
            while (true) {
                args.add(parseExpr());
                if (check(KofCTokenType.COMMA)) { advance(); continue; }
                break;
            }
        }
        expect(KofCTokenType.RPAREN);
        if (args.size() > MAX_ARGS) {
            error("Too many call arguments (" + args.size() + "); the subset supports at most " + MAX_ARGS);
        }
        return new KofCAst.CallExpr(name, args);
    }

    // ── validation (honest diagnostics, R6/Q7) ─────────────────────────────

    private void validate(KofCAst.Program program) {
        Map<String, KofCAst.StructDecl> structs = new LinkedHashMap<>();
        Map<String, Integer> structBytes = new LinkedHashMap<>();
        for (var s : program.structs()) {
            structs.put(s.name(), s);
            int size = 0;
            for (var f : s.fields()) {
                if (!f.type().equals("int")) { error("struct " + s.name() + " field " + f.name() + " must be int"); }
                size += 4;
            }
            structBytes.put(s.name(), size);
        }
        for (var fn : program.funcs()) {
            checkParamBounds(fn.name(), fn.params(), structBytes);
            if (fn.retType().startsWith("struct ")) {
                String n = fn.retType().substring("struct ".length());
                if (structBytes.get(n) > MAX_STRUCT_RET_BYTES) {
                    error(fn.name() + ": returning " + fn.retType() + " (" + structBytes.get(n)
                            + " bytes) by value; at most " + MAX_STRUCT_RET_BYTES + " (two eightbytes — accumulator + second return register)");
                }
            }
        }
        for (var p : program.prototypes()) checkParamBounds("external " + p.name(), p.params(), structBytes);
        Map<String, List<String>> calleeParams = new LinkedHashMap<>();
        for (var fn : program.funcs()) calleeParams.put(fn.name(), fn.params().stream().map(KofCAst.Param::type).toList());
        for (var p : program.prototypes()) calleeParams.putIfAbsent(p.name(), p.params().stream().map(KofCAst.Param::type).toList());
        for (var g : program.globals()) checkStructType(g.type(), structs);
        Map<String, Integer> arity = new LinkedHashMap<>();
        for (var fn : program.funcs()) arity.put(fn.name(), fn.params().size());
        for (var p : program.prototypes()) arity.putIfAbsent(p.name(), p.params().size());
        for (var fn : program.funcs()) {
            Map<String, String> types = new LinkedHashMap<>();
            for (var p : fn.params()) { types.put(p.name(), p.type()); checkStructType(p.type(), structs); }
            for (var g : program.globals()) types.putIfAbsent(g.name(), g.type());
            collectLocalTypes(fn.body(), types, structs);
            for (var st : fn.body()) validateStmt(st, types, structs, arity, fn.retType(), calleeParams);
        }
    }

    private void checkParamBounds(String where, List<KofCAst.Param> params,
                                   Map<String, Integer> structBytes) {
        for (var p : params) {
            if (p.type().startsWith("struct ")) {
                String n = p.type().substring("struct ".length());
                if (structBytes.get(n) > MAX_STRUCT_ARG_BYTES) {
                    error(where + ": struct parameter " + p.type() + " is " + structBytes.get(n)
                            + " bytes; at most " + MAX_STRUCT_ARG_BYTES + " (six eightbytes — the ISA argument-register budget)");
                }
            }
        }
    }

    private void checkStructType(String type, Map<String, KofCAst.StructDecl> structs) {
        if (type.startsWith("struct ")) {
            String n = type.substring("struct ".length());
            if (!structs.containsKey(n)) error("unknown struct " + n);
        }
    }

    private void collectLocalTypes(List<KofCAst.Stmt> body, Map<String, String> types,
                                   Map<String, KofCAst.StructDecl> structs) {
        for (var st : body) {
            if (st instanceof KofCAst.LocalDeclStmt s) { types.put(s.name(), s.type()); checkStructType(s.type(), structs); }
            else if (st instanceof KofCAst.IfStmt s) collectLocalTypes(s.thenBody(), types, structs);
            else if (st instanceof KofCAst.WhileStmt s) collectLocalTypes(s.body(), types, structs);
        }
    }

    private void validateStmt(KofCAst.Stmt stmt, Map<String, String> types,
                              Map<String, KofCAst.StructDecl> structs, Map<String, Integer> arity,
                              String retType, Map<String, List<String>> calleeParams) {
        switch (stmt) {
            case KofCAst.IfStmt s -> {
                validateExpr(s.cond(), types, structs, arity, calleeParams);
                for (var st : s.thenBody()) validateStmt(st, types, structs, arity, retType, calleeParams);
            }
            case KofCAst.WhileStmt s -> {
                validateExpr(s.cond(), types, structs, arity, calleeParams);
                for (var st : s.body()) validateStmt(st, types, structs, arity, retType, calleeParams);
            }
            case KofCAst.ExprStmt s -> validateExpr(s.expr(), types, structs, arity, calleeParams);
            case KofCAst.AssignStmt s -> validateExpr(s.value(), types, structs, arity, calleeParams);
            case KofCAst.FieldAssignStmt s -> {
                validateField(s.target(), s.field(), types, structs);
                validateExpr(s.value(), types, structs, arity, calleeParams);
            }
            case KofCAst.ReturnStmt s -> {
                if (retType.startsWith("struct ")) {
                    if (!(s.value() instanceof KofCAst.IdentExpr id)) {
                        error(retType + " function needs `return <variable>;`");
                    } else if (!retType.equals(types.get(id.name()))) {
                        error("returned variable has type " + types.get(id.name()) + "; expected " + retType);
                    }
                }
                if (s.value() != null) validateExpr(s.value(), types, structs, arity, calleeParams);
            }
            case KofCAst.AsmStmt _ -> { }
            case KofCAst.LocalDeclStmt _ -> { }
        }
    }

    private void validateExpr(KofCAst.Expr expr, Map<String, String> types,
                              Map<String, KofCAst.StructDecl> structs, Map<String, Integer> arity,
                              Map<String, List<String>> calleeParams) {
        switch (expr) {
            case KofCAst.BinaryExpr e -> { validateExpr(e.left(), types, structs, arity, calleeParams); validateExpr(e.right(), types, structs, arity, calleeParams); }
            case KofCAst.ParenExpr e -> validateExpr(e.inner(), types, structs, arity, calleeParams);
            case KofCAst.FieldExpr e -> validateField(e.base(), e.field(), types, structs);
            case KofCAst.CallExpr e -> {
                var ptypes = calleeParams.getOrDefault(e.name(), List.<String>of());
                for (var a : e.args()) validateExpr(a, types, structs, arity, calleeParams);
                for (int ai = 0; ai < e.args().size() && ai < ptypes.size(); ai++) {
                    String pt = ptypes.get(ai);
                    if (pt.startsWith("struct ")
                            && !(e.args().get(ai) instanceof KofCAst.IdentExpr aid && pt.equals(types.get(aid.name())))) {
                        error("argument " + (ai + 1) + " of " + e.name() + " expects " + pt);
                    }
                }
                if (PRINT_BUILTINS.contains(e.name())) {
                    if (!e.args().isEmpty()) error("print() takes no arguments in the subset");
                } else if (arity.containsKey(e.name())) {
                    int want = arity.get(e.name());
                    if (want != e.args().size()) {
                        error("function " + e.name() + " expects " + want + " argument(s), got " + e.args().size());
                    }
                } else {
                    error("call to unknown function " + e.name());
                }
            }
            case null, default -> { }
        }
    }

    private void validateField(String base, String field, Map<String, String> types,
                               Map<String, KofCAst.StructDecl> structs) {
        String type = types.get(base);
        if (type == null || !type.startsWith("struct ")) {
            error(base + " is not a struct variable");
            return;
        }
        KofCAst.StructDecl decl = structs.get(type.substring("struct ".length()));
        if (decl == null) return; // already reported
        boolean found = decl.fields().stream().anyMatch(f -> f.name().equals(field));
        if (!found) error("struct " + decl.name() + " has no field " + field);
    }

    private boolean isDerefAhead() {
        // pattern: * ( int * )  -> STAR LPAREN INT STAR RPAREN
        if (pos + 4 >= toks.size()) return false;
        if (toks.get(pos).type() != KofCTokenType.STAR) return false;
        if (toks.get(pos + 1).type() != KofCTokenType.LPAREN) return false;
        if (toks.get(pos + 2).type() != KofCTokenType.INT) return false;
        return toks.get(pos + 3).type() == KofCTokenType.STAR
                && toks.get(pos + 4).type() == KofCTokenType.RPAREN;
    }

    private void consumeDeref() {
        expect(KofCTokenType.STAR);
        expect(KofCTokenType.LPAREN);
        expect(KofCTokenType.INT);
        expect(KofCTokenType.STAR);
        expect(KofCTokenType.RPAREN);
    }

    private int parseIntLiteral() {
        String txt = expect(KofCTokenType.INTEGER).text();
        try {
            if (txt.startsWith("0x") || txt.startsWith("0X")) return (int) Long.parseLong(txt.substring(2), 16);
            return Integer.parseInt(txt);
        } catch (NumberFormatException e) { return 0; }
    }

    // helpers
    private KofCToken peek() { return toks.get(pos); }
    private boolean check(KofCTokenType t) { return peek().type() == t; }
    private boolean checkAt(int off, KofCTokenType t) {
        return pos + off < toks.size() && toks.get(pos + off).type() == t;
    }
    private KofCToken advance() { return toks.get(pos++); }
    private KofCToken expect(KofCTokenType t) {
        if (check(t)) return advance();
        error("Expected " + t + " got " + peek().type() + " (" + peek().text() + ")");
        return new KofCToken(t, "", 0, 0);
    }
    private void error(String msg) {
        KofCToken t = (pos < toks.size()) ? toks.get(pos) : toks.get(toks.size() - 1);
        errors.add("line " + t.line() + ", col " + t.col() + ": " + msg);
    }
}
