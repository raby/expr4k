package com.digitalbluebird.expr4k

/** Raised when the type checker finds a static type error. Carries the [pos] of the offending node. */
public class TypeException(message: String, pos: Pos) : Expr4kException(message, pos)

/**
 * The static type of an expression's value.
 *
 * [ANY] is the gradual-typing escape hatch: it stands for a type not known statically (an undeclared
 * variable, a list element, an object member) and is compatible with every other type.
 */
public enum class Type { NUMBER, STRING, BOOLEAN, NULL, LIST, OBJECT, ANY }

/**
 * A static pass over a [Node] tree that infers each node's [Type] and throws [TypeException] on a
 * mismatch, before evaluation.
 *
 * Variables resolve against an optional [schema] (name to [Type]); a name absent from the schema is
 * [Type.ANY], so a partial schema still checks everything it can and defers the rest to runtime.
 * With no schema, the pass verifies only what the literals and operators make knowable.
 */
public class TypeChecker(private val schema: Map<String, Type> = emptyMap()) {

    /** Infer the type of [node], or throw [TypeException]. */
    public fun check(node: Node): Type = when (node) {
        is NumberLit -> Type.NUMBER
        is StringLit -> Type.STRING
        is BoolLit -> Type.BOOLEAN
        is NullLit -> Type.NULL
        is ListLit -> {
            node.elements.forEach { check(it) } // catch errors inside the elements; element types are not tracked
            Type.LIST
        }
        is Identifier -> schema[node.name] ?: Type.ANY
        is Member -> checkMember(node)
        is Unary -> checkUnary(node)
        is Binary -> checkBinary(node)
        is Ternary -> checkTernary(node)
    }

    private fun checkMember(node: Member): Type {
        val target = check(node.target)
        if (target != Type.OBJECT && target != Type.ANY) {
            throw TypeException("A ${label(target)} has no members (reading '${node.name}')", node.pos)
        }
        return Type.ANY // member types are not tracked; defer to runtime
    }

    private fun checkUnary(node: Unary): Type = when (node.op) {
        UnaryOp.NOT -> { expect(check(node.operand), Type.BOOLEAN, "'!'", node.operand.pos); Type.BOOLEAN }
        UnaryOp.NEG -> { expect(check(node.operand), Type.NUMBER, "unary '-'", node.operand.pos); Type.NUMBER }
    }

    private fun checkTernary(node: Ternary): Type {
        expect(check(node.condition), Type.BOOLEAN, "the condition of '?:'", node.condition.pos)
        return join(check(node.ifTrue), check(node.ifFalse))
    }

    private fun checkBinary(node: Binary): Type {
        val left = check(node.left)
        val right = check(node.right)
        return when (node.op) {
            BinaryOp.OR, BinaryOp.AND -> {
                expect(left, Type.BOOLEAN, "operator '${node.op.symbol}'", node.left.pos)
                expect(right, Type.BOOLEAN, "operator '${node.op.symbol}'", node.right.pos)
                Type.BOOLEAN
            }
            // equality is defined for any pair of values, so it never fails to type-check
            BinaryOp.EQ, BinaryOp.NEQ -> Type.BOOLEAN
            BinaryOp.LT, BinaryOp.LTE, BinaryOp.GT, BinaryOp.GTE -> {
                requireOrderable(left, right, node)
                Type.BOOLEAN
            }
            BinaryOp.IN -> {
                expect(right, Type.LIST, "operator 'in'", node.right.pos)
                Type.BOOLEAN
            }
            BinaryOp.ADD -> checkPlus(left, right, node)
            BinaryOp.SUB, BinaryOp.MUL, BinaryOp.DIV, BinaryOp.MOD -> {
                expect(left, Type.NUMBER, "operator '${node.op.symbol}'", node.left.pos)
                expect(right, Type.NUMBER, "operator '${node.op.symbol}'", node.right.pos)
                Type.NUMBER
            }
        }
    }

    private fun checkPlus(left: Type, right: Type, node: Binary): Type = when {
        left == Type.ANY || right == Type.ANY -> Type.ANY // could resolve to numeric add or string concat
        left == Type.NUMBER && right == Type.NUMBER -> Type.NUMBER
        left == Type.STRING && right == Type.STRING -> Type.STRING
        else -> throw TypeException(
            "operator '+' expects two numbers or two strings but got ${label(left)} and ${label(right)}",
            node.pos,
        )
    }

    private fun requireOrderable(left: Type, right: Type, node: Binary) {
        val ok = left == Type.ANY || right == Type.ANY ||
            (left == Type.NUMBER && right == Type.NUMBER) ||
            (left == Type.STRING && right == Type.STRING)
        if (!ok) {
            throw TypeException(
                "operator '${node.op.symbol}' expects two numbers or two strings but got " +
                    "${label(left)} and ${label(right)}",
                node.pos,
            )
        }
    }

    private companion object {
        /** Assert [actual] satisfies the single [expected] type, allowing [Type.ANY] through. */
        fun expect(actual: Type, expected: Type, what: String, pos: Pos) {
            if (actual != expected && actual != Type.ANY) {
                throw TypeException("$what expects a ${label(expected)} but got ${label(actual)}", pos)
            }
        }

        /** The type of an expression that could be one of two branch types: their shared type, else [Type.ANY]. */
        fun join(a: Type, b: Type): Type = if (a == b) a else Type.ANY

        fun label(type: Type): String = when (type) {
            Type.NUMBER -> "number"
            Type.STRING -> "string"
            Type.BOOLEAN -> "boolean"
            Type.NULL -> "null"
            Type.LIST -> "list"
            Type.OBJECT -> "object"
            Type.ANY -> "any"
        }
    }
}
