(ns typedclojure-lsp.dev
  (:require [nrepl.server :as nrepl]
            [cider.nrepl :as cider]
            [lsp4clj.server :as server]
            [taoensso.telemere :as te]
            [typedclojure-lsp.main :as main]))

(defonce server! (atom nil))

(defn start!
  "Start up the LSP with an nREPL for development."
  [params]

  (let [{:keys [port] :as _server} (nrepl/start-server :handler cider/cider-nrepl-handler)]
    (te/log! :info (str "nREPL server started on port " port))
    (te/log! :info "Writing port to .nrepl-port")
    (spit ".nrepl-port" port))

  (main/start! params)

  (shutdown-agents)
  (System/exit 0))

(comment
  (server/send-notification
   @server! "window/showMessage"
   {:message "hello from typedclojure-lsp"
    :type "info"}))
