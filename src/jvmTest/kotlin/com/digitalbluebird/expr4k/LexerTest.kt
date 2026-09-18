package com.digitalbluebird.expr4k

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import kotlin.test.Test
import kotlin.test.assertFailsWith

class LexerTest {

    private fun types(source: String): List<TokenType> = Lexer(source).tokenize().map { it.type }

    @Test
    fun `tokenises a predicate expression`() {
        assertThat(types("age >= 18 && country in ['UK', 'IE']")).containsExactly(
            TokenType.IDENT, // age
            TokenType.GTE,
            TokenType.NUMBER, // 18
            TokenType.AND,
            TokenType.IDENT, // country
            TokenType.IN,
            TokenType.LBRACKET,
            TokenType.STRING, // 'UK'
            TokenType.COMMA,
            TokenType.STRING, // 'IE'
            TokenType.RBRACKET,
            TokenType.EOF,
        )
    }

    @Test
    fun `always appends an EOF token`() {
        assertThat(types("")).containsExactly(TokenType.EOF)
    }

    @Test
    fun `decodes integer and decimal number literals as doubles`() {
        val tokens = Lexer("18 3.14").tokenize()
        assertThat(tokens[0].literal).isEqualTo(18.0)
        assertThat(tokens[1].literal).isEqualTo(3.14)
    }

    @Test
    fun `a dot after a number is its own token, not part of the number`() {
        // member access like `user.age` must not be swallowed into a float
        assertThat(types("user.age")).containsExactly(
            TokenType.IDENT, TokenType.DOT, TokenType.IDENT, TokenType.EOF,
        )
    }

    @Test
    fun `decodes a string literal and its escapes`() {
        val token = Lexer("'hi\\n'").tokenize().first()
        assertThat(token.type).isEqualTo(TokenType.STRING)
        assertThat(token.literal).isEqualTo("hi\n")
    }

    @Test
    fun `single and double quotes both delimit strings`() {
        assertThat(Lexer("\"UK\"").tokenize().first().literal).isEqualTo("UK")
        assertThat(Lexer("'UK'").tokenize().first().literal).isEqualTo("UK")
    }

    @Test
    fun `keywords are distinct from identifiers`() {
        assertThat(types("true false null in")).containsExactly(
            TokenType.TRUE, TokenType.FALSE, TokenType.NULL, TokenType.IN, TokenType.EOF,
        )
        // a word that merely contains a keyword stays an identifier
        assertThat(types("intake trueness")).containsExactly(
            TokenType.IDENT, TokenType.IDENT, TokenType.EOF,
        )
    }

    @Test
    fun `identifiers may hold digits and underscores after the first letter`() {
        assertThat(types("_x user_2 a1")).containsExactly(
            TokenType.IDENT, TokenType.IDENT, TokenType.IDENT, TokenType.EOF,
        )
    }

    @Test
    fun `distinguishes single from double character operators`() {
        assertThat(types("< <= > >= ! !=")).containsExactly(
            TokenType.LT, TokenType.LTE, TokenType.GT, TokenType.GTE, TokenType.NOT, TokenType.NEQ,
            TokenType.EOF,
        )
    }

    @Test
    fun `reports the position of an unexpected character`() {
        val e = assertFailsWith<LexException> { Lexer("a @ b").tokenize() }
        assertThat(e.pos.col).isEqualTo(3)
    }

    @Test
    fun `tracks line and column across newlines`() {
        // the '@' sits on line 2, column 1
        val e = assertFailsWith<LexException> { Lexer("a &&\n@").tokenize() }
        assertThat(e.pos.line).isEqualTo(2)
        assertThat(e.pos.col).isEqualTo(1)
    }

    @Test
    fun `a string spanning a raw newline keeps its start position`() {
        // regression: the token position must be captured at the string's start, not after the
        // contained newline has advanced line/column (which previously yielded a negative column).
        val tokens = Lexer("'a\nb' + c").tokenize()
        assertThat(tokens[0].type).isEqualTo(TokenType.STRING)
        assertThat(tokens[0].literal).isEqualTo("a\nb")
        assertThat(tokens[0].pos).isEqualTo(Pos(1, 1, 0))
        // the '+' after the multi-line string is now on line 2
        assertThat(tokens[1].type).isEqualTo(TokenType.PLUS)
        assertThat(tokens[1].pos.line).isEqualTo(2)
    }

    @Test
    fun `a lone ampersand is rejected — only && is valid`() {
        assertFailsWith<LexException> { Lexer("a & b").tokenize() }
    }

    @Test
    fun `an unterminated string is rejected`() {
        assertThat(assertFailsWith<LexException> { Lexer("'oops").tokenize() })
            .isInstanceOf(LexException::class)
    }

    @Test
    fun `an invalid escape is rejected`() {
        assertFailsWith<LexException> { Lexer("'a\\q'").tokenize() }
    }

    @Test
    fun `records the lexeme and start position of each token`() {
        val plus = Lexer("1 + 2").tokenize()[1]
        assertThat(plus.type).isEqualTo(TokenType.PLUS)
        assertThat(plus.lexeme).isEqualTo("+")
        assertThat(plus.pos.col).isEqualTo(3)
    }
}
