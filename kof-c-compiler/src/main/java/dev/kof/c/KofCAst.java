package dev.kof.c;

import java.util.List;

public final class KofCAst {
    public record Program(List<StructDecl> structs, List<VarDecl> globals, List<FuncDecl> funcs, List<Prototype> prototypes) {}

    /** Struct definition; fields are `int` only. By value: parameter ≤ six eightbytes (48 B), return ≤ two (16 B). */
    public record StructDecl(String name, List<Param> fields) {}

    /** Global variable ({@code int g;} or {@code struct S s;}). */
    public record VarDecl(String type, String name) {}

    /** Parameter or struct field; {@code type} is {@code int} or {@code struct X}. */
    public record Param(String type, String name) {}

    public record FuncDecl(String name, String retType, List<Param> params, List<Stmt> body) {}

    /** Forward declaration of an external function ({@code int f(int a);}) — resolved at link. */
    public record Prototype(String name, String retType, List<Param> params) {}

    public sealed interface Stmt permits IfStmt, WhileStmt, AsmStmt, ExprStmt, AssignStmt, FieldAssignStmt, LocalDeclStmt, ReturnStmt {}

    public record IfStmt(Expr cond, List<Stmt> thenBody) implements Stmt {}
    public record WhileStmt(Expr cond, List<Stmt> body) implements Stmt {}
    public record AsmStmt(int value) implements Stmt {}
    /** Expression statement — a call in practice ({@code f(a, b);}). */
    public record ExprStmt(Expr expr) implements Stmt {}
    public record AssignStmt(boolean deref, String target, Expr value) implements Stmt {}
    /** {@code s.field = expr;} — C {@code int} field, stored 32-bit. */
    public record FieldAssignStmt(String target, String field, Expr value) implements Stmt {}
    /** Local variable declaration ({@code int x;} / {@code struct S s;}). */
    public record LocalDeclStmt(String type, String name) implements Stmt {}
    /** {@code return expr;} — {@code value} is null for a bare {@code return;}. */
    public record ReturnStmt(Expr value) implements Stmt {}

    public sealed interface Expr permits BinaryExpr, UnaryDeref, UnaryAddr, ParenExpr, IdentExpr, IntExpr, CallExpr, FieldExpr {}

    public record BinaryExpr(Expr left, String op, Expr right) implements Expr {}
    // Unary cases:
    //  - deref: *(int*)ident
    //  - addr: &ident
    //  - ident / int / paren / call / field are separate Expr types
    public record UnaryDeref(String ident) implements Expr {}
    public record UnaryAddr(String ident) implements Expr {}
    public record ParenExpr(Expr inner) implements Expr {}
    public record IdentExpr(String name) implements Expr {}
    public record IntExpr(int value) implements Expr {}
    public record CallExpr(String name, List<Expr> args) implements Expr {}
    /** {@code base.field} — reads a 32-bit C {@code int} field, sign-extended. */
    public record FieldExpr(String base, String field) implements Expr {}
}
