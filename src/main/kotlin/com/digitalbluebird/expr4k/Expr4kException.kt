package com.digitalbluebird.expr4k

/**
 * Base type for every error expr4k raises, each carrying the source [pos] it refers to.
 *
 * Sealed, so a caller can catch [Expr4kException] to handle any failure — lexing, parsing, type
 * checking or evaluation — uniformly, or catch a specific subtype ([LexException], [ParseException],
 * [TypeException], [EvalException]) when they need to tell them apart.
 */
public sealed class Expr4kException(message: String, public val pos: Pos) : Exception("$message at $pos")
