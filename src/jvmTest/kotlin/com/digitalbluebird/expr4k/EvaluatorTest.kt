package com.digitalbluebird.expr4k

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import kotlin.test.Test
import kotlin.test.assertFailsWith

class EvaluatorTest {

    private fun eval(source: String, context: Map<String, Any?> = emptyMap()): Any? =
        Evaluator(context).eval(Parser(Lexer(source).tokenize()).parse())

    @Test
    fun `evaluates arithmetic with precedence and grouping`() {
        assertThat(eval("2 + 3 * 4")).isEqualTo(14.0)
        assertThat(eval("(2 + 3) * 4")).isEqualTo(20.0)
        assertThat(eval("10 / 4")).isEqualTo(2.5)
        assertThat(eval("7 % 3")).isEqualTo(1.0)
        assertThat(eval("-5 + 2")).isEqualTo(-3.0)
    }

    @Test
    fun `division and modulo by zero are errors, not NaN or Infinity`() {
        assertFailsWith<EvalException> { eval("10 / 0") }
        assertFailsWith<EvalException> { eval("10 % 0") }
    }

    @Test
    fun `plus concatenates two strings`() {
        assertThat(eval("'a' + 'b' + 'c'")).isEqualTo("abc")
    }

    @Test
    fun `plus rejects mixing a number and a string`() {
        assertFailsWith<EvalException> { eval("1 + 'a'") }
    }

    @Test
    fun `compares numbers and strings, but not across types`() {
        assertThat(eval("3 < 5")).isEqualTo(true)
        assertThat(eval("5 <= 5")).isEqualTo(true)
        assertThat(eval("2 > 9")).isEqualTo(false)
        assertThat(eval("'apple' < 'banana'")).isEqualTo(true)
        assertFailsWith<EvalException> { eval("1 < 'x'") }
    }

    @Test
    fun `equality is structural and a type mismatch is false, not an error`() {
        assertThat(eval("1 == 1")).isEqualTo(true)
        assertThat(eval("1 != 2")).isEqualTo(true)
        assertThat(eval("'x' == 'x'")).isEqualTo(true)
        assertThat(eval("null == null")).isEqualTo(true)
        assertThat(eval("1 == 'x'")).isEqualTo(false)
        assertThat(eval("[1, 2] == [1, 2]")).isEqualTo(true)
    }

    @Test
    fun `evaluates logical operators and unary not`() {
        assertThat(eval("true && false")).isEqualTo(false)
        assertThat(eval("true || false")).isEqualTo(true)
        assertThat(eval("!false")).isEqualTo(true)
        assertThat(eval("!(1 < 2)")).isEqualTo(false)
    }

    @Test
    fun `logical operators short-circuit and never touch the dead side`() {
        // 'missing' would throw if evaluated; short-circuiting means it never is
        assertThat(eval("false && missing")).isEqualTo(false)
        assertThat(eval("true || missing")).isEqualTo(true)
    }

    @Test
    fun `the ternary evaluates only the branch it takes`() {
        assertThat(eval("1 < 2 ? 'yes' : 'no'")).isEqualTo("yes")
        assertThat(eval("true ? 1 : missing")).isEqualTo(1.0)
        assertThat(eval("false ? missing : 2")).isEqualTo(2.0)
    }

    @Test
    fun `evaluates list membership`() {
        assertThat(eval("'UK' in ['UK', 'IE']")).isEqualTo(true)
        assertThat(eval("'US' in ['UK', 'IE']")).isEqualTo(false)
        assertThat(eval("2 in [1, 2, 3]")).isEqualTo(true)
    }

    @Test
    fun `membership requires a list on the right`() {
        assertFailsWith<EvalException> { eval("2 in 3") }
    }

    @Test
    fun `reads variables from the context`() {
        assertThat(eval("age", mapOf("age" to 21.0))).isEqualTo(21.0)
        assertThat(eval("name", mapOf("name" to "Sam"))).isEqualTo("Sam")
        assertThat(eval("flag", mapOf("flag" to null))).isNull()
    }

    @Test
    fun `coerces context integers to the number type`() {
        // the README passes Int values; they must behave as numbers
        assertThat(eval("age + 1", mapOf("age" to 20))).isEqualTo(21.0)
        assertThat(eval("age >= 18", mapOf("age" to 21))).isEqualTo(true)
    }

    @Test
    fun `an undefined variable is an error`() {
        assertFailsWith<EvalException> { eval("missing") }
    }

    @Test
    fun `reads members of a nested object`() {
        val context = mapOf("user" to mapOf("age" to 30, "name" to "Sam"))
        assertThat(eval("user.age", context)).isEqualTo(30.0)
        assertThat(eval("user.name", context)).isEqualTo("Sam")
        assertThat(eval("user.age >= 18", context)).isEqualTo(true)
    }

    @Test
    fun `member access reports clear errors`() {
        assertFailsWith<EvalException> { eval("user.age", mapOf("user" to null)) }            // of null
        assertFailsWith<EvalException> { eval("user.height", mapOf("user" to mapOf("age" to 30))) } // missing
        assertFailsWith<EvalException> { eval("age.x", mapOf("age" to 30)) }                  // of a number
    }

    @Test
    fun `evaluates the flagship predicate end to end`() {
        val predicate = "age >= 18 && country in ['UK', 'IE']"
        assertThat(eval(predicate, mapOf("age" to 21, "country" to "UK"))).isEqualTo(true)
        assertThat(eval(predicate, mapOf("age" to 16, "country" to "UK"))).isEqualTo(false)
        assertThat(eval(predicate, mapOf("age" to 40, "country" to "US"))).isEqualTo(false)
    }

    @Test
    fun `a type error carries a source position`() {
        val e = assertFailsWith<EvalException> { eval("1 < 'x'") }
        assertThat(e.pos.line).isEqualTo(1)
    }
}
