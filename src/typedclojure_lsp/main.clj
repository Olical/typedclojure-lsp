(ns typedclojure-lsp.main
  (:require [taoensso.telemere :as te]
            [typedclojure-lsp.lsp :as lsp])
  (:import [java.io PrintStream])
  (:gen-class))

(defonce ^PrintStream lsp-stdout System/out)

(defn setup-stdio!
  "Redirects System.out and *out* to stderr so anything that prints to stdout
  (telemere defaults, nREPL chatter, library banners, errant println in user
  code) lands on stderr instead of corrupting the LSP JSON-RPC transport. The
  original stdout is held in lsp-stdout for the LSP server to write to.
  Idempotent."
  []
  (System/setOut System/err)
  (alter-var-root #'*out* (constantly *err*))
  (te/remove-handler! :default/console)
  (te/add-handler! :typedclojure-lsp/console (te/handler:console {:stream :err})))

(defn start!
  "Start up the LSP server over stdio."
  [_params]
  (setup-stdio!)

  (Thread/setDefaultUncaughtExceptionHandler
   (reify Thread$UncaughtExceptionHandler
     (uncaughtException [_ thread ex]
       (te/log!
        {:level :error
         :data {:thread (.getName thread)
                :exception ex}}
        "Uncaught exception in thread"))))

  (te/log! :info "Starting typedclojure-lsp")
  @(:start! (lsp/start-stdio-server! {:out lsp-stdout}))
  (te/log! :info "Shutting down typedclojure-lsp")

  (shutdown-agents)
  (System/exit 0))

(defn -main
  "CLI entry point. Starts the LSP server over stdio."
  [& _args]
  (start! {}))
