package com.digitalbluebird.expr4k

/** A source position: 1-based [line] and [col], plus the absolute character [offset]. */
public data class Pos(val line: Int, val col: Int, val offset: Int) {
    override fun toString(): String = "$line:$col"
}

/** The kind of a lexical [Token]. */
public enum class TokenType {
    // literals
    NUMBER, STRING, TRUE, FALSE, NULL,

    // identifier
    IDENT,

    // operators
    PLUS, MINUS, STAR, SLASH, PERCENT,
    EQ, NEQ, LT, LTE, GT, GTE,
    AND, OR, NOT,
    IN,
    QUESTION, COLON,

    // punctuation
    LPAREN, RPAREN, LBRACKET, RBRACKET, COMMA, DOT,

    // end of input
    EOF,
}

/**
 * A single lexical token.
 *
 * @property type the kind of token.
 * @property lexeme the exact text as it appeared in the source.
 * @property pos where the token starts.
 * @property literal the decoded value for a literal token: a [Double] for [TokenType.NUMBER], a
 *   [String] for [TokenType.STRING]; `null` for every other kind.
 */
public data class Token(
    val type: TokenType,
    val lexeme: String,
    val pos: Pos,
    val literal: Any? = null,
)
