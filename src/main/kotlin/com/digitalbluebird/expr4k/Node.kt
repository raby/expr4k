package com.digitalbluebird.expr4k

/**
 * A node in the expression syntax tree produced by [Parser].
 *
 * Named [Node] rather than `Expr` so the public facade can own the `Expr` name. Every node carries
 * the [pos] where it starts, so later stages (type checking, evaluation) can point errors at source.
 */
public sealed interface Node {
    public val pos: Pos
}

/** A numeric literal. Values are always [Double]; the language has a single number type. */
public data class NumberLit(val value: Double, override val pos: Pos) : Node

/** A string literal, already unescaped by the lexer. */
public data class StringLit(val value: String, override val pos: Pos) : Node

/** A boolean literal (`true` / `false`). */
public data class BoolLit(val value: Boolean, override val pos: Pos) : Node

/** The `null` literal. */
public data class NullLit(override val pos: Pos) : Node

/** A list literal, e.g. `[1, 2, 3]`; may be empty. */
public data class ListLit(val elements: List<Node>, override val pos: Pos) : Node

/** A bare name to be resolved against the evaluation context, e.g. `age`. */
public data class Identifier(val name: String, override val pos: Pos) : Node

/** Member access, e.g. `user.age` — [name] read from the value of [target]. */
public data class Member(val target: Node, val name: String, override val pos: Pos) : Node

/** A prefix unary operation, e.g. `!flag` or `-x`. */
public data class Unary(val op: UnaryOp, val operand: Node, override val pos: Pos) : Node

/** A binary operation, e.g. `a + b`, `x >= 1`, `c in [..]`. Short-circuiting of [BinaryOp.AND] /
 *  [BinaryOp.OR] is the evaluator's concern, not the tree's. */
public data class Binary(val op: BinaryOp, val left: Node, val right: Node, override val pos: Pos) : Node

/** A conditional, `condition ? ifTrue : ifFalse`. */
public data class Ternary(
    val condition: Node,
    val ifTrue: Node,
    val ifFalse: Node,
    override val pos: Pos,
) : Node

/** The prefix unary operators. */
public enum class UnaryOp { NOT, NEG }

/**
 * The binary operators, grouped loosest-binding first for readability (precedence lives in [Parser]).
 * Each carries its source [symbol] for error messages, shared by the evaluator and type checker.
 */
public enum class BinaryOp(internal val symbol: String) {
    OR("||"),
    AND("&&"),
    EQ("=="),
    NEQ("!="),
    LT("<"),
    LTE("<="),
    GT(">"),
    GTE(">="),
    IN("in"),
    ADD("+"),
    SUB("-"),
    MUL("*"),
    DIV("/"),
    MOD("%"),
}
