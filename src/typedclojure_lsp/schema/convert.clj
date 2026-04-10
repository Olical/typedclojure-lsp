(ns typedclojure-lsp.schema.convert
  (:require [clojure.data.json :as json]
            [clojure.java.io :as io]
            [clojure.set]))

(def ^:private base-type-map
  {"string"      {"type" "string"}
   "integer"     {"type" "integer"}
   "uinteger"    {"type" "integer"}
   "decimal"     {"type" "number"}
   "boolean"     {"type" "boolean"}
   "null"        {"type" "null"}
   "DocumentUri" {"type" "string"}
   "URI"         {"type" "string"}})

(defn convert-type
  "Converts a single metaModel type descriptor to JSON Schema."
  [type-desc]
  (case (get type-desc "kind")
    "base"
    (get base-type-map (get type-desc "name")
         ;; fallback for unknown base types
         {"type" "string"})

    "reference"
    {"$ref" (str "#/definitions/" (get type-desc "name"))}

    "array"
    {"type" "array"
     "items" (convert-type (get type-desc "element"))}

    "or"
    {"oneOf" (mapv convert-type (get type-desc "items"))}

    "map"
    {"type" "object"
     "additionalProperties" (convert-type (get type-desc "value"))}

    "literal"
    (let [props (get-in type-desc ["value" "properties"])
          properties (into {} (map (fn [p]
                                     [(get p "name") (convert-type (get p "type"))]))
                           props)
          required (into [] (comp (remove #(get % "optional"))
                                  (map #(get % "name")))
                         props)]
      {"type" "object"
       "properties" properties
       "required" required
       "additionalProperties" false})

    "stringLiteral"
    {"type" "string" "enum" [(get type-desc "value")]}

    "tuple"
    {"type" "array"
     "items" (mapv convert-type (get type-desc "items"))}

    "and"
    {"allOf" (mapv convert-type (get type-desc "items"))}

    ;; fallback
    {}))

(defn convert-structure
  "Converts a metaModel structure to JSON Schema object.
   Uses additionalProperties false. If structure has extends/mixins, wraps in allOf."
  [structure]
  (let [props (get structure "properties" [])
        properties (into {} (map (fn [p]
                                   [(get p "name") (convert-type (get p "type"))]))
                         props)
        required (into [] (comp (remove #(get % "optional"))
                                (map #(get % "name")))
                       props)
        obj-schema {"type" "object"
                    "properties" properties
                    "required" required
                    "additionalProperties" false}
        extends (get structure "extends" [])
        mixins (get structure "mixins" [])]
    (if (seq (concat extends mixins))
      {"allOf" (into (mapv convert-type (concat extends mixins))
                     [obj-schema])}
      obj-schema)))

(defn convert-enumeration
  "Converts a metaModel enumeration to JSON Schema enum."
  [enumeration]
  (let [base-schema (convert-type (get enumeration "type"))
        values (mapv #(get % "value") (get enumeration "values"))]
    (assoc base-schema "enum" values)))

(def ^:private root-types
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

(defn- load-meta-model []
  (-> (io/resource "typedclojure-lsp/lsp-meta-model.json")
      slurp
      (json/read-str)))

(defn- collect-refs
  "Recursively collects all reference names from a type descriptor."
  [type-desc]
  (case (get type-desc "kind")
    "reference" #{(get type-desc "name")}
    "array" (collect-refs (get type-desc "element"))
    "or" (into #{} (mapcat collect-refs) (get type-desc "items"))
    "and" (into #{} (mapcat collect-refs) (get type-desc "items"))
    "map" (into (collect-refs (get type-desc "key"))
                (collect-refs (get type-desc "value")))
    "literal" (into #{} (mapcat #(collect-refs (get % "type")))
                    (get-in type-desc ["value" "properties"]))
    "tuple" (into #{} (mapcat collect-refs) (get type-desc "items"))
    #{}))

(defn- structure-refs
  "Collects all type references from a structure."
  [structure]
  (let [prop-refs (into #{} (mapcat #(collect-refs (get % "type")))
                        (get structure "properties" []))
        extend-refs (into #{} (mapcat collect-refs)
                          (get structure "extends" []))
        mixin-refs (into #{} (mapcat collect-refs)
                         (get structure "mixins" []))]
    (clojure.set/union prop-refs extend-refs mixin-refs)))

(defn- type-alias-refs
  "Collects all type references from a type alias."
  [type-alias]
  (collect-refs (get type-alias "type")))

(defn- enumeration-refs
  "Enumerations don't reference other types."
  [_enumeration]
  #{})

(defn generate-json-schema
  "Loads metaModel, resolves transitive deps from root-types, generates full JSON Schema document."
  []
  (let [meta-model (load-meta-model)
        structures-by-name (into {} (map (fn [s] [(get s "name") s]))
                                 (get meta-model "structures"))
        enumerations-by-name (into {} (map (fn [e] [(get e "name") e]))
                                   (get meta-model "enumerations"))
        type-aliases-by-name (into {} (map (fn [ta] [(get ta "name") ta]))
                                   (get meta-model "typeAliases"))
        ;; Resolve transitive dependencies via BFS
        resolved (loop [to-visit root-types
                        visited #{}]
                   (if (empty? to-visit)
                     visited
                     (let [current (first to-visit)
                           remaining (disj to-visit current)]
                       (if (visited current)
                         (recur remaining visited)
                         (let [deps (cond
                                      (structures-by-name current)
                                      (structure-refs (structures-by-name current))

                                      (type-aliases-by-name current)
                                      (type-alias-refs (type-aliases-by-name current))

                                      (enumerations-by-name current)
                                      (enumeration-refs (enumerations-by-name current))

                                      :else #{})
                               new-deps (clojure.set/difference deps visited)]
                           (recur (into remaining new-deps)
                                  (conj visited current)))))))
        ;; Build definitions
        definitions (into {}
                          (keep (fn [type-name]
                                  (cond
                                    (structures-by-name type-name)
                                    [type-name (convert-structure (structures-by-name type-name))]

                                    (enumerations-by-name type-name)
                                    [type-name (convert-enumeration (enumerations-by-name type-name))]

                                    (type-aliases-by-name type-name)
                                    [type-name (convert-type (get (type-aliases-by-name type-name) "type"))]

                                    :else nil)))
                          resolved)]
    {"$schema" "http://json-schema.org/draft-07/schema#"
     "definitions" definitions}))

(defn -main
  "Generates and writes the JSON Schema to resources/typedclojure-lsp/lsp-json-schema.json."
  [& _args]
  (let [schema (generate-json-schema)
        output-path "resources/typedclojure-lsp/lsp-json-schema.json"]
    (spit output-path (json/write-str schema :indent true))
    (println "Generated JSON Schema at" output-path)
    (println (count (get schema "definitions")) "definitions")))
