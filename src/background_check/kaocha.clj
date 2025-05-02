(ns background-check.kaocha
  "Plugin that executes Typed Clojure."
  (:require [kaocha.plugin :as kp]
            [background-check.runner :as runner]))

(kp/defplugin background-check.kaocha/plugin
  (post-run
   [result]
   (runner/check-dirs (get result :background-check/dirs))
   result))
