package com.digitalbluebird.expr4k

/** Raised when tokens cannot be parsed into a syntax tree. Carries the [pos] of the offending token. */
public class ParseException(message: String, pos: Pos) : Expr4kException(message, pos)

/**
 * Parses a flat [tokens] list (as produced by [Lexer]) into a [Node] tree.
 *
 * Precedence-climbing recursive descent: one method per precedence level, loosest first. The list
 * must end with a [TokenType.EOF] token. Single-use, like [Lexer]: one instance per token list.
 *
 * Precedence, loosest to tightest:
 * ```
 * ternary        ? :                 (right)
 * or             ||                  (left)
 * and            &&                  (left)
 * equality       == !=               (left)
 * comparison     < <= > >= in        (left)
 * additive       + -                 (left)
 * multiplicative * / %               (left)
 * unary          ! -                 (prefix)
 * postfix        .member             (left)
 * primary        literals, names, ( ), [ ]
 * ```
 */
public class Parser(private val tokens: List<Token>, private val maxDepth: Int = MAX_DEPTH) {
    private var current = 0
    private var depth = 0

    /** Parse the tokens into a single expression tree, or throw [ParseException]. */
    public fun parse(): Node {
        val node = expression()
        if (!check(TokenType.EOF)) throw error(peek(), "Unexpected trailing input ${describe(peek())}")
        return node
    }

    /**
     * Run [body] one recursion level deeper, capping nesting at [maxDepth] so untrusted input cannot
     * overflow the stack. Wraps the two unbounded recursion points: [expression] (grouping, list
     * elements, ternary branches) and prefix [unary] chains.
     */
    private inline fun <T> nested(body: () -> T): T {
        if (++depth > maxDepth) throw error(peek(), "Expression nested too deeply (limit $maxDepth)")
        try {
            return body()
        } finally {
            depth--
        }
    }

    private fun expression(): Node = nested { ternary() }

    private fun ternary(): Node {
        val condition = or()
        if (match(TokenType.QUESTION)) {
            val ifTrue = expression()
            consume(TokenType.COLON, "Expected ':' in conditional expression")
            val ifFalse = expression()
            return Ternary(condition, ifTrue, ifFalse, condition.pos)
        }
        return condition
    }

    private fun or(): Node = leftAssoc(::and, TokenType.OR)

    private fun and(): Node = leftAssoc(::equality, TokenType.AND)

    private fun equality(): Node = leftAssoc(::comparison, TokenType.EQ, TokenType.NEQ)

    private fun comparison(): Node =
        leftAssoc(::additive, TokenType.LT, TokenType.LTE, TokenType.GT, TokenType.GTE, TokenType.IN)

    private fun additive(): Node = leftAssoc(::multiplicative, TokenType.PLUS, TokenType.MINUS)

    private fun multiplicative(): Node =
        leftAssoc(::unary, TokenType.STAR, TokenType.SLASH, TokenType.PERCENT)

    /**
     * Parse a left-associative run of binary operators at one precedence level: an [operand] followed
     * by zero or more `(op operand)` pairs, folded left. Every operator here maps to a [BinaryOp].
     */
    private fun leftAssoc(operand: () -> Node, vararg operators: TokenType): Node {
        var left = operand()
        while (match(*operators)) {
            val op = binaryOp(previous().type)
            val right = operand()
            left = Binary(op, left, right, left.pos)
        }
        return left
    }

    private fun unary(): Node {
        if (match(TokenType.NOT, TokenType.MINUS)) {
            val opToken = previous()
            val op = if (opToken.type == TokenType.NOT) UnaryOp.NOT else UnaryOp.NEG
            return nested { Unary(op, unary(), opToken.pos) }
        }
        return postfix()
    }

    private fun postfix(): Node {
        var node = primary()
        while (match(TokenType.DOT)) {
            val name = consume(TokenType.IDENT, "Expected a property name after '.'")
            node = Member(node, name.lexeme, node.pos)
        }
        return node
    }

    private fun primary(): Node {
        val token = peek()
        return when (token.type) {
            TokenType.NUMBER -> { advance(); NumberLit(token.literal as Double, token.pos) }
            TokenType.STRING -> { advance(); StringLit(token.literal as String, token.pos) }
            TokenType.TRUE -> { advance(); BoolLit(true, token.pos) }
            TokenType.FALSE -> { advance(); BoolLit(false, token.pos) }
            TokenType.NULL -> { advance(); NullLit(token.pos) }
            TokenType.IDENT -> { advance(); Identifier(token.lexeme, token.pos) }
            TokenType.LPAREN -> {
                advance()
                val inner = expression()
                consume(TokenType.RPAREN, "Expected ')' to close the group")
                inner
            }
            TokenType.LBRACKET -> { advance(); listLiteral(token.pos) }
            else -> throw error(token, "Unexpected ${describe(token)}")
        }
    }

    /** Parse the elements of a list literal after the opening `[` has been consumed. */
    private fun listLiteral(startPos: Pos): Node {
        val elements = mutableListOf<Node>()
        if (!check(TokenType.RBRACKET)) {
            do {
                elements.add(expression())
            } while (match(TokenType.COMMA))
        }
        consume(TokenType.RBRACKET, "Expected ']' to close the list")
        return ListLit(elements, startPos)
    }

    // --- token helpers ---

    private fun match(vararg types: TokenType): Boolean {
        if (types.any { check(it) }) {
            advance()
            return true
        }
        return false
    }

    private fun check(type: TokenType): Boolean = peek().type == type

    private fun advance(): Token {
        if (!isAtEnd()) current++
        return previous()
    }

    private fun consume(type: TokenType, message: String): Token {
        if (check(type)) return advance()
        throw error(peek(), message)
    }

    private fun isAtEnd(): Boolean = peek().type == TokenType.EOF

    private fun peek(): Token = tokens[current]

    private fun previous(): Token = tokens[current - 1]

    private fun error(token: Token, message: String): ParseException = ParseException(message, token.pos)

    /** Render a token for an error message: a quoted lexeme, or a friendly name for end-of-input. */
    private fun describe(token: Token): String =
        if (token.type == TokenType.EOF) "end of input" else "'${token.lexeme}'"

    private companion object {
        /** Nesting cap: deep enough for any hand-written expression, shallow enough to never overflow. */
        const val MAX_DEPTH = 200

        /** Map an operator token kind to its [BinaryOp]. Only ever called on a matched operator token. */
        fun binaryOp(type: TokenType): BinaryOp = when (type) {
            TokenType.OR -> BinaryOp.OR
            TokenType.AND -> BinaryOp.AND
            TokenType.EQ -> BinaryOp.EQ
            TokenType.NEQ -> BinaryOp.NEQ
            TokenType.LT -> BinaryOp.LT
            TokenType.LTE -> BinaryOp.LTE
            TokenType.GT -> BinaryOp.GT
            TokenType.GTE -> BinaryOp.GTE
            TokenType.IN -> BinaryOp.IN
            TokenType.PLUS -> BinaryOp.ADD
            TokenType.MINUS -> BinaryOp.SUB
            TokenType.STAR -> BinaryOp.MUL
            TokenType.SLASH -> BinaryOp.DIV
            TokenType.PERCENT -> BinaryOp.MOD
            else -> error("$type is not a binary operator") // unreachable: leftAssoc only matches operators
        }
    }
}
