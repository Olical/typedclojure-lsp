(ns typedclojure-lsp.runner-test
  (:require [clojure.test :as t]
            [matcher-combinators.test]
            [typedclojure-lsp.runner :as runner]))

(t/deftest check-dirs
  (t/testing "executes typed.clojure on the given dir, returning errors as data"
    (t/is
     (match?
      {:result :type-errors
       :type-errors
       [{:message "Function add could not be applied to arguments:\n\n\nDomains:\n\ttyped.clojure/Num typed.clojure/Num\n\nArguments:\n\t(typed.clojure/Val :foo) (typed.clojure/Val 10)\n\nRanges:\n\ttyped.clojure/Num\n\nwith expected type:\n\ttyped.clojure/Num\n\n"
         :env {:column 3,
               :file #"file:.*/typedclojure-lsp/dev/examples/core\.clj",
               :line 11},
         :form '(add :foo 10)
         :type-error :clojure.core.typed.errors/type-error}]}
      (runner/check-dirs ["dev/examples"]))))

  (t/testing "returns an exception if there's nothing to check"
    (t/is (match?
           {:result :exception
            :exception #(instance? java.lang.AssertionError %)}
           (runner/check-dirs ["dev/giants_shoulders"]))))

  (t/testing "returns :ok if everything is fine"
    (t/is (= {:result :ok} (runner/check-dirs ["src"]))))

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
          (t/is (string? file) "env should have a string file"))))))
