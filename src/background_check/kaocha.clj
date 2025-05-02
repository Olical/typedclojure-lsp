(ns background-check.kaocha
  (:require [kaocha.plugin :as kp]
            [typed.clojure :as t]))

(kp/defplugin background-check.kaocha/plugin
  (post-run
   [result]
   (try
     (t/check-dir-clj (get result :background-check/dirs))
     (catch clojure.lang.ExceptionInfo e
       (prn (map ex-data (:errors (ex-data e))))))
   result))
