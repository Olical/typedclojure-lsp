# Test Coverage, Schema Validation & Documentation

## Overview

Bring typedclojure-lsp from minimal test coverage to a well-tested, schema-validated codebase with clear documentation. The goal is to make further iteration clean and easy: bugs surface in tests, LSP protocol conformance is enforced by the official spec, and new contributors can understand the architecture without reading source.

## 1. Schema Infrastructure

### Fetching the LSP Spec

Fetch Microsoft's official `metaModel.json` from the LSP 3.17 specification and store it as a classpath resource. This is the most canonical, widely-depended-on source for LSP type definitions.

- **Source URL:** `https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/metaModel/metaModel.json`
- **Storage:** `resources/typedclojure-lsp/lsp-meta-model.json` (git-tracked)
- **Mise task:** `mise update-lsp-schema` fetches the latest version

### Converting to JSON Schema

The metaModel.json uses a custom type system (`{"kind": "reference", "name": "..."}` etc.), not JSON Schema. We write a converter namespace that transforms the structures we need into JSON Schema draft-07 with `$ref`-based `definitions`.

- **Namespace:** `typedclojure-lsp.schema.convert`
- **Output:** `resources/typedclojure-lsp/lsp-json-schema.json` (git-tracked)
- **Mise task:** `mise generate-lsp-json-schema` runs the converter
- **Scope:** Only converts types we actually use and their transitive dependencies

### Types to Cover Initially

- `InitializeParams`, `InitializeResult`
- `TextDocumentSyncOptions`
- `PublishDiagnosticsParams`, `Diagnostic`, `Range`, `Position`
- `DidOpenTextDocumentParams`, `DidSaveTextDocumentParams`
- `ServerCapabilities` (partial, the fields we populate)

### Schema Namespace

New namespace `typedclojure-lsp.schema` mirrors clojure-dap's approach:

- Mutable malli registry with `define!` for registering schemas
- `lsp-json-schema->malli` creates malli `:fn` validators backed by `luposlip/json-schema`, referencing definitions in the generated JSON Schema via `$ref`
- `validate` function returns nil on success, structured error on failure
- Explainer cache for efficient repeated validation

### Validation at Boundaries

`lsp.clj` handlers validate:
- **Incoming:** params for initialize, didOpen, didSave
- **Outgoing:** InitializeResult, PublishDiagnosticsParams

Validation failures log warnings but do not crash the server. A validation failure on outgoing messages indicates a bug in us, not the editor.

### New Dependency

- `luposlip/json-schema` (same JSON Schema validator clojure-dap uses)

## 2. Unit Tests

### test/typedclojure_lsp/path_test.clj (new)

- `classpath-dirs` returns a seq of strings, includes paths containing `"src"` and `"test"`
- `current-directory` returns a non-empty string that exists as a directory

### test/typedclojure_lsp/runner_test.clj (expand existing)

- Keep existing 3 test cases
- Add: verify the full shape of TypeError data from `ExceptionInfo->type-errors` (message, form, type-error, env with line/column/file) to document the internal API contract

### test/typedclojure_lsp/schema_test.clj (new)

- `lsp-json-schema->malli` produces schemas that validate correct LSP types and reject malformed ones
- `validate` returns nil on valid data, error on invalid
- Error messages are useful and descriptive

### test/typedclojure_lsp/schema/convert_test.clj (new)

- Converter transforms known metaModel structures (e.g. `Position`) into correct JSON Schema
- Handles references between types (e.g. `Range` referencing `Position`)
- Generated schema is valid and loadable by `luposlip/json-schema`

### test/typedclojure_lsp/lsp_test.clj (new)

- Unit tests for individual handler logic using spy to mock `runner/check-dirs` and `server/send-notification`
- `initialize`: given params with root-path, returns correct capabilities, sets root-uri atom
- `type-check-and-notify!`: given mock runner results with type errors, publishes correct diagnostics; given `:ok` result, clears previous diagnostics
- All outgoing messages validated against LSP schema in tests

### Test Output

Telemere logging active in tests. Kaocha's capture-output plugin captures it: quiet on pass, full dump on failure. Integration test helpers log every LSP message sent/received.

## 3. Integration Tests

### test/typedclojure_lsp/integration_test.clj (new)

#### Test Helper: `with-lsp-server`

- Creates a `core.async` channel pair (in-ch, out-ch)
- Starts the lsp4clj server with those channels instead of stdio
- Provides helpers to send JSON-RPC requests/notifications and read responses from the out channel (with timeout)
- Logs every message sent and received
- Validates all outgoing messages against the LSP JSON Schema
- Tears down the server after the test

#### Test Cases

1. **Full lifecycle** -- initialize -> initialized -> receive publishDiagnostics for `dev/examples` dir (known type error in `a-bad-fn`) -> verify diagnostics with correct file/line/column
2. **didSave triggers recheck** -- after initialize, send textDocument/didSave -> verify diagnostics published
3. **didOpen triggers recheck** -- same pattern with textDocument/didOpen
4. **Clean code produces empty diagnostics** -- point at directory with only valid typed code -> verify empty/cleared diagnostics
5. **Stale diagnostics cleared** -- trigger check with errors, then trigger with clean code -> verify previous file's diagnostics cleared

### test/typedclojure_lsp/smoke_test.clj (new)

Single end-to-end test: spawn the actual JVM process via `clojure -M -m typedclojure-lsp.main`, send initialize over stdin, read response from stdout, verify valid JSON-RPC with expected capabilities, shut down. Tests real entry point, stdio wiring, and clean shutdown. 60s timeout.

## 4. Documentation

### docs/architecture.md (new)

Three sections describing data flow so you don't need to read source:

1. **LSP Protocol Flow** -- how the editor talks to us: connect over stdio, initialize handshake, capabilities, didOpen/didSave triggers, diagnostics push. Covers JSON-RPC message format briefly.

2. **Typed Clojure Integration** -- how we invoke the type checker: resolve classpath dirs under project root, call `t/check-dir-clj`, handle ExceptionInfo with `:errors`, extract message/form/env into data maps, group by file.

3. **How We Sit In The Middle** -- the glue layer: main starts server, lsp handles protocol and orchestrates, runner wraps Typed Clojure, path resolves directories, schema validates at boundaries. Data flow diagram: `Editor <-stdio-> main <-lsp4clj-> lsp -> runner -> typed.clojure` with data shapes at each boundary.

### Docstrings

Add docstrings to all public functions across the codebase. Currently only `runner` has them consistently. Each docstring describes what the function does, its inputs, and its outputs.

## Implementation Order

1. **Schema infrastructure** -- fetch metaModel, write converter, create schema namespace, wire validation into lsp.clj
2. **Unit tests** -- path, schema, schema.convert, lsp, expand runner
3. **Integration tests** -- channel-pair integration tests, stdio smoke test
4. **Documentation** -- architecture.md, docstrings
