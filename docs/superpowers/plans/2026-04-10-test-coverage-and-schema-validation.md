# Test Coverage, Schema Validation & Documentation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Bring typedclojure-lsp from minimal test coverage to a well-tested, schema-validated codebase with architecture documentation.

**Architecture:** Fetch Microsoft's official LSP metaModel.json and convert the types we use into JSON Schema draft-07. Wire malli-based validation at LSP boundaries using luposlip/json-schema (same approach as clojure-dap). Add comprehensive unit tests, channel-pair integration tests, a stdio smoke test, and architecture docs.

**Tech Stack:** Clojure 1.12.1, lsp4clj 1.13.1, malli (via typed.malli), luposlip/json-schema 0.4.7, kaocha, matcher-combinators, spy, core.async

---

## File Structure

### New Files
- `resources/typedclojure-lsp/lsp-meta-model.json` — fetched from Microsoft, git-tracked
- `resources/typedclojure-lsp/lsp-json-schema.json` — generated JSON Schema, git-tracked
- `src/typedclojure_lsp/schema.clj` — schema registry, validation, JSON Schema->malli bridge
- `src/typedclojure_lsp/schema/convert.clj` — metaModel.json to JSON Schema converter
- `test/typedclojure_lsp/path_test.clj` — unit tests for path namespace
- `test/typedclojure_lsp/schema_test.clj` — unit tests for schema namespace
- `test/typedclojure_lsp/schema/convert_test.clj` — unit tests for converter
- `test/typedclojure_lsp/lsp_test.clj` — unit tests for LSP handlers
- `test/typedclojure_lsp/integration_test.clj` — channel-pair integration tests
- `test/typedclojure_lsp/smoke_test.clj` — stdio subprocess smoke test
- `docs/architecture.md` — data flow documentation

### Modified Files
- `deps.edn` — add luposlip/json-schema dep + jitpack repo
- `mise.toml` — add update-lsp-schema and generate-lsp-json-schema tasks
- `src/typedclojure_lsp/lsp.clj` — add schema validation at boundaries, docstrings
- `src/typedclojure_lsp/main.clj` — add docstrings
- `src/typedclojure_lsp/runner.clj` — add docstrings
- `src/typedclojure_lsp/path.clj` — add docstrings
- `test/typedclojure_lsp/runner_test.clj` — expand with shape assertions

---

## Task 1: Fetch LSP MetaModel and Add Dependencies

**Files:**
- Modify: `deps.edn`
- Modify: `mise.toml`
- Create: `resources/typedclojure-lsp/lsp-meta-model.json`

- [ ] **Step 1: Add luposlip/json-schema dependency and jitpack repo to deps.edn**

In `deps.edn`, add to `:deps`:

```clojure
luposlip/json-schema {:mvn/version "0.4.7"}
```

And add the `:mvn/repos` key at the top level (after `:aliases`):

```clojure
:mvn/repos
{"jitpack.io" {:url "https://jitpack.io"}}
```

- [ ] **Step 2: Add mise tasks for schema management**

In `mise.toml`, add:

```toml
[tasks.update-lsp-schema]
description = "Fetch the latest LSP meta model from Microsoft"
run = "curl -s https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/metaModel/metaModel.json > resources/typedclojure-lsp/lsp-meta-model.json"

[tasks.generate-lsp-json-schema]
description = "Generate JSON Schema from LSP meta model"
raw = true
run = "clojure -M -m typedclojure-lsp.schema.convert"
```

- [ ] **Step 3: Create resources directory and fetch the metaModel**

```bash
mkdir -p resources/typedclojure-lsp
mise update-lsp-schema
```

Verify the file exists and contains the expected top-level keys (metaData, requests, notifications, structures, enumerations, typeAliases).

- [ ] **Step 4: Verify dependencies resolve**

```bash
clojure -Spath
```

Expected: completes without errors, classpath includes json-schema jar.

- [ ] **Step 5: Commit**

```bash
git add deps.edn mise.toml resources/typedclojure-lsp/lsp-meta-model.json
git commit -m "Add LSP metaModel and luposlip/json-schema dep"
```

---

## Task 2: MetaModel to JSON Schema Converter

**Files:**
- Create: `test/typedclojure_lsp/schema/convert_test.clj`
- Create: `src/typedclojure_lsp/schema/convert.clj`
- Create: `resources/typedclojure-lsp/lsp-json-schema.json`

The converter reads the metaModel.json and produces a JSON Schema draft-07 document with `definitions` for each LSP type we need and their transitive dependencies.

The metaModel uses a custom type system:
- `{"kind": "base", "name": "string"}` → JSON Schema `{"type": "string"}`
- `{"kind": "base", "name": "integer"}` / `"uinteger"` → `{"type": "integer"}`
- `{"kind": "base", "name": "boolean"}` → `{"type": "boolean"}`
- `{"kind": "base", "name": "null"}` → `{"type": "null"}`
- `{"kind": "base", "name": "DocumentUri"}` / `"URI"` → `{"type": "string"}`
- `{"kind": "reference", "name": "Foo"}` → `{"$ref": "#/definitions/Foo"}`
- `{"kind": "array", "element": ...}` → `{"type": "array", "items": <converted element>}`
- `{"kind": "or", "items": [...]}` → `{"oneOf": [<converted items>]}`
- `{"kind": "map", "key": ..., "value": ...}` → `{"type": "object", "additionalProperties": <converted value>}`
- `{"kind": "literal", "value": {"properties": [...]}}` → inline object schema
- `{"kind": "stringLiteral", "value": "foo"}` → `{"type": "string", "enum": ["foo"]}`
- Structures with `extends`/`mixins` → `allOf` combining the parent refs with own properties
- Enumerations → `{"type": "integer", "enum": [1, 2, 3, 4]}` (for integer enums)
- Properties with `"optional": true` are omitted from the `required` array

- [ ] **Step 1: Write failing test for basic type conversion**

Create `test/typedclojure_lsp/schema/convert_test.clj`:

```clojure
(ns typedclojure-lsp.schema.convert-test
  (:require [clojure.test :as t]
            [matcher-combinators.test]
            [typedclojure-lsp.schema.convert :as convert]))

(t/deftest convert-type-test
  (t/testing "base types"
    (t/is (= {"type" "string"} (convert/convert-type {"kind" "base" "name" "string"})))
    (t/is (= {"type" "integer"} (convert/convert-type {"kind" "base" "name" "integer"})))
    (t/is (= {"type" "integer"} (convert/convert-type {"kind" "base" "name" "uinteger"})))
    (t/is (= {"type" "boolean"} (convert/convert-type {"kind" "base" "name" "boolean"})))
    (t/is (= {"type" "null"} (convert/convert-type {"kind" "base" "name" "null"})))
    (t/is (= {"type" "string"} (convert/convert-type {"kind" "base" "name" "DocumentUri"})))
    (t/is (= {"type" "string"} (convert/convert-type {"kind" "base" "name" "URI"})))))

(t/deftest convert-reference-test
  (t/testing "reference types produce $ref"
    (t/is (= {"$ref" "#/definitions/Range"}
             (convert/convert-type {"kind" "reference" "name" "Range"})))))

(t/deftest convert-array-test
  (t/testing "array types"
    (t/is (= {"type" "array" "items" {"type" "string"}}
             (convert/convert-type {"kind" "array" "element" {"kind" "base" "name" "string"}})))))

(t/deftest convert-or-test
  (t/testing "union types produce oneOf"
    (t/is (= {"oneOf" [{"type" "string"} {"type" "null"}]}
             (convert/convert-type {"kind" "or"
                                    "items" [{"kind" "base" "name" "string"}
                                             {"kind" "base" "name" "null"}]})))))

(t/deftest convert-map-test
  (t/testing "map types"
    (t/is (= {"type" "object" "additionalProperties" {"type" "string"}}
             (convert/convert-type {"kind" "map"
                                    "key" {"kind" "base" "name" "string"}
                                    "value" {"kind" "base" "name" "string"}})))))

(t/deftest convert-literal-test
  (t/testing "literal/inline object types"
    (t/is (= {"type" "object"
              "properties" {"name" {"type" "string"}}
              "required" ["name"]}
             (convert/convert-type {"kind" "literal"
                                    "value" {"properties" [{"name" "name"
                                                            "type" {"kind" "base" "name" "string"}}]}})))))

(t/deftest convert-string-literal-test
  (t/testing "string literal types"
    (t/is (= {"type" "string" "enum" ["foo"]}
             (convert/convert-type {"kind" "stringLiteral" "value" "foo"})))))
```

