(ns background-check.ext.kaocha.plugin
  (:require [typed.clj.checker.check.unanalyzed :as un-clj]))

(un-clj/install-defuspecial
 'kaocha.plugin/defplugin
 'background-check.ext.kaocha.plugin__defplugin/defuspecial__defplugin)
