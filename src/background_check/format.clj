(ns ^:typed.clojure background-check.format
  "Formats Typed Clojure results for consumption by other systems."
  (:require [clojure.string :as str]
            [clojure.core.typed :as ct]
            [typed.clojure :as t]))

;; TODO Implement a fs namespace which can find the root directory of a project and write to a known file location.
;; The location and formatter should be configurable ultimately so we can eventually add a clj-kondo target.

(t/ann error->str [background-check.runner/TypeError :-> t/Str])

(defn error->str
  "Formats a Typed Clojure error as a string."
  [{:keys [type-error env _form _data _message] :as _error}]
  (ct/print-env "foo")
  (let [{:keys [file line column]} env
        trimmed-file (str/replace file #"^.*:" "")]
    (str trimmed-file ":" line ":" column " " (name type-error))))