- [ ] **Step 2: Run test to verify it fails**

```bash
clojure -M:test:dev --focus typedclojure-lsp.schema.convert-test
```

Expected: FAIL — namespace not found.

- [ ] **Step 3: Implement convert-type**

Create `src/typedclojure_lsp/schema/convert.clj`:

```clojure
(ns typedclojure-lsp.schema.convert
  "Converts LSP metaModel.json types into JSON Schema draft-07.

  The metaModel uses a custom type system with kinds like 'base', 'reference',
  'array', 'or', 'map', 'literal', and 'stringLiteral'. This namespace
  converts those into standard JSON Schema that can be validated with
  luposlip/json-schema."
  (:require [clojure.data.json :as json]
            [clojure.java.io :as io]))

(defn convert-type
  "Convert a metaModel type descriptor to a JSON Schema type descriptor."
  [type-desc]
  (case (get type-desc "kind")
    "base" (case (get type-desc "name")
             ("string" "DocumentUri" "URI") {"type" "string"}
             ("integer" "uinteger") {"type" "integer"}
             "decimal" {"type" "number"}
             "boolean" {"type" "boolean"}
             "null" {"type" "null"}
             ;; LSPAny - accept anything
             {"type" "string"})

    "reference" {"$ref" (str "#/definitions/" (get type-desc "name"))}

    "array" {"type" "array"
             "items" (convert-type (get type-desc "element"))}

    "or" {"oneOf" (mapv convert-type (get type-desc "items"))}

    "and" {"allOf" (mapv convert-type (get type-desc "items"))}

    "map" {"type" "object"
           "additionalProperties" (convert-type (get type-desc "value"))}

    "literal" (let [props (get-in type-desc ["value" "properties"])
                    required (vec (keep #(when-not (get % "optional") (get % "name")) props))]
                (cond-> {"type" "object"
                         "properties" (into {}
                                            (map (fn [p]
                                                   [(get p "name") (convert-type (get p "type"))]))
                                            props)}
                  (seq required) (assoc "required" required)))

    "stringLiteral" {"type" "string" "enum" [(get type-desc "value")]}

    "integerLiteral" {"type" "integer" "enum" [(get type-desc "value")]}

    "boolean" {"type" "boolean"}

    "tuple" {"type" "array"
             "items" (mapv convert-type (get type-desc "items"))
             "minItems" (count (get type-desc "items"))
             "maxItems" (count (get type-desc "items"))}

    ;; Fallback: accept anything
    {}))
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
clojure -M:test:dev --focus typedclojure-lsp.schema.convert-test
```

Expected: all PASS.

- [ ] **Step 5: Write failing test for structure conversion**

Add to `convert_test.clj`:

```clojure
(t/deftest convert-structure-test
  (t/testing "converts a simple structure to JSON Schema"
    (let [position-struct {"name" "Position"
                           "properties" [{"name" "line"
                                          "type" {"kind" "base" "name" "uinteger"}}
                                         {"name" "character"
                                          "type" {"kind" "base" "name" "uinteger"}}]}]
      (t/is (= {"type" "object"
                "properties" {"line" {"type" "integer"}
                              "character" {"type" "integer"}}
                "required" ["line" "character"]
                "additionalProperties" false}
               (convert/convert-structure position-struct)))))

  (t/testing "optional properties excluded from required"
    (let [save-opts {"name" "SaveOptions"
                     "properties" [{"name" "includeText"
                                    "type" {"kind" "base" "name" "boolean"}
                                    "optional" true}]}]
      (t/is (= {"type" "object"
                "properties" {"includeText" {"type" "boolean"}}
                "additionalProperties" false}
               (convert/convert-structure save-opts)))))

  (t/testing "structures with extends produce allOf"
    (let [struct {"name" "Child"
                  "extends" [{"kind" "reference" "name" "Parent"}]
                  "properties" [{"name" "extra"
                                 "type" {"kind" "base" "name" "string"}}]}]
      (t/is (= {"allOf" [{"$ref" "#/definitions/Parent"}
                          {"type" "object"
                           "properties" {"extra" {"type" "string"}}
                           "required" ["extra"]
                           "additionalProperties" false}]}
               (convert/convert-structure struct))))))

(t/deftest convert-enumeration-test
  (t/testing "converts integer enumerations"
    (let [enum {"name" "DiagnosticSeverity"
                "type" {"kind" "base" "name" "uinteger"}
                "values" [{"name" "Error" "value" 1}
                          {"name" "Warning" "value" 2}
                          {"name" "Information" "value" 3}
                          {"name" "Hint" "value" 4}]}]
      (t/is (= {"type" "integer" "enum" [1 2 3 4]}
               (convert/convert-enumeration enum)))))

  (t/testing "converts string enumerations"
    (let [enum {"name" "TraceValues"
                "type" {"kind" "base" "name" "string"}
                "values" [{"name" "Off" "value" "off"}
                          {"name" "Messages" "value" "messages"}
                          {"name" "Verbose" "value" "verbose"}]}]
      (t/is (= {"type" "string" "enum" ["off" "messages" "verbose"]}
               (convert/convert-enumeration enum))))))
```

- [ ] **Step 6: Run test to verify it fails**

```bash
clojure -M:test:dev --focus typedclojure-lsp.schema.convert-test/convert-structure-test
```

Expected: FAIL — `convert-structure` not found.

- [ ] **Step 7: Implement convert-structure and convert-enumeration**

Add to `convert.clj`:

```clojure
(defn convert-structure
  "Convert a metaModel structure to a JSON Schema object definition."
  [structure]
  (let [props (get structure "properties" [])
        required (vec (keep #(when-not (get % "optional") (get % "name")) props))
        own-schema (cond-> {"type" "object"
                            "properties" (into {}
                                               (map (fn [p]
                                                      [(get p "name") (convert-type (get p "type"))]))
                                               props)
                            "additionalProperties" false}
                     (seq required) (assoc "required" required))
        extends (get structure "extends" [])
        mixins (get structure "mixins" [])]
    (if (seq (concat extends mixins))
      {"allOf" (into (mapv convert-type (concat extends mixins))
                     [own-schema])}
      own-schema)))

(defn convert-enumeration
  "Convert a metaModel enumeration to a JSON Schema enum."
  [enumeration]
  (let [base-type (get-in enumeration ["type" "name"])
        json-type (case base-type
                    ("string") "string"
                    ("integer" "uinteger") "integer"
                    "string")]
    {"type" json-type
     "enum" (mapv #(get % "value") (get enumeration "values"))}))
```

- [ ] **Step 8: Run tests to verify they pass**

```bash
clojure -M:test:dev --focus typedclojure-lsp.schema.convert-test
```

Expected: all PASS.

