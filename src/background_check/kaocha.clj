(ns background-check.kaocha
  "Plugin that executes Typed Clojure."
  (:require [kaocha.plugin :as kp]
            [background-check.runner :as runner]
            [typed.clojure :as t]))

(t/ann
 plugin-post-run-hook
 [(t/HMap
   :optional
   {:background-check/dirs (t/Seqable t/Str)})])

(kp/defplugin background-check.kaocha/plugin
  (post-run
   [result]
   (assert (map? result))
   (runner/check-dirs (get result :background-check/dirs))
   result))
