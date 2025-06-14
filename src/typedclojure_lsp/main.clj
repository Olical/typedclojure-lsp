(ns typedclojure-lsp.main
  (:require [taoensso.telemere :as te]
            [typedclojure-lsp.lsp :as lsp])
  (:gen-class))

(defn start!
  "Start up the LSP with an nREPL for development."
  [_params]

  (te/remove-handler! :default/console)
  (te/add-handler!
   :typedclojure-lsp/console
   (binding [*out* *err*]
     (te/handler:console)))

  (Thread/setDefaultUncaughtExceptionHandler
   (reify Thread$UncaughtExceptionHandler
     (uncaughtException [_ thread ex]
       (te/log!
        {:level :error
         :data {:thread (.getName thread)
                :exception ex}}
        "Uncaught exception in thread"))))

  (te/log! :info "Starting typedclojure-lsp")
  @(:start! (lsp/start-stdio-server!))
  (te/log! :info "Shutting down typedclojure-lsp")

  (shutdown-agents)
  (System/exit 0))

(defn -main [& _args]
  (start! {}))
