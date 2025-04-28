(ns background-check.kaocha
  (:require [kaocha.plugin :as kp]
            [typed.clojure :as t]))

(kp/defplugin background-check.kaocha/plugin
  (post-run
   [result]
   (let [pats (or (seq (map re-pattern (get :background-check/ns-patterns result)))
                  [#".*"])]
     (try
       (some-> (filter
                (fn [ns]
                  (let [ns-name (str ns)]
                    (some #(re-find % ns-name) pats)))
                (all-ns))
               (seq)
               (t/check-ns-clj))
       (catch clojure.lang.ExceptionInfo e
         (println "errors!"))))
   result))
