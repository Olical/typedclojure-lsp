(ns background-check.core-test
  (:require [clojure.test :as t]
            [background-check.core :as core]))

(t/deftest example
  (t/is (= true (core/example))))
