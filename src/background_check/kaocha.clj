(ns ^:typed.clojure background-check.kaocha
  "Plugin that executes Typed Clojure."
  (:require [clojure.string :as str]
            [clojure.java.io :as io]
            [kaocha.plugin :as kp]
            [background-check.runner :as runner]
            [background-check.format :as fmt]
            [typed.clojure :as t]))

(t/defalias KaochaOptions
  (t/HMap
   :optional
   {:background-check/dirs (t/Seqable t/Str)}))

(t/ann output-file-path t/Str)
(def output-file-path ".background-check/errors.txt")

(t/ann write-errors! [(t/Seqable runner/TypeError) :-> t/Nothing])
(t/ann clojure.core/spit [t/Str t/Str :-> t/Nothing])

(defn write-errors!
  "Spits the errors out into a hard-coded file, for now. This will become configurable if required or become the point where we're write out to an LSP client. This is very much a placeholder to help with experimentation and a proof of concept."
  [type-errors]
  (io/make-parents output-file-path)
  (spit output-file-path (str/join "\n" (map fmt/error->str type-errors))))

(t/ann ^:no-check kaocha.plugin/-register [t/Keyword :-> t/Keyword])
(t/ann plugin-post-run-hook [KaochaOptions :-> KaochaOptions])

(kp/defplugin background-check.kaocha/plugin
  (post-run
   [kaocha-result]
   (assert (map? kaocha-result))
   (let [{:keys [result type-errors exception] :as _check-dirs-result}
         (runner/check-dirs (get kaocha-result :background-check/dirs))]
     (case result
       :type-errors (write-errors! type-errors)
       :exception (println exception)
       :ok (println "all good")))
   kaocha-result))
