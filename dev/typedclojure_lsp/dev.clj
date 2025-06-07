(ns typedclojure-lsp.dev
  (:require [nrepl.server :as nrepl]
            [cider.nrepl :as cider]
            [lsp4clj.server :as server]
            [taoensso.telemere :as te]
            [typedclojure-lsp.lsp :as lsp]))

(defonce server! (atom nil))

(defn start!
  "Start up the LSP with an nREPL for development."
  [_params]

  (te/log! :info "Starting development server")
  (let [{:keys [port] :as _server} (nrepl/start-server :handler cider/cider-nrepl-handler)]
    (te/log! :info (str "nREPL server started on port " port))
    (te/log! :info "Writing port to .nrepl-port")
    (spit ".nrepl-port" port))

  (let [{:keys [server start!]} (lsp/start-stdio-server!)]
    (reset! server! server)
    @start!)

  (te/log! :info "Shutting down development server")
  (shutdown-agents)
  (System/exit 0))

(comment
  (server/send-notification
   @server! "window/showMessage"
   {:message "hello from typedclojure-lsp"
    :type "info"}))
