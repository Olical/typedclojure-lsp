(ns background-check.kaocha
  "Plugin that executes Typed Clojure."
  (:require [kaocha.plugin :as kp]
            [background-check.runner :as runner]
            [typed.clojure :as t]))

(t/defalias KaochaOptions
  (t/HMap
   :optional
   {:background-check/dirs (t/Seqable t/Str)}))

(t/ann kaocha.plugin/-register [t/Keyword :-> t/Keyword])
(t/ann plugin-post-run-hook [KaochaOptions :-> KaochaOptions])

(kp/defplugin background-check.kaocha/plugin
  (post-run
   [result]
   (assert (map? result))
   (runner/check-dirs (get result :background-check/dirs))
   result))
