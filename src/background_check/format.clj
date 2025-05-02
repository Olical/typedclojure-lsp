(ns background-check.format
  "Formats Typed Clojure results for consumption by other systems.")

;; TODO Implement a [TypeError] -> Str formatter for consumption by https://github.com/mattn/efm-langserver
;; TODO Implement a fs namespace which can find the root directory of a project and write to a known file location.
;; The location and formatter should be configurable ultimately so we can eventually add a clj-kondo target.
