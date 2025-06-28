(ns ^:typed.clojure typedclojure-lsp.path
  (:require [clojure.java.io :as io]
            [clojure.java.classpath :as cp]
            [typed.clojure :as t]))

(set! *warn-on-reflection* true)

(t/ann cp/classpath [-> (t/Seqable java.io.File)])
(t/ann classpath-dirs [-> (t/Seqable t/Str)])
(defn classpath-dirs []
  (keep #(.getCanonicalPath ^java.io.File %) (cp/classpath)))

(t/ann current-directory [-> t/Str])
(defn current-directory []
  (let [path (.getCanonicalPath (io/file "."))]
    (assert (string? path))
    path))
