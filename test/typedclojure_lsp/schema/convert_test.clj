(ns typedclojure-lsp.schema.convert-test
  (:require [clojure.test :as t]
            [matcher-combinators.test]
            [typedclojure-lsp.schema.convert :as convert]))

(t/deftest convert-type-test
  (t/testing "base types"
    (t/is (= {"type" "string"}
             (convert/convert-type {"kind" "base" "name" "string"})))
    (t/is (= {"type" "integer"}
             (convert/convert-type {"kind" "base" "name" "integer"})))
    (t/is (= {"type" "integer"}
             (convert/convert-type {"kind" "base" "name" "uinteger"})))
    (t/is (= {"type" "boolean"}
             (convert/convert-type {"kind" "base" "name" "boolean"})))
    (t/is (= {"type" "null"}
             (convert/convert-type {"kind" "base" "name" "null"})))
    (t/is (= {"type" "string"}
             (convert/convert-type {"kind" "base" "name" "DocumentUri"})))
    (t/is (= {"type" "string"}
             (convert/convert-type {"kind" "base" "name" "URI"}))))

  (t/testing "reference"
    (t/is (= {"$ref" "#/definitions/Foo"}
             (convert/convert-type {"kind" "reference" "name" "Foo"}))))

  (t/testing "array"
    (t/is (= {"type" "array"
              "items" {"type" "string"}}
             (convert/convert-type {"kind" "array"
                                    "element" {"kind" "base" "name" "string"}}))))

  (t/testing "or"
    (t/is (= {"oneOf" [{"type" "integer"} {"type" "string"}]}
             (convert/convert-type {"kind" "or"
                                    "items" [{"kind" "base" "name" "integer"}
                                             {"kind" "base" "name" "string"}]}))))

  (t/testing "map"
    (t/is (= {"type" "object"
              "additionalProperties" {"type" "string"}}
             (convert/convert-type {"kind" "map"
                                    "key" {"kind" "base" "name" "string"}
                                    "value" {"kind" "base" "name" "string"}}))))

  (t/testing "literal (inline object)"
    (t/is (= {"type" "object"
              "properties" {"name" {"type" "string"}
                            "version" {"type" "string"}}
              "required" ["name"]
              "additionalProperties" false}
             (convert/convert-type
              {"kind" "literal"
               "value" {"properties"
                        [{"name" "name"
                          "type" {"kind" "base" "name" "string"}}
                         {"name" "version"
                          "type" {"kind" "base" "name" "string"}
                          "optional" true}]}}))))

  (t/testing "stringLiteral"
    (t/is (= {"type" "string" "enum" ["foo"]}
             (convert/convert-type {"kind" "stringLiteral" "value" "foo"})))))

(t/deftest convert-structure-test
  (t/testing "simple structure (Position)"
    (t/is (= {"type" "object"
              "properties" {"line" {"type" "integer"}
                            "character" {"type" "integer"}}
              "required" ["line" "character"]
              "additionalProperties" false}
             (convert/convert-structure
              {"name" "Position"
               "properties" [{"name" "line"
                              "type" {"kind" "base" "name" "uinteger"}}
                             {"name" "character"
                              "type" {"kind" "base" "name" "uinteger"}}]}))))

  (t/testing "optional properties (SaveOptions)"
    (t/is (= {"type" "object"
              "properties" {"includeText" {"type" "boolean"}}
              "required" []
              "additionalProperties" false}
             (convert/convert-structure
              {"name" "SaveOptions"
               "properties" [{"name" "includeText"
                              "type" {"kind" "base" "name" "boolean"}
                              "optional" true}]}))))

  (t/testing "structure with extends"
    (t/is (= {"allOf"
              [{"$ref" "#/definitions/Parent"}
               {"type" "object"
                "properties" {"extra" {"type" "string"}}
                "required" ["extra"]
                "additionalProperties" false}]}
             (convert/convert-structure
              {"name" "Child"
               "properties" [{"name" "extra"
                              "type" {"kind" "base" "name" "string"}}]
               "extends" [{"kind" "reference" "name" "Parent"}]})))))

(t/deftest convert-enumeration-test
  (t/testing "integer enum (DiagnosticSeverity)"
    (t/is (= {"type" "integer" "enum" [1 2 3 4]}
             (convert/convert-enumeration
              {"name" "DiagnosticSeverity"
               "type" {"kind" "base" "name" "uinteger"}
               "values" [{"name" "Error" "value" 1}
                         {"name" "Warning" "value" 2}
                         {"name" "Information" "value" 3}
                         {"name" "Hint" "value" 4}]}))))

  (t/testing "string enum (TraceValues)"
    (t/is (= {"type" "string" "enum" ["off" "messages" "verbose"]}
             (convert/convert-enumeration
              {"name" "TraceValues"
               "type" {"kind" "base" "name" "string"}
               "values" [{"name" "Off" "value" "off"}
                         {"name" "Messages" "value" "messages"}
                         {"name" "Verbose" "value" "verbose"}]})))))

(t/deftest generate-json-schema-test
  (t/testing "full generation from real metaModel"
    (let [schema (convert/generate-json-schema)]
      (t/testing "has the expected top-level shape"
        (t/is (= "http://json-schema.org/draft-07/schema#" (get schema "$schema")))
        (t/is (map? (get schema "definitions"))))

      (t/testing "Position definition is correct"
        (t/is (match?
               {"type" "object"
                "properties" {"line" {"type" "integer"}
                              "character" {"type" "integer"}}
                "required" ["line" "character"]
                "additionalProperties" false}
               (get-in schema ["definitions" "Position"]))))

      (t/testing "Range references Position"
        (t/is (match?
               {"type" "object"
                "properties" {"start" {"$ref" "#/definitions/Position"}
                              "end" {"$ref" "#/definitions/Position"}}
                "required" ["start" "end"]
                "additionalProperties" false}
               (get-in schema ["definitions" "Range"]))))

      (t/testing "Diagnostic definition exists"
        (let [diag (get-in schema ["definitions" "Diagnostic"])]
          (t/is (some? diag))
          (t/is (= {"$ref" "#/definitions/Range"}
                    (get-in diag ["properties" "range"])))))

      (t/testing "PublishDiagnosticsParams definition exists"
        (t/is (some? (get-in schema ["definitions" "PublishDiagnosticsParams"]))))

      (t/testing "InitializeResult definition exists"
        (t/is (some? (get-in schema ["definitions" "InitializeResult"]))))

      (t/testing "DiagnosticSeverity is an integer enum"
        (t/is (match?
               {"type" "integer" "enum" [1 2 3 4]}
               (get-in schema ["definitions" "DiagnosticSeverity"])))))))