- [ ] **Step 9: Write failing test for full schema generation**

Add to `convert_test.clj`:

```clojure
(t/deftest generate-json-schema-test
  (t/testing "generates a complete JSON Schema from the real metaModel"
    (let [schema (convert/generate-json-schema)]
      (t/is (= "http://json-schema.org/draft-07/schema#" (get schema "$schema")))
      (t/is (map? (get schema "definitions")))

      (t/testing "includes Position definition"
        (t/is (match?
               {"type" "object"
                "properties" {"line" {"type" "integer"}
                              "character" {"type" "integer"}}
                "required" ["line" "character"]}
               (get-in schema ["definitions" "Position"]))))

      (t/testing "includes Range definition"
        (t/is (match?
               {"type" "object"
                "properties" {"start" {"$ref" "#/definitions/Position"}
                              "end" {"$ref" "#/definitions/Position"}}}
               (get-in schema ["definitions" "Range"]))))

      (t/testing "includes Diagnostic definition"
        (t/is (match?
               {"type" "object"
                "properties" {"range" {"$ref" "#/definitions/Range"}
                              "message" {"type" "string"}}}
               (get-in schema ["definitions" "Diagnostic"]))))

      (t/testing "includes PublishDiagnosticsParams definition"
        (t/is (match?
               {"type" "object"
                "properties" {"uri" {"type" "string"}
                              "diagnostics" {"type" "array"
                                             "items" {"$ref" "#/definitions/Diagnostic"}}}}
               (get-in schema ["definitions" "PublishDiagnosticsParams"]))))

      (t/testing "includes InitializeResult definition"
        (t/is (some? (get-in schema ["definitions" "InitializeResult"]))))

      (t/testing "includes DiagnosticSeverity enumeration"
        (t/is (= {"type" "integer" "enum" [1 2 3 4]}
                 (get-in schema ["definitions" "DiagnosticSeverity"])))))))
```

- [ ] **Step 10: Run test to verify it fails**

```bash
clojure -M:test:dev --focus typedclojure-lsp.schema.convert-test/generate-json-schema-test
```

Expected: FAIL — `generate-json-schema` not found.

- [ ] **Step 11: Implement generate-json-schema and -main**

Add to `convert.clj`:

```clojure
(defn load-meta-model
  "Load the LSP metaModel.json from classpath resources."
  []
  (-> (io/resource "typedclojure-lsp/lsp-meta-model.json")
      slurp
      (json/read-str)))

(def ^:private root-types
  "The LSP types we need. Transitive dependencies are resolved automatically."
  #{"InitializeParams" "_InitializeParams" "WorkspaceFoldersInitializeParams"
    "InitializeResult" "ServerCapabilities"
    "TextDocumentSyncOptions" "SaveOptions"
    "PublishDiagnosticsParams" "Diagnostic" "DiagnosticSeverity"
    "DiagnosticTag" "DiagnosticRelatedInformation"
    "Range" "Position" "Location"
    "DidOpenTextDocumentParams" "TextDocumentItem"
    "DidSaveTextDocumentParams" "TextDocumentIdentifier"
    "CodeDescription"
    "ClientCapabilities" "TextDocumentSyncKind" "TraceValues"
    "WorkspaceFolder" "LSPAny"})

(defn- collect-references
  "Recursively collect all type names referenced by a type descriptor."
  [type-desc]
  (case (get type-desc "kind")
    "reference" #{(get type-desc "name")}
    "array" (collect-references (get type-desc "element"))
    "or" (into #{} (mapcat collect-references) (get type-desc "items"))
    "and" (into #{} (mapcat collect-references) (get type-desc "items"))
    "map" (into (collect-references (get type-desc "key"))
                (collect-references (get type-desc "value")))
    "literal" (into #{} (mapcat #(collect-references (get % "type")))
                    (get-in type-desc ["value" "properties"]))
    "tuple" (into #{} (mapcat collect-references) (get type-desc "items"))
    #{}))

(defn- resolve-transitive-deps
  "Starting from root-types, resolve all transitively referenced types."
  [structures enumerations]
  (let [struct-map (into {} (map (fn [s] [(get s "name") s])) structures)
        enum-map (into {} (map (fn [e] [(get e "name") e])) enumerations)]
    (loop [needed root-types
           resolved #{}]
      (let [new-names (clojure.set/difference needed resolved)]
        (if (empty? new-names)
          resolved
          (let [new-refs (into #{}
                               (mapcat
                                (fn [name]
                                  (if-let [s (get struct-map name)]
                                    (into (into #{} (mapcat #(collect-references (get % "type")))
                                                (get s "properties" []))
                                          (mapcat #(collect-references %)
                                                  (concat (get s "extends" [])
                                                          (get s "mixins" []))))
                                    ;; Enumerations don't reference other types
                                    #{})))
                               new-names)]
            (recur (into needed new-refs)
                   (into resolved new-names))))))))

(defn generate-json-schema
  "Generate a JSON Schema draft-07 document from the LSP metaModel.
  Only includes types listed in root-types and their transitive dependencies."
  []
  (let [meta-model (load-meta-model)
        structures (get meta-model "structures")
        enumerations (get meta-model "enumerations")
        struct-map (into {} (map (fn [s] [(get s "name") s])) structures)
        enum-map (into {} (map (fn [e] [(get e "name") e])) enumerations)
        needed (resolve-transitive-deps structures enumerations)
        definitions (merge
                     (into {}
                           (keep (fn [[name struct]]
                                   (when (contains? needed name)
                                     [name (convert-structure struct)])))
                           struct-map)
                     (into {}
                           (keep (fn [[name enum]]
                                   (when (contains? needed name)
                                     [name (convert-enumeration enum)])))
                           enum-map))]
    {"$schema" "http://json-schema.org/draft-07/schema#"
     "definitions" definitions}))

(defn -main
  "Generate the LSP JSON Schema and write it to resources."
  [& _args]
  (let [schema (generate-json-schema)
        output-path "resources/typedclojure-lsp/lsp-json-schema.json"]
    (spit output-path (json/write-str schema :indent true))
    (println "Wrote LSP JSON Schema to" output-path)
    (println (count (get schema "definitions")) "definitions generated")))
```

Note: this namespace requires `clojure.data.json`. Add to `deps.edn` under `:deps`:

```clojure
org.clojure/data.json {:mvn/version "2.5.1"}
```

Also add `clojure.set` to the `:require` in the ns form:

```clojure
(ns typedclojure-lsp.schema.convert
  (:require [clojure.data.json :as json]
            [clojure.java.io :as io]
            [clojure.set]))
```

- [ ] **Step 12: Run tests to verify they pass**

```bash
clojure -M:test:dev --focus typedclojure-lsp.schema.convert-test
```

Expected: all PASS.

- [ ] **Step 13: Generate the JSON Schema file**

```bash
mise generate-lsp-json-schema
```

Expected: prints the output path and definition count. Verify the file exists and is valid JSON.

- [ ] **Step 14: Commit**

```bash
git add src/typedclojure_lsp/schema/convert.clj test/typedclojure_lsp/schema/convert_test.clj deps.edn resources/typedclojure-lsp/lsp-json-schema.json
git commit -m "Add LSP metaModel to JSON Schema converter"
```

---

## Task 3: Schema Validation Namespace

**Files:**
- Create: `test/typedclojure_lsp/schema_test.clj`
- Create: `src/typedclojure_lsp/schema.clj`

This namespace mirrors clojure-dap's `schema.clj`: a mutable malli registry, `define!`/`validate` helpers, and `lsp-json-schema->malli` that creates malli `:fn` validators backed by `luposlip/json-schema`.

