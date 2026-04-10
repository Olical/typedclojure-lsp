(ns ^:typed.clojure typedclojure-lsp.path
  (:require [clojure.java.io :as io]
            [clojure.java.classpath :as cp]
            [typed.clojure :as t]))

(t/ann cp/classpath [-> (t/Seqable java.io.File)])
(t/ann classpath-dirs [-> (t/Seqable t/Str)])
(defn classpath-dirs
  "Returns the canonical paths of all directories on the classpath as strings."
  []
  (keep #(.getCanonicalPath ^java.io.File %) (cp/classpath)))

(t/ann current-directory [-> t/Str])
(defn current-directory
  "Returns the canonical path of the current working directory as a string."
  []
  (let [path (.getCanonicalPath (io/file "."))]
    (assert (string? path))
    path))
