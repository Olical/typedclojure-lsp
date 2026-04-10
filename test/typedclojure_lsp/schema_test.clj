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
