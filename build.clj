(ns build
  (:require [clojure.tools.build.api :as b]
            [deps-deploy.deps-deploy :as dd]))

(def lib 'uk.me.oli/typedclojure-lsp)

;; TODO Use the GitHub rev / tag
;; https://github.com/jlesquembre/clojars-publish-action/blob/89a4eb7bdbe1270621e6643250afce152701699e/src/entrypoint.clj#L41-L47
(def version "0.0.3") ; (b/git-count-revs nil)

(def class-dir "target/classes")
(def jar-file (format "target/%s-%s.jar" (name lib) version))

(def pom-template
  [[:description "Typed Clojure in your editor over LSP."]
   [:url "https://github.com/Olical/typedclojure-lsp"]
   [:licenses
    [:license
     [:name "Unlicense"]
     [:url "https://unlicense.org/"]]]
   [:developers
    [:developer
     [:name "Oliver Caldwell"]]]
   [:scm
    [:url "https://github.com/Olical/typedclojure-lsp"]
    [:connection "scm:git:https://github.com/Olical/typedclojure-lsp.git"]
    [:developerConnection "scm:git:ssh://git@github.com:Olical/typedclojure-lsp.git"]
    [:tag (str "v" version)]]])

(def basis (delay (b/create-basis {:project "deps.edn"})))

(defn clean [_]
  (b/delete {:path "target"}))

(defn jar [_]
  (b/write-pom {:class-dir class-dir
                :lib lib
                :version version
                :basis @basis
                :pom-data pom-template
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

(defn deploy "Deploy the JAR to Clojars." [opts]
  (dd/deploy {:installer :remote :artifact (b/resolve-path jar-file)
              :pom-file (b/pom-path
                         {:lib lib
                          :class-dir class-dir})})
  opts)
