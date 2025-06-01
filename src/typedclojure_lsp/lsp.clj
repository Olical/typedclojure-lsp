(ns typedclojure-lsp.lsp
  (:require [clojure.set :as set]
            [clojure.core.async :as async]
            [lsp4clj.io-server :as io-server]
            [lsp4clj.server :as server]
            [aleph.tcp :as tcp]
            [aleph.netty :as netty]
            [manifold.stream :as s]
            [taoensso.telemere :as te]
            [typedclojure-lsp.runner :as runner])
  (:import [java.io OutputStream PipedInputStream PipedOutputStream]))

;; TODO Hook up https://github.com/nextjournal/beholder and typedclojure to type check on file change, or should it be driven by LSP?
;; TODO Hook the server up to a channel pair for testing.

;; TODO On textDocument/didOpen or textDocument/didSave, run the diagnostics. (almost there, Neovim isn't activating it though, so it's not attached to my buffer)
;; TODO Run textDocument/publishDiagnostics with the results.

;; TODO Can this be cleaned up or simplified somehow? Am I missing a function that already exists that does this for me.
;; TODO Can I just start a simpler socket server? I don't need manifold here https://github.com/clojure-lsp/lsp4clj?tab=readme-ov-file#other-types-of-servers
(defn manifold-duplex->io-streams [duplex-stream]
  (let [pout (PipedOutputStream.)
        pin  (PipedInputStream. pout)
        out (proxy [OutputStream] []
              (write
                ([b]
                 (s/put! duplex-stream b))
                ([b off len]
                 (let [segment (java.util.Arrays/copyOfRange b off (+ off len))]
                   (s/put! duplex-stream segment)))))]

    (s/consume
     (fn [msg]
       (let [bytes (if (instance? (Class/forName "[B") msg)
                     msg
                     (.getBytes (str msg) "UTF-8"))]
         (.write pout bytes)
         (.flush pout)))
     duplex-stream)

    {:input-stream pin
     :output-stream out}))

; (t/ann handler [manifold.stream.default.Stream map? :-> t/Nothing])
(defn handler [duplex-stream client-info]
  (te/log! {:level :info :data client-info} "New connection.")
  (let [lsp-server
        (io-server/server
         (merge
          {:trace-level "verbose"}
          (set/rename-keys
           (manifold-duplex->io-streams duplex-stream)
           {:input-stream :in
            :output-stream :out})))]

    (async/go-loop []
      (when-let [[level & args] (async/<! (:trace-ch lsp-server))]
        (te/log!
         {:level :info
          :data {:level level
                 :args args}}
         "lsp4clj")
        (recur)))

    (server/send-notification
     lsp-server "window/showMessage"
     {:message "hello from typedclojure-lsp"
      :type "info"}))
  nil)

(defn type-check-and-notify! []
  (runner/check-dirs ["."]))

(defmethod server/receive-notification "textDocument/didOpen"
  [_ context opts]
  (te/log!
   {:level :info
    :data {:context context
           :opts opts}}
   "textDocument/didOpen")
  (type-check-and-notify!))

(defmethod server/receive-notification "textDocument/didSave"
  [_ context opts]
  (te/log!
   {:level :info
    :data {:context context
           :opts opts}}
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
