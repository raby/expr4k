package com.digitalbluebird.expr4k

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEqualTo
import kotlin.test.Test
import kotlin.test.assertFailsWith

class ParserTest {

    private fun parse(source: String): Node = Parser(Lexer(source).tokenize()).parse()

    /** Render a tree as an S-expression so precedence and associativity are legible in assertions. */
    private fun sexpr(node: Node): String = when (node) {
        is NumberLit -> node.value.toString()
        is StringLit -> "'${node.value}'"
        is BoolLit -> node.value.toString()
        is NullLit -> "null"
        is Identifier -> node.name
        is ListLit -> node.elements.joinToString(separator = " ", prefix = "[", postfix = "]") { sexpr(it) }
        is Member -> "(. ${sexpr(node.target)} ${node.name})"
        is Unary -> "(${node.op} ${sexpr(node.operand)})"
        is Binary -> "(${node.op} ${sexpr(node.left)} ${sexpr(node.right)})"
        is Ternary -> "(?: ${sexpr(node.condition)} ${sexpr(node.ifTrue)} ${sexpr(node.ifFalse)})"
    }

    private fun ast(source: String): String = sexpr(parse(source))

    @Test
    fun `parses each kind of literal`() {
        assertThat(ast("42")).isEqualTo("42.0")
        assertThat(ast("'hi'")).isEqualTo("'hi'")
        assertThat(ast("true")).isEqualTo("true")
        assertThat(ast("false")).isEqualTo("false")
        assertThat(ast("null")).isEqualTo("null")
        assertThat(ast("age")).isEqualTo("age")
    }

    @Test
    fun `multiplication binds tighter than addition`() {
        assertThat(ast("2 + 3 * 4")).isEqualTo("(ADD 2.0 (MUL 3.0 4.0))")
        assertThat(ast("2 * 3 + 4")).isEqualTo("(ADD (MUL 2.0 3.0) 4.0)")
    }

    @Test
    fun `parentheses override precedence`() {
        assertThat(ast("(2 + 3) * 4")).isEqualTo("(MUL (ADD 2.0 3.0) 4.0)")
    }

    @Test
    fun `unary binds tighter than multiplication`() {
        assertThat(ast("-2 * 3")).isEqualTo("(MUL (NEG 2.0) 3.0)")
        assertThat(ast("!a == b")).isEqualTo("(EQ (NOT a) b)")
        assertThat(ast("--x")).isEqualTo("(NEG (NEG x))")
    }

    @Test
    fun `binary operators are left associative`() {
        assertThat(ast("1 - 2 - 3")).isEqualTo("(SUB (SUB 1.0 2.0) 3.0)")
        assertThat(ast("a == b == c")).isEqualTo("(EQ (EQ a b) c)")
    }

    @Test
    fun `logical and binds tighter than or`() {
        assertThat(ast("a && b || c")).isEqualTo("(OR (AND a b) c)")
        assertThat(ast("a || b && c")).isEqualTo("(OR a (AND b c))")
    }

    @Test
    fun `the ternary is right associative`() {
        assertThat(ast("a ? b : c ? d : e")).isEqualTo("(?: a b (?: c d e))")
    }

    @Test
    fun `parses the flagship predicate`() {
        assertThat(ast("age >= 18 && country in ['UK', 'IE']"))
            .isEqualTo("(AND (GTE age 18.0) (IN country ['UK' 'IE']))")
    }

    @Test
    fun `parses list literals, including empty and nested`() {
        assertThat(ast("[1, 2, 3]")).isEqualTo("[1.0 2.0 3.0]")
        assertThat(ast("[]")).isEqualTo("[]")
        assertThat(ast("[[1], [2, 3]]")).isEqualTo("[[1.0] [2.0 3.0]]")
    }

    @Test
    fun `parses member access, left associatively`() {
        assertThat(ast("user.age")).isEqualTo("(. user age)")
        assertThat(ast("a.b.c")).isEqualTo("(. (. a b) c)")
    }

    @Test
    fun `member access binds tighter than arithmetic`() {
        assertThat(ast("a.b + c")).isEqualTo("(ADD (. a b) c)")
    }

    @Test
    fun `a compound node starts at its left operand`() {
        // "  age >= 18": the whole comparison begins where `age` begins, at column 3
        val node = parse("  age >= 18")
        assertThat(node.pos).isEqualTo(Pos(1, 3, 2))
    }

    @Test
    fun `rejects trailing input`() {
        val e = assertFailsWith<ParseException> { parse("1 2") }
        assertThat(e.pos.col).isEqualTo(3)
    }

    @Test
    fun `rejects an unclosed group and names the missing paren`() {
        val e = assertFailsWith<ParseException> { parse("(1 + 2") }
        assertThat(e.message!!).contains(")")
    }

    @Test
    fun `rejects an unclosed list and names the missing bracket`() {
        val e = assertFailsWith<ParseException> { parse("[1, 2") }
        assertThat(e.message!!).contains("]")
    }

    @Test
    fun `rejects a dangling operator at the start`() {
        val e = assertFailsWith<ParseException> { parse("* 3") }
        assertThat(e.pos.col).isEqualTo(1)
    }

    @Test
    fun `rejects a ternary that is missing its colon`() {
        val e = assertFailsWith<ParseException> { parse("a ? b") }
        assertThat(e.message!!).contains(":")
    }

    @Test
    fun `rejects empty input`() {
        val e = assertFailsWith<ParseException> { parse("") }
        assertThat(e.message!!).contains("end of input")
    }
}