- [ ] **Step 1: Write failing test for define! and validate**

Create `test/typedclojure_lsp/schema_test.clj`:

```clojure
(ns typedclojure-lsp.schema-test
  (:require [clojure.test :as t]
            [matcher-combinators.test]
            [typedclojure-lsp.schema :as schema]))

(t/deftest define-and-validate-test
  (t/testing "define a schema and validate against it"
    (schema/define! ::color :string)
    (t/is (nil? (schema/validate ::color "red")))
    (t/is (some? (schema/validate ::color 42))))

  (t/testing "schemas can reference other schemas"
    (schema/define! ::name :string)
    (schema/define! ::person [:map [:name ::name] [:age pos-int?]])
    (t/is (nil? (schema/validate ::person {:name "Alice" :age 30})))
    (t/is (some? (schema/validate ::person {:name 42 :age 30}))))

  (t/testing "validate returns error details on failure"
    (t/is (match?
           {:message #"Failed to validate"
            :humanized some?}
           (schema/validate ::color 42))))

  (t/testing "unknown schema returns error"
    (t/is (match?
           {:message #"Unknown schema"}
           (schema/validate ::nonexistent "hello")))))
```

- [ ] **Step 2: Run test to verify it fails**

```bash
clojure -M:test:dev --focus typedclojure-lsp.schema-test
```

Expected: FAIL — namespace not found.

- [ ] **Step 3: Implement schema namespace**

Create `src/typedclojure_lsp/schema.clj`:

```clojure
(ns typedclojure-lsp.schema
  "Schema registration and validation for LSP types.

  Provides a mutable malli registry, define!/validate helpers,
  and lsp-json-schema->malli for bridging JSON Schema definitions
  into malli validators via luposlip/json-schema."
  (:require [clojure.string :as str]
            [malli.core :as m]
            [malli.error :as me]
            [malli.util :as mu]
            [malli.registry :as mr]
            [json-schema.core :as json-schema]
            [taoensso.telemere :as te]))

(defonce schemas! (atom (merge (m/default-schemas) (mu/schemas))))
(mr/set-default-registry! (mr/mutable-registry schemas!))

(defonce explainers! (atom {}))

(defn define!
  "Register a schema in the global malli registry. Returns the id."
  [id schema]
  (swap! schemas! assoc id schema)
  (reset! explainers! {})
  id)

(defn- upsert-explainer!
  "Return a cached explainer for the schema id, compiling if needed."
  [id]
  (if-let [schema (get @schemas! id)]
    (or (get @explainers! id)
        (let [explainer (m/explainer schema)]
          (swap! explainers! assoc id explainer)
          explainer))
    nil))

(defn validate
  "Validate a value against a registered schema.
  Returns nil on success, a map with :message and :humanized on failure."
  [id value]
  (if-let [explainer (upsert-explainer! id)]
    (when-let [explanation (explainer value)]
      {:message (str "Failed to validate against schema " id)
       :explanation explanation
       :humanized (me/humanize explanation)})
    {:message (str "Unknown schema: " id)}))

(defn lsp-json-schema->malli
  "Create a malli :fn schema that validates against a definition in the
  LSP JSON Schema resource using luposlip/json-schema.

  definition-key is a keyword like :PublishDiagnosticsParams that maps
  to a key in the JSON Schema's definitions object."
  [definition-key]
  (let [prepared-schema
        (json-schema/prepare-schema
         {:$schema "http://json-schema.org/draft-07/schema"
          :id (str "typedclojure-lsp.schema/" (name definition-key))
          :$ref (str "classpath://typedclojure-lsp/lsp-json-schema.json#/definitions/" (name definition-key))}
         {:classpath-aware? true})]

    [:fn
     {:error/fn
      (fn [{:keys [_schema value]} _]
        (try
          (json-schema/validate prepared-schema value)
          nil
          (catch clojure.lang.ExceptionInfo e
            (let [cause (.getMessage e)
                  {:keys [errors]} (ex-data e)]
              (str cause " " (str/join ", " errors))))))}

     (fn [x]
       (try
         (json-schema/validate prepared-schema x)
         true
         (catch clojure.lang.ExceptionInfo _e
           false)))]))
```

- [ ] **Step 4: Run tests to verify they pass**

```bash
clojure -M:test:dev --focus typedclojure-lsp.schema-test
```

Expected: all PASS.

- [ ] **Step 5: Write failing test for LSP JSON Schema validation**

Add to `schema_test.clj`:

```clojure
(t/deftest lsp-json-schema-validation-test
  (t/testing "validates a correct PublishDiagnosticsParams"
    (schema/define! ::publish-diagnostics
      (schema/lsp-json-schema->malli :PublishDiagnosticsParams))

    (t/is (nil? (schema/validate ::publish-diagnostics
                                 {"uri" "file:///foo.clj"
                                  "diagnostics" []}))))

  (t/testing "validates PublishDiagnosticsParams with diagnostics"
    (t/is (nil? (schema/validate ::publish-diagnostics
                                 {"uri" "file:///foo.clj"
                                  "diagnostics" [{"range" {"start" {"line" 0 "character" 0}
                                                           "end" {"line" 0 "character" 5}}
                                                  "message" "type error"}]}))))

  (t/testing "rejects malformed PublishDiagnosticsParams"
    (t/is (some? (schema/validate ::publish-diagnostics
                                  {"diagnostics" []}))))

  (t/testing "validates InitializeResult"
    (schema/define! ::initialize-result
      (schema/lsp-json-schema->malli :InitializeResult))

    (t/is (nil? (schema/validate ::initialize-result
                                 {"capabilities" {}
                                  "serverInfo" {"name" "typedclojure"}}))))

  (t/testing "validates Position"
    (schema/define! ::position
      (schema/lsp-json-schema->malli :Position))

    (t/is (nil? (schema/validate ::position {"line" 0 "character" 5})))
    (t/is (some? (schema/validate ::position {"line" 0})))))
```

- [ ] **Step 6: Run tests to verify they pass**

```bash
clojure -M:test:dev --focus typedclojure-lsp.schema-test
```

Expected: all PASS. If any fail, it means the generated JSON Schema has issues — fix in the converter and regenerate.

- [ ] **Step 7: Commit**

```bash
git add src/typedclojure_lsp/schema.clj test/typedclojure_lsp/schema_test.clj
git commit -m "Add schema validation namespace with LSP JSON Schema bridge"
```

---

## Task 4: Wire Schema Validation into LSP Handlers

**Files:**
- Modify: `src/typedclojure_lsp/lsp.clj`

- [ ] **Step 1: Define LSP schemas on namespace load**

Add schema requires and definitions to `lsp.clj`. Add to the `ns` require:

```clojure
[typedclojure-lsp.schema :as schema]
```

After the ns form, add schema definitions:

```clojure
(schema/define! ::initialize-result
  (schema/lsp-json-schema->malli :InitializeResult))

(schema/define! ::publish-diagnostics-params
  (schema/lsp-json-schema->malli :PublishDiagnosticsParams))
```

- [ ] **Step 2: Add validation helper**

Add a helper that validates and logs warnings:

```clojure
(defn- validate-outgoing!
  "Validate an outgoing LSP message against its schema.
  Logs a warning if validation fails but does not throw."
  [schema-id message]
  (when-let [error (schema/validate schema-id message)]
    (te/log!
     {:level :warn
      :data {:schema-id schema-id
             :error error
             :message message}}
     "Outgoing LSP message failed schema validation")))
```

- [ ] **Step 3: Add validation to initialize response**

In the `server/receive-request "initialize"` method, validate the response before returning it:

