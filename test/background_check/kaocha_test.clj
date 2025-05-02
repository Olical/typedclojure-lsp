(ns background-check.kaocha-test
  (:require [clojure.test :as t]))

;; TODO Test the Kaocha plugin hook, just make sure it calls the right function.
(t/deftest hello-world
  (t/is (= 1 1)))
