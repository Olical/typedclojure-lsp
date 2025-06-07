(ns typedclojure-lsp.lsp
  (:require [clojure.java.io :as io]
            [clojure.core.async :as a]
            [lsp4clj.server :as server]
            [lsp4clj.io-server :as io-server]
            [aleph.tcp :as tcp]
            [aleph.netty :as netty]
            [manifold.stream :as s]
            [clj-commons.byte-streams :as bs]
            [taoensso.telemere :as te]
            [typedclojure-lsp.runner :as runner]))

;; TODO Hook up https://github.com/nextjournal/beholder and typedclojure to type check on file change, or should it be driven by LSP?
;; TODO Hook the server up to a channel pair for testing.

;; TODO On textDocument/didOpen or textDocument/didSave, run the diagnostics. (almost there, Neovim isn't activating it though, so it's not attached to my buffer)
;; TODO Run textDocument/publishDiagnostics with the results.

(te/add-handler! :typedclojure-lsp/file (te/handler:file {:path ".typedclojure-lsp/logs/typedclojure-lsp.log"}))
(te/remove-handler! :default/console)

(Thread/setDefaultUncaughtExceptionHandler
 (reify Thread$UncaughtExceptionHandler
   (uncaughtException [_ thread ex]
     (te/log!
      {:level :error
       :data {:thread (.getName thread)
              :exception ex}}
      "Uncaught exception in thread"))))

(defn manifold-stream->io-pair [duplex-stream]
  (let [pout (java.io.PipedOutputStream.)
        pin (java.io.PipedInputStream. pout)
        out (proxy [java.io.OutputStream] []
              (write
                ([b]
                 (s/put! duplex-stream b))
                ([b off len]
                 (let [segment (java.util.Arrays/copyOfRange b off (+ off len))]
                   (s/put! duplex-stream segment)))))]

    (s/consume
     (fn [msg]
       (.write pout msg)
       (.flush pout))
     duplex-stream)

    {:in pin
     :out out}))

; (t/ann handler [manifold.stream.default.Stream map? :-> t/Nothing])
(defn handler [duplex-stream client-info]
  (try
    (te/log! {:level :info :data client-info} "New connection.")
    (let [lsp-server
          (io-server/server
           (merge
            {:trace-level "verbose"}
            (manifold-stream->io-pair duplex-stream)))]

      (a/go-loop []
        (when-let [[level & args] (a/<! (:log-ch lsp-server))]
          (te/log!
           {:level :info
            :data {:level level
                   :args args}}
           "lsp4clj [log]")
          (recur)))

      (a/go-loop []
        (when-let [[level & args] (a/<! (:trace-ch lsp-server))]
          (te/log!
           {:level :info
            :data {:level level
                   :args args}}
           "lsp4clj [trace]")
          (recur)))

      (server/send-notification
       lsp-server "window/showMessage"
       {:message "hello from typedclojure-lsp\n"
        :type "info"}))
    nil
    (catch Exception e
      (te/log!
       {:level :error
        :data {:exception e}}
       "Error in LSP handler")
      (throw e))))

(defmethod server/receive-request "initialize"
  [_ context params]
  (te/log!
   {:level :info
    :data {:context context
           :params params}}
   "initialize")
  {})

(defn type-check-and-notify! []
  (runner/check-dirs ["."]))

(defmethod server/receive-notification "textDocument/didOpen"
  [_ context params]
  (te/log!
   {:level :info
    :data {:context context
           :params params}}
   "textDocument/didOpen")
  (type-check-and-notify!))

(defmethod server/receive-notification "textDocument/didSave"
  [_ context params]
  (te/log!
   {:level :info
    :data {:context context
           :params params}}
   "textDocument/didSave")
  (type-check-and-notify!))

; (t/ann tcp/start-server [[manifold.stream.default.Stream map? :-> t/Nothing] :-> netty/AlephServer])
; (t/ann start-server! [:-> netty/AlephServer])
(defn start-server! []
  ;; TODO Random port, write to file that gets deleted on exit.
  (tcp/start-server handler {:port 9999}))

(comment
  (def server (start-server!))
  (.close server)
  (netty/port server))