```clojure
(defmethod server/receive-request "initialize"
  [_ context params]
  (te/log!
   {:level :info
    :data {:context (loggable-context context)
           :params params}}
   "initialize")
  (reset! (:root-uri! context) (:root-path params))
  (let [result {:capabilities {:textDocumentSync {:openClose true
                                                  :save {:includeText false}}}
                :serverInfo {:name "typedclojure"}}]
    (validate-outgoing! ::initialize-result result)
    result))
```

- [ ] **Step 4: Add validation to publishDiagnostics notifications**

In `type-check-and-notify!`, validate each diagnostics notification. Wrap the `server/send-notification` calls to validate the params:

```clojure
(defn- send-diagnostics!
  "Send a publishDiagnostics notification, validating the params first."
  [server params]
  (validate-outgoing! ::publish-diagnostics-params params)
  (server/send-notification server "textDocument/publishDiagnostics" params))
```

Then replace the two `server/send-notification` calls in `type-check-and-notify!` with `send-diagnostics!`:

For clearing diagnostics:
```clojure
(run!
 (fn [path]
   (send-diagnostics! server {:uri path :diagnostics []}))
 @files-with-diagnostics!)
```

For publishing type errors:
```clojure
(send-diagnostics!
 server
 {:uri file-path
  :diagnostics
  (map ...)})
```

- [ ] **Step 5: Add incoming validation to handlers**

Add a schema definition for incoming params:

```clojure
(schema/define! ::initialize-params
  (schema/lsp-json-schema->malli :InitializeParams))
```

Add a validation helper for incoming messages:

```clojure
(defn- validate-incoming!
  "Validate an incoming LSP message against its schema.
  Logs a warning if validation fails but does not throw."
  [schema-id message]
  (when-let [error (schema/validate schema-id message)]
    (te/log!
     {:level :warn
      :data {:schema-id schema-id
             :error error
             :message message}}
     "Incoming LSP message failed schema validation")))
```

Add `(validate-incoming! ::initialize-params params)` as the first line in the initialize handler body (after the log call).

Note: we only validate initialize params for now since didOpen/didSave params from lsp4clj are already parsed and the schemas for those are less critical. We can expand later.

- [ ] **Step 6: Run existing tests to verify nothing is broken**

```bash
clojure -M:test:dev
```

Expected: all existing tests pass.

- [ ] **Step 7: Commit**

```bash
git add src/typedclojure_lsp/lsp.clj
git commit -m "Wire LSP schema validation into handlers"
```

---

## Task 5: Path Unit Tests

**Files:**
- Create: `test/typedclojure_lsp/path_test.clj`

- [ ] **Step 1: Write tests**

Create `test/typedclojure_lsp/path_test.clj`:

```clojure
(ns typedclojure-lsp.path-test
  (:require [clojure.test :as t]
            [typedclojure-lsp.path :as path]))

(t/deftest classpath-dirs-test
  (t/testing "returns a seq of strings"
    (let [dirs (path/classpath-dirs)]
      (t/is (seq dirs) "classpath-dirs should not be empty")
      (t/is (every? string? dirs) "every entry should be a string")))

  (t/testing "includes expected source paths from this project"
    (let [dirs (path/classpath-dirs)
          has-suffix? (fn [suffix] (some #(clojure.string/ends-with? % suffix) dirs))]
      (t/is (has-suffix? "/src") "should include src directory")
      (t/is (has-suffix? "/test") "should include test directory"))))

(t/deftest current-directory-test
  (t/testing "returns a non-empty string"
    (let [dir (path/current-directory)]
      (t/is (string? dir))
      (t/is (pos? (count dir)))))

  (t/testing "returned path exists as a directory"
    (let [dir (path/current-directory)
          f (java.io.File. dir)]
      (t/is (.exists f))
      (t/is (.isDirectory f)))))
```

- [ ] **Step 2: Run tests**

```bash
clojure -M:test:dev --focus typedclojure-lsp.path-test
```

Expected: all PASS.

- [ ] **Step 3: Commit**

```bash
git add test/typedclojure_lsp/path_test.clj
git commit -m "Add path namespace unit tests"
```

---

## Task 6: Expand Runner Tests

**Files:**
- Modify: `test/typedclojure_lsp/runner_test.clj`

- [ ] **Step 1: Add shape assertion for TypeError data**

Add to the existing `check-dirs` test in `runner_test.clj`, a new testing block:

```clojure
(t/testing "type error data has the expected shape for LSP consumption"
  (let [{:keys [type-errors]} (runner/check-dirs ["dev/examples"])]
    (t/is (seq type-errors) "should have at least one error")
    (doseq [error type-errors]
      (t/is (string? (:message error)) "error should have a string message")
      (t/is (some? (:form error)) "error should have a form")
      (t/is (keyword? (:type-error error)) "error should have a keyword type-error")
      (t/is (map? (:env error)) "error should have an env map")
      (let [{:keys [line column file]} (:env error)]
        (t/is (number? line) "env should have a numeric line")
        (t/is (number? column) "env should have a numeric column")
        (t/is (string? file) "env should have a string file")))))
```

- [ ] **Step 2: Run tests**

```bash
clojure -M:test:dev --focus typedclojure-lsp.runner-test
```

Expected: all PASS.

- [ ] **Step 3: Commit**

```bash
git add test/typedclojure_lsp/runner_test.clj
git commit -m "Add TypeError shape assertions to runner tests"
```

---

## Task 7: LSP Handler Unit Tests

**Files:**
- Create: `test/typedclojure_lsp/lsp_test.clj`

These tests mock `runner/check-dirs` and `server/send-notification` using spy to test handler logic in isolation.

- [ ] **Step 1: Write failing test for initialize handler**

Create `test/typedclojure_lsp/lsp_test.clj`:

```clojure
(ns typedclojure-lsp.lsp-test
  (:require [clojure.test :as t]
            [matcher-combinators.test]
            [spy.core :as spy]
            [lsp4clj.server :as server]
            [typedclojure-lsp.lsp :as lsp]))

(defn- make-context
  "Create a test context with fresh atoms and a nil server."
  []
  {:server nil
   :files-with-diagnostics! (atom #{})
   :root-uri! (atom nil)})

(t/deftest initialize-test
  (t/testing "returns capabilities and sets root-uri"
    (let [context (make-context)
          result (server/receive-request "initialize" context {:root-path "/home/user/project"})]
      (t/is (match?
             {:capabilities {:textDocumentSync {:openClose true
                                                :save {:includeText false}}}
              :serverInfo {:name "typedclojure"}}
             result))
      (t/is (= "/home/user/project" @(:root-uri! context))))))
```

- [ ] **Step 2: Run test to verify it passes**

```bash
clojure -M:test:dev --focus typedclojure-lsp.lsp-test/initialize-test
```

Expected: PASS — the initialize handler is a pure function given the right context.

- [ ] **Step 3: Write test for type-check-and-notify! with errors**

Add to `lsp_test.clj`:

