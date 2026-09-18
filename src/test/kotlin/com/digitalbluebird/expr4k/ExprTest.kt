package com.digitalbluebird.expr4k

import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlin.test.Test
import kotlin.test.assertFailsWith

class ExprTest {

    @Test
    fun `compiles once and evaluates a predicate against many contexts`() {
        val expr = Expr.compile("age >= 18 && country in ['UK', 'IE']")
        assertThat(expr.evalBoolean(mapOf("age" to 21, "country" to "UK"))).isEqualTo(true)
        assertThat(expr.evalBoolean(mapOf("age" to 16, "country" to "UK"))).isEqualTo(false)
        assertThat(expr.evalBoolean(mapOf("age" to 40, "country" to "US"))).isEqualTo(false)
    }

    @Test
    fun `evaluates to a number, a string and a raw value`() {
        assertThat(Expr.compile("age * 2").evalNumber(mapOf("age" to 21))).isEqualTo(42.0)
        assertThat(Expr.compile("'hi ' + name").evalString(mapOf("name" to "Sam"))).isEqualTo("hi Sam")
        assertThat(Expr.compile("[1, 2, 3]").eval()).isEqualTo(listOf(1.0, 2.0, 3.0))
    }

    @Test
    fun `evaluates a constant expression with no context`() {
        assertThat(Expr.compile("1 < 2").evalBoolean()).isEqualTo(true)
    }

    @Test
    fun `exposes the statically inferred type`() {
        assertThat(Expr.compile("age >= 18", mapOf("age" to Type.NUMBER)).type).isEqualTo(Type.BOOLEAN)
        assertThat(Expr.compile("age + 1", mapOf("age" to Type.NUMBER)).type).isEqualTo(Type.NUMBER)
        assertThat(Expr.compile("x").type).isEqualTo(Type.ANY)
    }

    @Test
    fun `a schema makes compile reject a type error up front`() {
        assertFailsWith<TypeException> { Expr.compile("age && true", mapOf("age" to Type.NUMBER)) }
    }

    @Test
    fun `compile surfaces lexing and parsing errors`() {
        assertFailsWith<LexException> { Expr.compile("a @ b") }
        assertFailsWith<ParseException> { Expr.compile("1 +") }
    }

    @Test
    fun `every failure is catchable as one Expr4kException carrying a position`() {
        val e = assertFailsWith<Expr4kException> { Expr.compile("1 +") }
        assertThat(e.pos.line).isEqualTo(1)
    }

    @Test
    fun `evalBoolean on a non-boolean result is an error`() {
        assertFailsWith<EvalException> { Expr.compile("1 + 2").evalBoolean() }
    }

    @Test
    fun `evalNumber on a non-number result is an error`() {
        assertFailsWith<EvalException> { Expr.compile("'x'").evalNumber() }
    }
}
