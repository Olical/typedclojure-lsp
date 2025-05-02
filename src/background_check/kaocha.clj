(ns background-check.kaocha
  (:require [kaocha.plugin :as kp]
            [background-check.runner :as runner]))

(kp/defplugin background-check.kaocha/plugin
  (post-run
   [result]
   (prn (runner/check-dirs (get result :background-check/dirs)))
   result))
