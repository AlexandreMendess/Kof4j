package dev.kof.c;

public enum KofCTokenType {
    // keywords
    INT, VOID, IF, WHILE, ASM, RETURN, STRUCT,
    // literals
    IDENTIFIER, INTEGER,
    // symbols
    LPAREN, RPAREN, LBRACE, RBRACE, SEMI, COMMA, STAR, AMP, DOT,
    EQUAL, PLUS, MINUS, PIPE, CARET,
    LESS, GREATER, LESS_EQUAL, GREATER_EQUAL,
    EQUAL_EQUAL, BANG_EQUAL,
    LESS_LESS, GREATER_GREATER,
    // end
    EOF
}
