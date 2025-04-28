(ns background-check.kaocha
  (:require [kaocha.plugin :as kp]
            [typed.clojure :as t]))

(kp/defplugin background-check.kaocha/plugin
  (post-run
   [result]
   (try
     (some-> (filter #(re-find #"^examples." (str %)) (all-ns))
             (seq)
             (t/check-ns-clj))
     (catch clojure.lang.ExceptionInfo e
       (println "errors!")))
   result))
