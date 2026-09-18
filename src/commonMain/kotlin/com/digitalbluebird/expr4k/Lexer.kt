package com.digitalbluebird.expr4k

/** Raised when the source cannot be tokenised. Carries the [pos] of the offending character. */
public class LexException(message: String, pos: Pos) : Expr4kException(message, pos)

/**
 * Turns an expression [source] string into a flat list of [Token]s, always ending with an
 * [TokenType.EOF] token. Hand-written: a single forward pass over the characters, no lexer generator.
 *
 * A [Lexer] is single-use: it holds a mutable cursor and is not reset between calls, so construct a
 * fresh instance per source (as the public API does).
 */
public class Lexer(private val source: String) {
    private var start = 0
    private var current = 0
    private var line = 1
    private var lineStart = 0 // offset of the current line's first character, for column maths
    private var startPos = Pos(1, 1, 0) // start of the token being scanned; captured before it can span a newline

    private val tokens = mutableListOf<Token>()

    /** Scan the whole source and return its tokens, including a trailing [TokenType.EOF]. */
    public fun tokenize(): List<Token> {
        while (!isAtEnd()) {
            start = current
            startPos = posAt(current) // line/lineStart are correct here; capture before scanToken can advance them
            scanToken()
        }
        tokens.add(Token(TokenType.EOF, "", posAt(current)))
        return tokens
    }

    private fun scanToken() {
        when (val c = advance()) {
            ' ', '\t', '\r' -> {} // skip insignificant whitespace
            '\n' -> newline()
            '(' -> add(TokenType.LPAREN)
            ')' -> add(TokenType.RPAREN)
            '[' -> add(TokenType.LBRACKET)
            ']' -> add(TokenType.RBRACKET)
            ',' -> add(TokenType.COMMA)
            '.' -> add(TokenType.DOT)
            '+' -> add(TokenType.PLUS)
            '-' -> add(TokenType.MINUS)
            '*' -> add(TokenType.STAR)
            '/' -> add(TokenType.SLASH)
            '%' -> add(TokenType.PERCENT)
            '?' -> add(TokenType.QUESTION)
            ':' -> add(TokenType.COLON)
            '=' -> { expect('='); add(TokenType.EQ) }
            '!' -> add(if (match('=')) TokenType.NEQ else TokenType.NOT)
            '<' -> add(if (match('=')) TokenType.LTE else TokenType.LT)
            '>' -> add(if (match('=')) TokenType.GTE else TokenType.GT)
            '&' -> { expect('&'); add(TokenType.AND) }
            '|' -> { expect('|'); add(TokenType.OR) }
            '\'', '"' -> string(c)
            else -> when {
                c.isDigit() -> number()
                c.isLetter() || c == '_' -> identifier()
                else -> throw LexException("Unexpected character '$c'", startPos)
            }
        }
    }

    private fun string(quote: Char) {
        val sb = StringBuilder()
        while (!isAtEnd() && peek() != quote) {
            when (val ch = advance()) {
                '\\' -> sb.append(escape())
                '\n' -> { newline(); sb.append(ch) }
                else -> sb.append(ch)
            }
        }
        if (isAtEnd()) throw LexException("Unterminated string", startPos)
        advance() // consume the closing quote
        add(TokenType.STRING, sb.toString())
    }

    private fun escape(): Char {
        if (isAtEnd()) throw LexException("Unterminated escape", posAt(current - 1))
        return when (val esc = advance()) {
            'n' -> '\n'
            't' -> '\t'
            'r' -> '\r'
            '\\' -> '\\'
            '\'' -> '\''
            '"' -> '"'
            else -> throw LexException("Invalid escape '\\$esc'", posAt(current - 2))
        }
    }

    private fun number() {
        while (peek().isDigit()) advance()
        if (peek() == '.' && peekNext().isDigit()) {
            advance()
            while (peek().isDigit()) advance()
        }
        add(TokenType.NUMBER, source.substring(start, current).toDouble())
    }

    private fun identifier() {
        while (peek().isLetterOrDigit() || peek() == '_') advance()
        val text = source.substring(start, current)
        add(KEYWORDS[text] ?: TokenType.IDENT)
    }

    // --- scanning helpers ---

    private fun isAtEnd(): Boolean = current >= source.length

    private fun advance(): Char = source[current++]

    // Char.MIN_VALUE (NUL) stands in for "no character": returned past the end of input so scans can
    // test the next char without a separate bounds check (NUL never appears in a valid expression).
    private fun peek(): Char = if (isAtEnd()) Char.MIN_VALUE else source[current]

    private fun peekNext(): Char = if (current + 1 >= source.length) Char.MIN_VALUE else source[current + 1]

    private fun match(expected: Char): Boolean {
        if (isAtEnd() || source[current] != expected) return false
        current++
        return true
    }

    private fun expect(expected: Char) {
        if (!match(expected)) throw LexException("Expected '$expected'", posAt(current))
    }

    private fun newline() {
        line++
        lineStart = current
    }

    private fun posAt(offset: Int): Pos = Pos(line, offset - lineStart + 1, offset)

    private fun add(type: TokenType, literal: Any? = null) {
        tokens.add(Token(type, source.substring(start, current), startPos, literal))
    }

    private companion object {
        val KEYWORDS: Map<String, TokenType> = mapOf(
            "true" to TokenType.TRUE,
            "false" to TokenType.FALSE,
            "null" to TokenType.NULL,
            "in" to TokenType.IN,
        )
    }
}
