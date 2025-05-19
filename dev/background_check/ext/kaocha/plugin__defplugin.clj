(ns ^:typed.clojure ^:no-doc background-check.ext.kaocha.plugin__defplugin
  "Typing rules clojure.core/assert"
  (:require [typed.cljc.checker.check :as check]
            [typed.cljc.analyzer :as ana2]
            [clojure.core.typed.util-vars :as vs]
            [typed.cljc.checker.check.unanalyzed :refer [defuspecial]]))

(defuspecial defuspecial__defplugin
  "defuspecial implementation for kaocha.plugin/defplugin"
  [expr expected {{:keys [check-form-eval]} ::vs/check-config
                  ::check/keys [check-expr] :as opts}]
  (when (= :never check-form-eval)
    (check-expr (ana2/analyze-outer expr opts) expected opts)))
