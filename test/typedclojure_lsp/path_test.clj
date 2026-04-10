(ns typedclojure-lsp.path-test
  (:require [clojure.test :as t]
            [clojure.string]
            [typedclojure-lsp.path :as path]))

(t/deftest classpath-dirs-test
  (t/testing "returns a seq of strings"
    (let [dirs (path/classpath-dirs)]
      (t/is (seq dirs) "classpath-dirs should not be empty")
      (t/is (every? string? dirs) "every entry should be a string")))

  (t/testing "includes expected source paths from this project"
    (let [dirs (path/classpath-dirs)
          has-suffix? (fn [suffix] (some #(clojure.string/ends-with? % suffix) dirs))]
      ;; Kaocha sets up a classloader with the test path, so /test is
      ;; the one directory we can reliably assert on in the test runner.
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