```clojure
(t/deftest type-check-and-notify-test
  (t/testing "publishes diagnostics for type errors"
    (let [send-calls (atom [])
          context {:server :test-server
                   :files-with-diagnostics! (atom #{})
                   :root-uri! (atom (System/getProperty "user.dir"))}]
      (with-redefs [server/send-notification (fn [_server method params]
                                               (swap! send-calls conj {:method method :params params}))
                    typedclojure-lsp.runner/check-dirs
                    (fn [_dirs]
                      {:result :type-errors
                       :type-errors [{:message "Type mismatch"
                                      :form '(add :foo 10)
                                      :type-error :clojure.core.typed.errors/type-error
                                      :env {:line 11
                                            :column 3
                                            :file "file:///project/src/core.clj"}}]})
                    typedclojure-lsp.path/classpath-dirs
                    (fn [] [(System/getProperty "user.dir")])]

        (lsp/type-check-and-notify! context)

        (t/testing "sends publishDiagnostics notification"
          (let [diag-calls (filter #(= "textDocument/publishDiagnostics" (:method %)) @send-calls)]
            (t/is (pos? (count diag-calls)) "should send at least one diagnostics notification")

            (t/testing "diagnostic has correct shape"
              (t/is (match?
                     {:method "textDocument/publishDiagnostics"
                      :params {:uri "file:///project/src/core.clj"
                               :diagnostics [{:source "typedclojure"
                                              :message "Type mismatch"
                                              :range {:start {:line 11 :character 3}
                                                      :end {:line number? :character number?}}}]}}
                     (last diag-calls)))))))))

  (t/testing "clears previous diagnostics before publishing new ones"
    (let [send-calls (atom [])
          previous-files (atom #{"file:///old-file.clj"})
          context {:server :test-server
                   :files-with-diagnostics! previous-files
                   :root-uri! (atom (System/getProperty "user.dir"))}]
      (with-redefs [server/send-notification (fn [_server method params]
                                               (swap! send-calls conj {:method method :params params}))
                    typedclojure-lsp.runner/check-dirs (fn [_dirs] {:result :ok})
                    typedclojure-lsp.path/classpath-dirs (fn [] [(System/getProperty "user.dir")])]

        (lsp/type-check-and-notify! context)

        (t/testing "clears diagnostics for previously errored file"
          (t/is (match?
                 [{:method "textDocument/publishDiagnostics"
                   :params {:uri "file:///old-file.clj"
                            :diagnostics []}}]
                 @send-calls)))))))
```

- [ ] **Step 4: Run tests**

```bash
clojure -M:test:dev --focus typedclojure-lsp.lsp-test
```

Expected: all PASS. If the `type-check-and-notify!` function is private or not directly callable, adjust to use `#'lsp/type-check-and-notify!`.

- [ ] **Step 5: Commit**

```bash
git add test/typedclojure_lsp/lsp_test.clj
git commit -m "Add LSP handler unit tests"
```

---

## Task 8: Channel-Pair Integration Tests

**Files:**
- Create: `test/typedclojure_lsp/integration_test.clj`

These tests start a real lsp4clj server over core.async channels and send/receive LSP messages through it. They exercise the full stack: lsp handlers -> runner -> typed.clojure -> diagnostics.

lsp4clj's `chan-server` accepts `:input-ch` and `:output-ch` channels. Messages on these channels are plain Clojure maps (not JSON strings), using kebab-case keys: `{:jsonrpc "2.0" :id 1 :method "initialize" :params {...}}`.

- [ ] **Step 1: Write integration test with helper**

Create `test/typedclojure_lsp/integration_test.clj`:

```clojure
(ns typedclojure-lsp.integration-test
  (:require [clojure.test :as t]
            [clojure.core.async :as a]
            [matcher-combinators.test]
            [lsp4clj.server :as server]
            [taoensso.telemere :as te]
            [typedclojure-lsp.schema :as schema]))

(defn- read-with-timeout
  "Read from a channel with a timeout. Returns ::timeout if nothing arrives."
  ([ch] (read-with-timeout ch 30000))
  ([ch timeout-ms]
   (let [[val _] (a/alts!! [ch (a/timeout timeout-ms)])]
     (or val ::timeout))))

(defn- collect-notifications
  "Read all available notifications from the output channel until timeout."
  ([ch] (collect-notifications ch 15000))
  ([ch timeout-ms]
   (loop [results []]
     (let [val (read-with-timeout ch timeout-ms)]
       (if (= val ::timeout)
         results
         (recur (conj results val)))))))

(defn- with-lsp-server
  "Start an LSP server over channel pairs, execute f with helpers, then shut down.
  f receives a map with:
    :send!    - fn to put a message on the input channel
    :read!    - fn to read one message from output (with timeout)
    :collect! - fn to collect all available output messages
    :server   - the raw server object"
  [f]
  (let [input-ch (a/chan 100)
        output-ch (a/chan 100)
        srv (server/chan-server {:input-ch input-ch :output-ch output-ch})
        context {:server srv
                 :files-with-diagnostics! (atom #{})
                 :root-uri! (atom nil)}
        start! (server/start srv context)]
    (try
      (f {:send! (fn [msg]
                   (te/log! {:level :info :data {:direction :outgoing :msg msg}}
                            "Integration test -> server")
                   (a/>!! input-ch msg))
          :read! (fn
                   ([] (read-with-timeout output-ch))
                   ([timeout] (read-with-timeout output-ch timeout)))
          :collect! (fn
                      ([] (collect-notifications output-ch))
                      ([timeout] (collect-notifications output-ch timeout)))
          :server srv})
      (finally
        (server/shutdown srv)
        (a/close! input-ch)))))

(defn- initialize!
  "Send initialize + initialized handshake, return the initialize response."
  [{:keys [send! read!] :as helpers}]
  (send! {:jsonrpc "2.0"
          :id 1
          :method "initialize"
          :params {:root-path (System/getProperty "user.dir")}})
  (let [response (read!)]
    (te/log! {:level :info :data {:response response}} "Initialize response")
    (send! {:jsonrpc "2.0"
            :method "initialized"
            :params {}})
    response))

(t/deftest full-lifecycle-test
  (t/testing "initialize -> initialized -> receive diagnostics for known type errors"
    (with-lsp-server
      (fn [{:keys [collect!] :as helpers}]
        (let [response (initialize! helpers)]
          (t/testing "initialize response has expected capabilities"
            (t/is (match?
                   {:result {:capabilities {:text-document-sync {:open-close true
                                                                 :save {:include-text false}}}
                             :server-info {:name "typedclojure"}}}
                   response)))

          (t/testing "receives publishDiagnostics with type errors from dev/examples"
            (let [notifications (collect!)
                  diag-notifications (filter #(= (:method %) "textDocument/publishDiagnostics")
                                            notifications)]
              (te/log! {:level :info :data {:notifications notifications}} "All notifications after initialized")
              ;; dev/examples/core.clj has a known type error in a-bad-fn
              (t/is (pos? (count diag-notifications))
                    "should receive at least one diagnostics notification")
              (t/is (match?
                     [{:method "textDocument/publishDiagnostics"
                       :params {:uri #"examples/core\.clj"
                                :diagnostics [{:source "typedclojure"
                                               :message #"Function add could not be applied"
                                               :range {:start {:line number? :character number?}
                                                       :end {:line number? :character number?}}}]}}]
                     (filter #(seq (get-in % [:params :diagnostics])) diag-notifications))))))))))

(t/deftest did-save-triggers-recheck-test
  (t/testing "textDocument/didSave triggers type checking"
    (with-lsp-server
      (fn [{:keys [send! collect!] :as helpers}]
        (initialize! helpers)
        ;; Consume initial notifications
        (collect!)

        ;; Send didSave
        (send! {:jsonrpc "2.0"
                :method "textDocument/didSave"
                :params {:text-document {:uri "file:///dev/examples/core.clj"}}})

        (let [notifications (collect!)
              diag-notifications (filter #(= (:method %) "textDocument/publishDiagnostics")
                                        notifications)]
          (te/log! {:level :info :data {:notifications notifications}} "Notifications after didSave")
          (t/is (seq diag-notifications)
                "should receive diagnostics after didSave"))))))

(t/deftest did-open-triggers-recheck-test
  (t/testing "textDocument/didOpen triggers type checking"
    (with-lsp-server
      (fn [{:keys [send! collect!] :as helpers}]
        (initialize! helpers)
        (collect!)

        (send! {:jsonrpc "2.0"
                :method "textDocument/didOpen"
                :params {:text-document {:uri "file:///dev/examples/core.clj"
                                         :language-id "clojure"
                                         :version 1
                                         :text ""}}})

        (let [notifications (collect!)
              diag-notifications (filter #(= (:method %) "textDocument/publishDiagnostics")
                                        notifications)]
          (te/log! {:level :info :data {:notifications notifications}} "Notifications after didOpen")
          (t/is (seq diag-notifications)
                "should receive diagnostics after didOpen"))))))

(t/deftest clean-code-empty-diagnostics-test
  (t/testing "valid typed code produces empty diagnostics"
    (with-lsp-server
      (fn [{:keys [send! collect!] :as helpers}]
        ;; Point root at src/ which contains valid typed code
        (send! {:jsonrpc "2.0"
                :id 1
                :method "initialize"
                :params {:root-path (str (System/getProperty "user.dir") "/src")}})
        (let [_response (let [{:keys [read!]} helpers] (read!))]
          (send! {:jsonrpc "2.0"
                  :method "initialized"
                  :params {}})

          (let [notifications (collect!)
                diag-with-errors (filter #(and (= (:method %) "textDocument/publishDiagnostics")
                                               (seq (get-in % [:params :diagnostics])))
                                         notifications)]
            (te/log! {:level :info :data {:notifications notifications}} "Notifications for clean code")
            (t/is (empty? diag-with-errors)
                  "valid typed code should not produce diagnostics with errors")))))))

(t/deftest stale-diagnostics-cleared-test
  (t/testing "diagnostics from a previous check are cleared on recheck"
    (with-lsp-server
      (fn [{:keys [send! collect!] :as helpers}]
        ;; Initialize with the project root (dev/examples has errors)
        (initialize! helpers)

        ;; First check produces diagnostics for dev/examples/core.clj
        (let [initial-notifications (collect!)
              initial-diags (filter #(and (= (:method %) "textDocument/publishDiagnostics")
                                          (seq (get-in % [:params :diagnostics])))
                                    initial-notifications)]
          (te/log! {:level :info :data {:initial-diags initial-diags}} "Initial diagnostics")
          (t/is (seq initial-diags) "should have diagnostics from first check")

          ;; Trigger recheck via didSave
          (send! {:jsonrpc "2.0"
                  :method "textDocument/didSave"
                  :params {:text-document {:uri "file:///dev/examples/core.clj"}}})

          ;; Second check should clear previously errored files first (empty diagnostics)
          ;; then re-publish the current errors
          (let [recheck-notifications (collect!)
                clearing-notifications (filter #(and (= (:method %) "textDocument/publishDiagnostics")
                                                      (empty? (get-in % [:params :diagnostics])))
                                               recheck-notifications)]
            (te/log! {:level :info :data {:recheck recheck-notifications}} "Recheck notifications")
            (t/is (seq clearing-notifications)
                  "should send empty diagnostics to clear stale errors before republishing")))))))
```

