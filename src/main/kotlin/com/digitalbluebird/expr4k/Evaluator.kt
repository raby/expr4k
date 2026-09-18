package com.digitalbluebird.expr4k

/** Raised when a well-formed tree cannot be evaluated: a type mismatch, an undefined name, and such. */
public class EvalException(message: String, pos: Pos) : Expr4kException(message, pos)

/** The expr4k type name of a runtime [value], for error messages. Shared across the evaluator and facade. */
internal fun typeName(value: Any?): String = when (value) {
    null -> "null"
    is Double -> "number"
    is String -> "string"
    is Boolean -> "boolean"
    is List<*> -> "list"
    is Map<*, *> -> "object"
    else -> value::class.simpleName ?: "value"
}

/**
 * Evaluates a [Node] tree against a [context] of variable bindings.
 *
 * Values are [Double] (the single number type), [String], [Boolean], `null`, a [List], or a nested
 * [Map] (an "object", reached by member access). Numbers arriving through the [context] are coerced
 * to [Double], so an `Int` binding compares equal to a numeric literal.
 *
 * `&&` and `||` short-circuit; `?:` evaluates only the branch it takes. Type mismatches are caught
 * here, at evaluation time; a dedicated type-checking stage comes later. Construct one per evaluation.
 */
public class Evaluator(private val context: Map<String, Any?> = emptyMap()) {

    /** Evaluate [node] to a value, or throw [EvalException]. */
    public fun eval(node: Node): Any? = when (node) {
        is NumberLit -> node.value
        is StringLit -> node.value
        is BoolLit -> node.value
        is NullLit -> null
        is ListLit -> node.elements.map { eval(it) }
        is Identifier -> readVariable(node)
        is Member -> readMember(node)
        is Unary -> evalUnary(node)
        is Binary -> evalBinary(node)
        is Ternary -> evalTernary(node)
    }

    private fun readVariable(node: Identifier): Any? {
        if (node.name !in context) throw EvalException("Undefined variable '${node.name}'", node.pos)
        return normalize(context[node.name])
    }

    private fun readMember(node: Member): Any? {
        val target = eval(node.target)
        if (target !is Map<*, *>) {
            throw EvalException("Cannot read member '${node.name}' of ${typeName(target)}", node.pos)
        }
        if (node.name !in target) throw EvalException("Object has no member '${node.name}'", node.pos)
        return normalize(target[node.name])
    }

    private fun evalTernary(node: Ternary): Any? {
        val condition = eval(node.condition)
        if (condition !is Boolean) {
            throw EvalException(
                "The condition of '?:' must be a boolean but was ${typeName(condition)}",
                node.condition.pos,
            )
        }
        return if (condition) eval(node.ifTrue) else eval(node.ifFalse)
    }

    private fun evalUnary(node: Unary): Any? {
        val operand = eval(node.operand)
        return when (node.op) {
            UnaryOp.NOT -> {
                if (operand !is Boolean) typeError("'!'", "a boolean", operand, node.pos)
                !operand
            }
            UnaryOp.NEG -> {
                if (operand !is Double) typeError("unary '-'", "a number", operand, node.pos)
                -operand
            }
        }
    }

    private fun evalBinary(node: Binary): Any? = when (node.op) {
        BinaryOp.OR, BinaryOp.AND -> evalLogical(node)
        BinaryOp.EQ -> eval(node.left) == eval(node.right)
        BinaryOp.NEQ -> eval(node.left) != eval(node.right)
        BinaryOp.LT, BinaryOp.LTE, BinaryOp.GT, BinaryOp.GTE -> evalComparison(node)
        BinaryOp.IN -> evalMembership(node)
        BinaryOp.ADD -> evalPlus(node)
        BinaryOp.SUB, BinaryOp.MUL, BinaryOp.DIV, BinaryOp.MOD -> evalArithmetic(node)
    }

    private fun evalLogical(node: Binary): Boolean {
        val left = eval(node.left)
        if (left !is Boolean) typeError("operator '${node.op.symbol}'", "a boolean", left, node.left.pos)
        // short-circuit: don't touch the right side when the left already decides the result
        if (node.op == BinaryOp.AND && !left) return false
        if (node.op == BinaryOp.OR && left) return true
        val right = eval(node.right)
        if (right !is Boolean) typeError("operator '${node.op.symbol}'", "a boolean", right, node.right.pos)
        return right
    }

    private fun evalComparison(node: Binary): Boolean {
        val left = eval(node.left)
        val right = eval(node.right)
        val order: Int = when {
            left is Double && right is Double -> left.compareTo(right)
            left is String && right is String -> left.compareTo(right)
            else -> throw EvalException(
                "operator '${node.op.symbol}' expects two numbers or two strings but got " +
                    "${typeName(left)} and ${typeName(right)}",
                node.pos,
            )
        }
        return when (node.op) {
            BinaryOp.LT -> order < 0
            BinaryOp.LTE -> order <= 0
            BinaryOp.GT -> order > 0
            BinaryOp.GTE -> order >= 0
            else -> error("not a comparison operator: ${node.op}")
        }
    }

    private fun evalMembership(node: Binary): Boolean {
        val needle = eval(node.left)
        val haystack = eval(node.right)
        if (haystack !is List<*>) {
            throw EvalException(
                "operator 'in' expects a list on the right but got ${typeName(haystack)}",
                node.right.pos,
            )
        }
        return needle in haystack
    }

    private fun evalPlus(node: Binary): Any {
        val left = eval(node.left)
        val right = eval(node.right)
        return when {
            left is Double && right is Double -> left + right
            left is String && right is String -> left + right
            else -> throw EvalException(
                "operator '+' expects two numbers or two strings but got ${typeName(left)} and ${typeName(right)}",
                node.pos,
            )
        }
    }

    private fun evalArithmetic(node: Binary): Double {
        val left = eval(node.left)
        if (left !is Double) typeError("operator '${node.op.symbol}'", "two numbers", left, node.left.pos)
        val right = eval(node.right)
        if (right !is Double) typeError("operator '${node.op.symbol}'", "two numbers", right, node.right.pos)
        return when (node.op) {
            BinaryOp.SUB -> left - right
            BinaryOp.MUL -> left * right
            BinaryOp.DIV -> if (right == 0.0) throw EvalException("Division by zero", node.pos) else left / right
            BinaryOp.MOD -> if (right == 0.0) throw EvalException("Modulo by zero", node.pos) else left % right
            else -> error("not an arithmetic operator: ${node.op}")
        }
    }

    private companion object {
        /** Coerce a raw context value into the evaluator's world: every [Number] becomes a [Double],
         *  recursively through lists and objects, so equality and comparison behave uniformly. */
        fun normalize(value: Any?): Any? = when (value) {
            null, is Double, is String, is Boolean -> value
            is Number -> value.toDouble()
            is List<*> -> value.map { normalize(it) }
            is Map<*, *> -> value.entries.associate { (k, v) -> k.toString() to normalize(v) }
            else -> value
        }

        fun typeError(operator: String, expected: String, got: Any?, pos: Pos): Nothing =
            throw EvalException("$operator expects $expected but got ${typeName(got)}", pos)
    }
}
