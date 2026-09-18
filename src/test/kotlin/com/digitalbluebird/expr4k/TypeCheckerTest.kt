package com.digitalbluebird.expr4k

import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlin.test.Test
import kotlin.test.assertFailsWith

class TypeCheckerTest {

    private fun check(source: String, schema: Map<String, Type> = emptyMap()): Type =
        TypeChecker(schema).check(Parser(Lexer(source).tokenize()).parse())

    @Test
    fun `infers the type of each literal`() {
        assertThat(check("42")).isEqualTo(Type.NUMBER)
        assertThat(check("'x'")).isEqualTo(Type.STRING)
        assertThat(check("true")).isEqualTo(Type.BOOLEAN)
        assertThat(check("null")).isEqualTo(Type.NULL)
        assertThat(check("[1, 2]")).isEqualTo(Type.LIST)
    }

    @Test
    fun `infers result types of operators`() {
        assertThat(check("1 + 2")).isEqualTo(Type.NUMBER)
        assertThat(check("'a' + 'b'")).isEqualTo(Type.STRING)
        assertThat(check("1 < 2")).isEqualTo(Type.BOOLEAN)
        assertThat(check("true && false")).isEqualTo(Type.BOOLEAN)
        assertThat(check("1 == 'x'")).isEqualTo(Type.BOOLEAN) // equality is defined across types
        assertThat(check("2 in [1, 2]")).isEqualTo(Type.BOOLEAN)
    }

    @Test
    fun `rejects arithmetic on non-numbers`() {
        assertFailsWith<TypeException> { check("'a' * 2") }
        assertFailsWith<TypeException> { check("true - 1") }
    }

    @Test
    fun `rejects mixing a number and a string in plus`() {
        assertFailsWith<TypeException> { check("1 + 'a'") }
    }

    @Test
    fun `rejects logical operators on non-booleans`() {
        assertFailsWith<TypeException> { check("1 && true") }
        assertFailsWith<TypeException> { check("!5") }
    }

    @Test
    fun `rejects negation of a non-number`() {
        assertFailsWith<TypeException> { check("-'a'") }
    }

    @Test
    fun `rejects ordering comparison across types`() {
        assertFailsWith<TypeException> { check("1 < 'a'") }
    }

    @Test
    fun `rejects 'in' when the right side is not a list`() {
        assertFailsWith<TypeException> { check("2 in 3") }
    }

    @Test
    fun `rejects a non-boolean ternary condition`() {
        assertFailsWith<TypeException> { check("1 ? 2 : 3") }
    }

    @Test
    fun `a ternary is the branch type when they agree, else any`() {
        assertThat(check("true ? 1 : 2")).isEqualTo(Type.NUMBER)
        assertThat(check("true ? 1 : 'a'")).isEqualTo(Type.ANY)
    }

    @Test
    fun `member access needs an object`() {
        assertFailsWith<TypeException> { check("age.x", mapOf("age" to Type.NUMBER)) }
        assertThat(check("user.name", mapOf("user" to Type.OBJECT))).isEqualTo(Type.ANY)
    }

    @Test
    fun `an undeclared variable is 'any' and defers to runtime`() {
        // no schema: unknown names are ANY, so nothing here is statically wrong
        assertThat(check("x")).isEqualTo(Type.ANY)
        assertThat(check("x + 1")).isEqualTo(Type.ANY)
        assertThat(check("x && flag")).isEqualTo(Type.BOOLEAN)
        assertThat(check("x < 5")).isEqualTo(Type.BOOLEAN)
    }

    @Test
    fun `a schema turns runtime type errors into compile-time ones`() {
        val schema = mapOf("age" to Type.NUMBER, "country" to Type.STRING)
        assertThat(check("age >= 18 && country in ['UK', 'IE']", schema)).isEqualTo(Type.BOOLEAN)
        // 'age' is a number, so using it as a boolean is now a static error
        assertFailsWith<TypeException> { check("age && true", schema) }
        assertFailsWith<TypeException> { check("country > 5", schema) }
    }

    @Test
    fun `catches a type error nested inside a list`() {
        assertFailsWith<TypeException> { check("[1, 2 + 'a']") }
    }

    @Test
    fun `catches errors the evaluator's short-circuit would hide`() {
        // at runtime `false && ...` never evaluates the right side, so this bug would slip past;
        // the type checker examines both sides and catches it statically
        assertFailsWith<TypeException> { check("false && (1 + 'a')") }
    }

    @Test
    fun `a type error carries the offending position`() {
        // "age && true": 'age' is a number used where a boolean is required, at column 1
        val e = assertFailsWith<TypeException> { check("age && true", mapOf("age" to Type.NUMBER)) }
        assertThat(e.pos.col).isEqualTo(1)
    }
}