- [ ] **Step 2: Run integration tests**

```bash
clojure -M:test:dev --focus typedclojure-lsp.integration-test
```

Expected: all PASS. These tests run the real type checker so they will be slower (10-30s). The output will show the full LSP message flow via telemere logging if a test fails.

Note: the exact key format (kebab-case vs camelCase) in responses from lsp4clj's chan-server may differ from what's shown above. If tests fail due to key format, adjust the matchers. For example, lsp4clj may return `:text-document-sync` or `:textDocumentSync`. Check the actual output logged by telemere and adjust the matchers accordingly.

- [ ] **Step 3: Commit**

```bash
git add test/typedclojure_lsp/integration_test.clj
git commit -m "Add channel-pair integration tests"
```

---

## Task 9: Stdio Smoke Test

**Files:**
- Create: `test/typedclojure_lsp/smoke_test.clj`

This test spawns the actual JVM process, sends an LSP initialize request over stdin, and reads the response from stdout. It tests the real entry point, stdio wiring, and that the process can start and respond.

- [ ] **Step 1: Write the smoke test**

Create `test/typedclojure_lsp/smoke_test.clj`:

```clojure
(ns typedclojure-lsp.smoke-test
  (:require [clojure.test :as t]
            [clojure.data.json :as json]
            [taoensso.telemere :as te])
  (:import [java.io OutputStreamWriter BufferedReader InputStreamReader]))

(defn- encode-lsp-message
  "Encode a Clojure map as an LSP JSON-RPC message with Content-Length header."
  [msg]
  (let [body (json/write-str msg)
        length (count (.getBytes body "UTF-8"))]
    (str "Content-Length: " length "\r\n\r\n" body)))

(defn- read-lsp-message
  "Read one LSP message from a BufferedReader. Returns parsed JSON or nil on timeout."
  [^BufferedReader reader timeout-ms]
  (let [deadline (+ (System/currentTimeMillis) timeout-ms)]
    (loop []
      (if (> (System/currentTimeMillis) deadline)
        nil
        (if (.ready reader)
          (let [line (.readLine reader)]
            (te/log! {:level :info :data {:line line}} "Smoke test read line")
            (if (and line (.startsWith line "Content-Length:"))
              (let [length (parse-long (clojure.string/trim (subs line (count "Content-Length:"))))
                    _ (.readLine reader) ;; empty line separator
                    buf (char-array length)
                    _ (.read reader buf 0 length)
                    body (String. buf)]
                (te/log! {:level :info :data {:body body}} "Smoke test read body")
                (json/read-str body :key-fn keyword))
              (recur)))
          (do
            (Thread/sleep 100)
            (recur)))))))

(t/deftest stdio-smoke-test
  (t/testing "server starts over stdio, responds to initialize, and can be shut down"
    (let [proc (.start (ProcessBuilder.
                        ["clojure" "-M" "-m" "typedclojure-lsp.main"]
                        ))
          stdin (OutputStreamWriter. (.getOutputStream proc) "UTF-8")
          stdout (BufferedReader. (InputStreamReader. (.getInputStream proc) "UTF-8"))]
      (try
        ;; Send initialize request
        (let [init-msg (encode-lsp-message
                        {:jsonrpc "2.0"
                         :id 1
                         :method "initialize"
                         :params {:processId nil
                                  :rootPath (System/getProperty "user.dir")
                                  :capabilities {}}})]
          (te/log! {:level :info :data {:message init-msg}} "Smoke test sending initialize")
          (.write stdin init-msg)
          (.flush stdin))

        ;; Read response
        (let [response (read-lsp-message stdout 60000)]
          (te/log! {:level :info :data {:response response}} "Smoke test initialize response")
          (t/is (some? response) "should receive a response within 60s")
          (when response
            (t/is (= 1 (:id response)) "response id should match request id")
            (t/is (some? (get-in response [:result :capabilities]))
                  "response should contain capabilities")))

        (finally
          (.destroyForcibly proc)
          (.waitFor proc 5 java.util.concurrent.TimeUnit/SECONDS))))))
```

- [ ] **Step 2: Run the smoke test**

```bash
clojure -M:test:dev --focus typedclojure-lsp.smoke-test
```

Expected: PASS. This test is slow (JVM startup) — expect 15-30s. If it times out, increase the timeout or check that `clojure -M -m typedclojure-lsp.main` starts correctly.

- [ ] **Step 3: Commit**

```bash
git add test/typedclojure_lsp/smoke_test.clj
git commit -m "Add stdio smoke test"
```

---

## Task 10: Architecture Documentation

**Files:**
- Create: `docs/architecture.md`

- [ ] **Step 1: Write the architecture doc**

