(ns background-check.format-test
  (:require [clojure.test :as t]
            [matcher-combinators.test]
            [background-check.format :as fmt]
            [background-check.runner :as runner]))

(t/deftest error->str
  (t/testing "converts a type checking error to a string"
    (t/is (match?
           #"/.*/dev/examples/core\.clj:11:3 type-error"
           (fmt/error->str (:type-errors (runner/check-dirs ["dev/examples"])))))))
