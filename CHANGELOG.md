# Changelog

All notable changes to expr4k are recorded here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and the project aims to follow
[Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

- Hand-written lexer with source positions and clear tokenisation errors.
- Precedence-climbing recursive-descent parser producing a sealed `Node` AST, with a recursion-depth
  cap so untrusted input cannot overflow the stack.
- Static type checker with gradual typing (an optional variable schema; unknown names are `ANY`).
- Tree-walking evaluator: short-circuiting `&&` / `||`, lazy `?:`, list membership, member access,
  and context-number coercion to the single `Double` number type.
- Public `Expr.compile(...)` facade with `eval` / `evalBoolean` / `evalNumber` / `evalString`.
- Unified, sealed `Expr4kException` hierarchy; every failure carries its source position.
- A REPL and one-shot CLI.
- Kotlin Multiplatform: the same library compiles to the JVM and to JavaScript (a ~64KB browser
  bundle with a JSON-in / JSON-out `evaluate` entry point).
- GitHub Actions CI (build + test), and an indicative microbenchmark.
- Maven Central (Central Portal) publishing configuration — see [PUBLISHING.md](PUBLISHING.md).

[Unreleased]: https://github.com/raby/expr4k/commits/main
