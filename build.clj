(ns build
  (:require [clojure.tools.build.api :as b]))

(def lib 'olical/typedclojure-lsp)

;; TODO Use the GitHub rev / tag
;; https://github.com/jlesquembre/clojars-publish-action/blob/89a4eb7bdbe1270621e6643250afce152701699e/src/entrypoint.clj#L41-L47
(def version "0.0.2") ; (b/git-count-revs nil)

(def class-dir "target/classes")
(def jar-file (format "target/%s-%s.jar" (name lib) version))

(def basis (delay (b/create-basis {:project "deps.edn"})))

(defn clean [_]
  (b/delete {:path "target"}))

(defn jar [_]
  (b/write-pom {:class-dir class-dir
                :lib lib
                :version version
                :basis @basis
                :src-dirs ["src"]})

  (b/copy-dir {:src-dirs ["src" "resources"]
               :target-dir class-dir})

  ;; Can AOT compile for performance one day.
  ;; May create JVM compatibility issues though!
  ; (b/compile-clj {:basis @basis
  ;                 :ns-compile '[typedclojure-lsp.main]
  ;                 :class-dir class-dir})

  (b/jar {:class-dir class-dir
          :jar-file jar-file}))
