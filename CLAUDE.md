# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Typed Clojure LSP - an LSP server that brings Typed Clojure type checking into editors. Users get real-time type checking with diagnostics displayed inline. The server communicates over stdio using lsp4clj.

## Commands

All commands use [mise](https://mise.jdx.dev/) as the task runner (requires Java 21, Clojure 1.12.1):

- `mise test` - Run the Kaocha test suite
- `mise typecheck` - Run the Typed Clojure type checker on this codebase (dogfooding)
- `mise format` - Format code with cljfmt
- `mise dev` - Start LSP with nREPL + FlowStorm debugger for development
- `mise build` - Build JAR artifact
- `mise deploy` - Deploy to Clojars
- `mise antq` - Check for outdated dependencies

To run a single test, use Kaocha's `--focus` flag: `clojure -M:test:dev --focus typedclojure-lsp.runner-test/check-dirs`

## Architecture

Four namespaces, linear data flow:

1. **`main`** - Entry point. Configures telemere logging to stderr, starts stdio LSP server.
2. **`lsp`** - LSP protocol handlers (initialize, didOpen, didSave). On each event, triggers type checking via `runner`, then publishes diagnostics back to the editor. Tracks files-with-diagnostics in an atom to clear stale diagnostics.
3. **`runner`** - Wraps `typed.clojure/check-dir-clj`. Catches ExceptionInfo from the type checker and converts errors to data maps (`{:result :ok | :type-errors | :exception}`). Also serves as CLI entry point (`-m typedclojure-lsp.runner`) for CI usage.
4. **`path`** - Classpath and working directory resolution. Used by both `lsp` and `runner` to determine which directories to type check.

LSP context (server, atoms for root-uri and files-with-diagnostics) is threaded through all handlers as a map.

## Key Conventions

- Namespaces that are type-checked use `^:typed.clojure` metadata on the `ns` form (currently `runner` and `path`; `lsp` has a TODO to add it).
- Type annotations use `t/ann` and `t/defalias` from `typed.clojure`.
- Tests use `matcher-combinators` for flexible assertions (`match?`).
- The project dogfoods itself: `mise typecheck` runs this project's own type checker against its source.
- Per-project editor setup uses `.typedclojure-lsp/start` scripts (see README for editor config).