Create `docs/architecture.md`:

```markdown
# Architecture

How typedclojure-lsp works: the LSP protocol on one side, Typed Clojure on the other, and how we sit in the middle.

## LSP Protocol Flow

The editor communicates with us over stdio using JSON-RPC 2.0 (the Language Server Protocol).

```
Editor                          typedclojure-lsp
  |                                    |
  |-- initialize (rootPath) ---------> |
  |<-------- InitializeResult ---------|  (capabilities, serverInfo)
  |                                    |
  |-- initialized ------------------> |
  |                                    |  [runs type checker]
  |<-- textDocument/publishDiagnostics |  (errors for each file)
  |                                    |
  |-- textDocument/didOpen ----------> |
  |                                    |  [runs type checker]
  |<-- textDocument/publishDiagnostics |
  |                                    |
  |-- textDocument/didSave ----------> |
  |                                    |  [runs type checker]
  |<-- textDocument/publishDiagnostics |
```

**initialize**: The editor sends its root path. We respond with our capabilities: we support open/close notifications and save notifications (without text content). We store the root path to scope type checking.

**initialized / didOpen / didSave**: Each of these triggers a full type check. We check all classpath directories that fall under the project root, then push diagnostics for any files with errors. We also clear diagnostics for files that previously had errors but no longer do.

**publishDiagnostics**: We send one notification per file that has type errors. Each diagnostic includes the error message, source position (line/column), and a range spanning the offending form.

## Typed Clojure Integration

Typed Clojure checks namespaces that have `^:typed.clojure` metadata on their `ns` form. We invoke it via `typed.clojure/check-dir-clj`, passing directory paths.

```
runner/check-dirs
  |
  |--> typed.clojure/check-dir-clj [dirs]
  |      |
  |      |--> succeeds: {:result :ok}
  |      |
  |      |--> throws ExceptionInfo with {:errors [ExceptionInfo ...]}
  |             |
  |             |--> ExceptionInfo->type-errors
  |                    |
  |                    |--> for each error:
  |                           (ex-data error) => {:env {:line :column :file}
  |                                               :form <the bad form>
  |                                               :type-error <keyword>}
  |                           (ex-message error) => "Function add could not..."
  |                    |
  |                    |--> [{:message str
  |                            :form any
  |                            :type-error keyword
  |                            :env {:line int :column int :file str}}]
  |
  |--> {:result :type-errors, :type-errors [<above>]}
  |
  |--> or {:result :exception, :exception <Throwable>}
```

**Directory resolution**: We don't pass the project root directly to Typed Clojure (it doesn't work that way). Instead, `path/classpath-dirs` returns all directories on the JVM classpath, and we filter to those under the project root using `str/starts-with?`.

## How We Sit In The Middle

```
Editor <--stdio--> main <--lsp4clj--> lsp --> runner --> typed.clojure
                                       |
                                       +--> path (classpath resolution)
                                       |
                                       +--> schema (LSP message validation)
```

**main** — Entry point. Configures telemere logging to stderr (removes default console handler, adds stderr handler). Sets up uncaught exception handler. Starts the lsp4clj stdio server and blocks until shutdown.

**lsp** — Protocol handlers. Implements `server/receive-request` for "initialize" and `server/receive-notification` for "initialized", "textDocument/didOpen", "textDocument/didSave". Orchestrates type checking via `type-check-and-notify!` which calls runner, groups errors by file, and pushes diagnostics. Tracks files-with-diagnostics in an atom to clear stale diagnostics on recheck. Validates outgoing messages against LSP JSON Schema.

**runner** — Typed Clojure wrapper. `check-dirs` calls `t/check-dir-clj` and catches exceptions, converting them to data maps. Also serves as CLI entry point (`-m typedclojure-lsp.runner`) for CI usage with exit code 1 on failure.

**path** — Classpath and directory resolution. `classpath-dirs` returns canonical paths of all classpath entries. `current-directory` returns the canonical working directory.

**schema** — LSP message validation. Loads a generated JSON Schema (converted from Microsoft's official metaModel.json) and uses luposlip/json-schema to validate messages at boundaries. Schemas are registered in a mutable malli registry and cached as explainers for efficiency.

**schema.convert** — Build-time converter. Reads metaModel.json, converts the LSP type definitions we use (and their transitive dependencies) into JSON Schema draft-07, writes the result to a classpath resource.

### Data Shapes at Boundaries

**LSP context** (threaded through all handlers):
```clojure
{:server     <lsp4clj server>
 :root-uri!  (atom <string, project root path>)
 :files-with-diagnostics! (atom <set of URI strings>)}
```

**Runner output** (`runner/check-dirs` return value):
```clojure
{:result :ok}
;; or
{:result :type-errors
 :type-errors [{:message "..." :form '(...) :type-error :keyword
                :env {:line int :column int :file "file://..."}}]}
;; or
{:result :exception :exception <Throwable>}
```

**Outgoing diagnostics** (LSP PublishDiagnosticsParams):
```clojure
{:uri "file:///path/to/file.clj"
 :diagnostics [{:source "typedclojure"
                :message "Function add could not be applied..."
                :range {:start {:line 11 :character 3}
                        :end {:line 11 :character 19}}}]}
```
```

- [ ] **Step 2: Commit**

```bash
git add docs/architecture.md
git commit -m "Add architecture documentation"
```

---

## Task 11: Docstrings

**Files:**
- Modify: `src/typedclojure_lsp/main.clj`
- Modify: `src/typedclojure_lsp/lsp.clj`
- Modify: `src/typedclojure_lsp/path.clj`

- [ ] **Step 1: Add docstrings to main.clj**

The `-main` function needs a docstring:

```clojure
(defn -main
  "CLI entry point. Starts the LSP server over stdio."
  [& _args]
  (start! {}))
```

- [ ] **Step 2: Add docstrings to lsp.clj**

```clojure
(defn loggable-context
  "Return the context with the server object elided for safe logging."
  [context]
  ...)

(defn type-check-and-notify!
  "Run the type checker on classpath directories under the project root,
  then publish diagnostics to the editor. Clears stale diagnostics from
  files that no longer have errors."
  [{:keys [server files-with-diagnostics! root-uri!] :as _context}]
  ...)

(defn start-stdio-server!
  "Create and start an LSP server communicating over stdio.
  Returns a map with :server and :start! (a derefable future)."
  []
  ...)
```

Add docstrings to the `send-diagnostics!` and `validate-outgoing!` private functions too (these were added in Task 4).

- [ ] **Step 3: Add docstrings to path.clj**

```clojure
(defn classpath-dirs
  "Return canonical paths for all directories on the JVM classpath."
  []
  ...)

(defn current-directory
  "Return the canonical path of the current working directory."
  []
  ...)
```

- [ ] **Step 4: Run all tests to verify nothing broke**

```bash
clojure -M:test:dev
```

Expected: all PASS.

- [ ] **Step 5: Commit**

```bash
git add src/typedclojure_lsp/main.clj src/typedclojure_lsp/lsp.clj src/typedclojure_lsp/path.clj
git commit -m "Add docstrings to all public functions"
```

---

## Task 12: Final Verification

- [ ] **Step 1: Run the full test suite**

```bash
mise test
```

Expected: all tests PASS.

- [ ] **Step 2: Run the type checker**

```bash
mise typecheck
```

Expected: passes (or shows known issues in new namespaces that aren't annotated with `^:typed.clojure` yet).

- [ ] **Step 3: Format the code**

```bash
mise format
```

- [ ] **Step 4: Commit any formatting changes**

```bash
git add -A
git status
```

If there are formatting changes:

```bash
git commit -m "Format code"
```
