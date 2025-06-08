(ns typedclojure-lsp.main
  (:require [taoensso.telemere :as te]
            [typedclojure-lsp.lsp :as lsp]))

(defn start!
  "Start up the LSP with an nREPL for development."
  [{:keys [logging?]
    :or {logging? true}}]

  (te/remove-handler! :default/console)
  (when logging?
    (te/add-handler! :typedclojure-lsp/file (te/handler:file {:path ".typedclojure-lsp/logs/typedclojure-lsp.log"})))

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
