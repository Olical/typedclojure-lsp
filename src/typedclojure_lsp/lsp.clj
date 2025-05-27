(ns typedclojure-lsp.lsp
  (:require [lsp4clj.io-server :as io-server]
            [aleph.tcp :as tcp]
            [aleph.netty :as netty]
            [manifold.stream :as s]
            [taoensso.telemere :as te]))

; (t/ann handler [manifold.stream.default.Stream map? :-> t/Nothing])
(defn handler [duplex-stream client-info]
  (te/log! {:level :info :data client-info} "Handling new connection.")
  (io-server/stdio-server
   {:in duplex-stream
    :out duplex-stream})
  nil)

; (t/ann tcp/start-server [[manifold.stream.default.Stream map? :-> t/Nothing] :-> netty/AlephServer])
; (t/ann start-server! [:-> netty/AlephServer])
(defn start-server! []
  (tcp/start-server handler))

(comment
  (def server (start-server!))
  (.shutdown server))
