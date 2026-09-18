package com.digitalbluebird.expr4k.cli

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEqualTo
import kotlin.test.Test

class SessionTest {

    @Test
    fun `evaluates a constant expression and shows its type`() {
        assertThat(Session().handle("1 + 2")).isEqualTo("3.0 : number")
        assertThat(Session().handle("'a' + 'b'")).isEqualTo("\"ab\" : string")
        assertThat(Session().handle("1 < 2")).isEqualTo("true : boolean")
    }

    @Test
    fun `binds a variable with let and uses it later`() {
        val session = Session()
        assertThat(session.handle(":let age 21")).isEqualTo("age = 21.0 : number")
        assertThat(session.handle("age >= 18")).isEqualTo("true : boolean")
    }

    @Test
    fun `type-checks a bare expression against the current bindings`() {
        val session = Session()
        session.handle(":let age 21")
        assertThat(session.handle("age && true")).contains("expects a boolean")
    }

    @Test
    fun `evaluates the flagship predicate with bound variables`() {
        val session = Session()
        session.handle(":let age 21")
        session.handle(":let country 'UK'")
        assertThat(session.handle("age >= 18 && country in ['UK', 'IE']")).isEqualTo("true : boolean")
    }

    @Test
    fun `type command shows the inferred type without evaluating`() {
        assertThat(Session().handle(":type 'a' + 'b'")).isEqualTo("string")
        assertThat(Session().handle(":type 1 >= 2")).isEqualTo("boolean")
    }

    @Test
    fun `vars lists the bound variables`() {
        val session = Session()
        assertThat(session.handle(":vars")).contains("no variables")
        session.handle(":let x 1")
        assertThat(session.handle(":vars")).contains("x = 1.0 : number")
    }

    @Test
    fun `blank input produces no output`() {
        assertThat(Session().handle("   ")).isEqualTo("")
    }

    @Test
    fun `reports an evaluation error as a one-line message`() {
        assertThat(Session().handle("1 +")).contains("error:")
        assertThat(Session().handle("missing + 1")).contains("Undefined variable")
    }

    @Test
    fun `reports an unknown command`() {
        assertThat(Session().handle(":wat")).contains("unknown command")
    }

    @Test
    fun `help lists the commands`() {
        assertThat(Session().handle(":help")).contains(":let")
    }
}
