package dev.kof.c;

import java.util.ArrayList;
import java.util.List;

public final class KofCLexer {
    private final String src;
    private int pos = 0;
    private int line = 1;
    private int col = 1;
    private final List<KofCToken> out = new ArrayList<>();

    public KofCLexer(String src) { this.src = src; }

    public List<KofCToken> lex() {
        while (pos < src.length()) {
            char c = src.charAt(pos);
            if (c == '/' && pos + 1 < src.length() && src.charAt(pos + 1) == '/') {
                // line comment
                while (pos < src.length() && src.charAt(pos) != '\n') advance();
                continue;
            }
            if (c == '/' && pos + 1 < src.length() && src.charAt(pos + 1) == '*') {
                pos += 2; col += 2;
                while (pos + 1 < src.length() && !(src.charAt(pos) == '*' && src.charAt(pos + 1) == '/')) {
                    if (src.charAt(pos) == '\n') { line++; col = 1; } else col++;
                    pos++;
                }
                if (pos + 1 < src.length()) { pos += 2; col += 2; }
                continue;
            }
            if (Character.isWhitespace(c)) {
                if (c == '\n') { line++; col = 1; } else col++;
                pos++;
                continue;
            }
            if (Character.isLetter(c) || c == '_') {
                int s = pos; int sc = col;
                while (pos < src.length() && (Character.isLetterOrDigit(src.charAt(pos)) || src.charAt(pos) == '_')) { pos++; col++; }
                String w = src.substring(s, pos);
                KofCTokenType t = switch (w) {
                    case "int" -> KofCTokenType.INT;
                    case "void" -> KofCTokenType.VOID;
                    case "if" -> KofCTokenType.IF;
                    case "while" -> KofCTokenType.WHILE;
                    case "asm" -> KofCTokenType.ASM;
                    case "return" -> KofCTokenType.RETURN;
                    case "struct" -> KofCTokenType.STRUCT;
                    default -> KofCTokenType.IDENTIFIER;
                };
                out.add(new KofCToken(t, w, line, sc));
                continue;
            }
            if (Character.isDigit(c)) {
                int s = pos; int sc = col;
                // handle hex 0x ?
                if (c == '0' && pos + 1 < src.length() && (src.charAt(pos+1) == 'x' || src.charAt(pos+1) == 'X')) {
                    pos+=2; col+=2;
                    while (pos < src.length() && isHex(src.charAt(pos))) { pos++; col++; }
                } else {
                    while (pos < src.length() && Character.isDigit(src.charAt(pos))) { pos++; col++; }
                }
                out.add(new KofCToken(KofCTokenType.INTEGER, src.substring(s, pos), line, sc));
                continue;
            }
            int sc = col;
            // multi-char ops
            if (pos + 1 < src.length()) {
                String two = src.substring(pos, pos + 2);
                KofCTokenType tt = switch (two) {
                    case "==" -> KofCTokenType.EQUAL_EQUAL;
                    case "!=" -> KofCTokenType.BANG_EQUAL;
                    case "<=" -> KofCTokenType.LESS_EQUAL;
                    case ">=" -> KofCTokenType.GREATER_EQUAL;
                    case "<<" -> KofCTokenType.LESS_LESS;
                    case ">>" -> KofCTokenType.GREATER_GREATER;
                    default -> null;
                };
                if (tt != null) {
                    out.add(new KofCToken(tt, two, line, sc));
                    pos += 2; col += 2;
                    continue;
                }
            }
            KofCTokenType tt = switch (c) {
                case '(' -> KofCTokenType.LPAREN;
                case ')' -> KofCTokenType.RPAREN;
                case '{' -> KofCTokenType.LBRACE;
                case '}' -> KofCTokenType.RBRACE;
                case ';' -> KofCTokenType.SEMI;
                case ',' -> KofCTokenType.COMMA;
                case '.' -> KofCTokenType.DOT;
                case '*' -> KofCTokenType.STAR;
                case '&' -> KofCTokenType.AMP;
                case '=' -> KofCTokenType.EQUAL;
                case '+' -> KofCTokenType.PLUS;
                case '-' -> KofCTokenType.MINUS;
                case '|' -> KofCTokenType.PIPE;
                case '^' -> KofCTokenType.CARET;
                case '<' -> KofCTokenType.LESS;
                case '>' -> KofCTokenType.GREATER;
                default -> null;
            };
            if (tt != null) {
                out.add(new KofCToken(tt, String.valueOf(c), line, sc));
                pos++; col++;
                continue;
            }
            // unknown char skip
            pos++; col++;
        }
        out.add(new KofCToken(KofCTokenType.EOF, "", line, col));
        return out;
    }

    private void advance() { pos++; col++; }
    private boolean isHex(char c) { return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F'); }
}
