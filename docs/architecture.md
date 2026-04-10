# Architecture

typedclojure-lsp is an LSP server that bridges editors to Typed Clojure's type checker. It runs as a stdio process, receives LSP notifications, invokes `typed.clojure/check-dir-clj`, and pushes diagnostics back.

## 1. LSP Protocol Flow

Communication uses **stdio** (stdin/stdout) with **JSON-RPC 2.0** messages, handled by the `lsp4clj` library.

### Sequence

```
Editor                          main                 lsp                  runner
  |                               |                    |                     |
  |--- stdin: initialize -------->|                    |                     |
  |                               |---> server/receive-request "initialize"  |
  |                               |     stores root-path in root-uri! atom   |
  |                               |     returns capabilities                 |
  |<-- stdout: InitializeResult --|                    |                     |
  |                               |                    |                     |
  |--- stdin: initialized ------->|                    |                     |
  |                               |---> type-check-and-notify!               |
  |                               |                    |---> check-dirs ---->|
  |                               |                    |<--- result data ----|
  |<-- stdout: publishDiagnostics-|                    |                     |
  |                               |                    |                     |
  |--- stdin: didOpen ----------->|                    |                     |
  |                               |---> type-check-and-notify!               |
  |<-- stdout: publishDiagnostics-|                    |                     |
  |                               |                    |                     |
  |--- stdin: didSave ----------->|                    |                     |
  |                               |---> type-check-and-notify!               |
  |<-- stdout: publishDiagnostics-|                    |                     |
```

### Initialize Handshake

The editor sends `initialize` with `root-path`. The server responds with:

```clojure
{:capabilities {:textDocumentSync {:openClose true
                                    :save {:includeText false}}}
 :serverInfo {:name "typedclojure"}}
```

This tells the editor: send us open/close and save events, but we don't need the file text on save (we re-check whole directories).

### Triggers

Three events trigger a full type check: `initialized`, `textDocument/didOpen`, and `textDocument/didSave`. Each calls the same `type-check-and-notify!` function.

### Diagnostics Push

After each check, the server first clears diagnostics for all previously-flagged files (sending empty diagnostics arrays), then publishes new diagnostics for any files with type errors.

## 2. Typed Clojure Integration

### Classpath Dir Resolution

Typed Clojure checks entire directories, not individual files. The `path` namespace provides `classpath-dirs`, which calls `clojure.java.classpath/classpath` and returns canonical paths for each entry.

The `lsp` namespace filters these to only directories that start with the project's `root-uri` (from the initialize handshake). This prevents checking dependencies or unrelated classpath entries.

### Invoking the Type Checker

`runner/check-dirs` wraps `typed.clojure/check-dir-clj` in a try/catch:

```
check-dirs(dirs)
  |
  |---> t/check-dir-clj(dirs)
  |
  |---> success: {:result :ok}
  |
  |---> ExceptionInfo caught:
  |       extract (.errors (ex-data e))
  |       for each error:
  |         ex-message -> :message
  |         ex-data    -> :form, :type-error, :env {:line :column :file}
  |       return {:result :type-errors, :type-errors [...]}
  |
  |---> Throwable caught:
  |       return {:result :exception, :exception e}
```

### Error Data Shape

Typed Clojure throws `ExceptionInfo` with `:errors` in its ex-data. Each error is itself an `ExceptionInfo` with:

```clojure
;; ex-data of each individual error
{:form   <the-form>        ;; the Clojure form that failed
 :type-error <keyword>     ;; e.g. :clojure.core.typed.errors/top-level-error
 :env    {:line   <int>
          :column <int>
          :file   <string>}}  ;; absolute path
```

`ExceptionInfo->type-errors` flattens these into a sequence of maps with `:message`, `:form`, `:type-error`, and `:env`.

## 3. How We Sit In The Middle

### Component Diagram

```
                            typedclojure-lsp
              +---------------------------------------------+
              |                                             |
 Editor <-stdio-> main <-lsp4clj-> lsp -> runner -> typed.clojure
              |                      |                      |
              |                      +-> path (dir lookup)  |
              |                      |                      |
              |                      +-> schema (validate)  |
              +---------------------------------------------+
```

### Component Responsibilities

| Component | Namespace | Role |
|-----------|-----------|------|
| **main** | `typedclojure-lsp.main` | Entry point. Configures logging to stderr, sets up uncaught exception handler, calls `lsp/start-stdio-server!`, blocks on server start, then shuts down. |
| **lsp** | `typedclojure-lsp.lsp` | Protocol handler. Implements `initialize`, `initialized`, `didOpen`, `didSave` via lsp4clj multimethods. Owns the context map. Converts runner output to LSP diagnostics. |
| **runner** | `typedclojure-lsp.runner` | Type checker wrapper. Calls `t/check-dir-clj`, catches exceptions, returns structured data. Also has a `-main` for CLI use (CI). |
| **path** | `typedclojure-lsp.path` | Directory resolution. `classpath-dirs` returns all classpath entries as canonical paths. `current-directory` returns CWD (used by runner's CLI mode). |
| **schema** | `typedclojure-lsp.schema` | Validation layer. Maintains a mutable malli registry. `define!` registers schemas, `validate` checks values. `lsp-json-schema->malli` bridges LSP JSON Schema definitions into malli validators via `luposlip/json-schema`. |
| **schema.convert** | `typedclojure-lsp.schema.convert` | Build-time tool. Converts the LSP meta-model JSON into a JSON Schema file used at runtime by the schema namespace. |

### Data Shapes at Each Boundary

**LSP context map** (created in `start-stdio-server!`, passed to all handlers):

```clojure
{:server                <lsp4clj-server>   ;; send notifications through this
 :files-with-diagnostics! (atom #{})        ;; tracks files we've sent diagnostics for
 :root-uri!              (atom nil)}        ;; project root path, set on initialize
```

**Runner output** (returned by `check-dirs`):

```clojure
;; Success
{:result :ok}

;; Type errors found
{:result :type-errors
 :type-errors [{:message "Type mismatch..."
                :form    '(inc "a")
                :type-error :clojure.core.typed.errors/top-level-error
                :env     {:line 5 :column 1 :file "/path/to/file.clj"}}]}

;; Unexpected exception
{:result :exception
 :exception <Throwable>}
```

**Outgoing diagnostics** (sent via `textDocument/publishDiagnostics`):

```clojure
{:uri "/path/to/file.clj"
 :diagnostics [{:source  "typedclojure"
                :message "Type mismatch..."
                :range   {:start {:line 5 :character 1}
                          :end   {:line 5 :character 10}}}]}
```

The `lsp` namespace validates both incoming and outgoing messages against LSP JSON Schema definitions (registered as `::initialize-params`, `::initialize-result`, `::publish-diagnostics-params`). Validation failures log warnings but do not block message processing.
