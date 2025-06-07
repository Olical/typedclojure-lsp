(ns typedclojure-lsp.lsp
  (:require [clojure.core.async :as a]
            [lsp4clj.server :as server]
            [lsp4clj.io-server :as io-server]
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

(defmethod server/receive-request "initialize"
  [_ context params]
  (te/log!
   {:level :info
    :data {:context context
           :params params}}
   "initialize")
  {:capabilities {}
   :serverInfo {:name "typedclojure"}})

(defmethod server/receive-notification "initialized"
  [_ context params]
  (te/log!
   {:level :info
    :data {:context context
           :params params}}
   "initialized"))

(defn type-check-and-notify! [{:keys [server] :as _context}]
  (te/log! :info "Running type checker...")
  (let [{:keys [result type-errors] :as type-check-result} (runner/check-dirs ["dev" "src"])]
    (te/log!
     {:level :info
      :data type-check-result})

    (when (= result :type-errors)
      (run!
       (fn [[file-path type-errors-for-file]]
         (server/send-notification
          server "textDocument/publishDiagnostics"
          {:uri file-path
           :diagnostics
           (map
            (fn [{:keys [message form _type-error env]}]
              {:source "typedclojure"
               :message message
               :range {:start {:line (:line env) :character (:column env)}
                       :end {:line (:line env) :character (+ (:column env) (count (pr-str form)))}}})
            type-errors-for-file)}))
       (group-by (comp :file :env) type-errors))))

  nil)

(defmethod server/receive-notification "textDocument/didOpen"
  [_ context params]
  (te/log!
   {:level :info
    :data {:context context
           :params params}}
   "textDocument/didOpen")
  (type-check-and-notify! context))

(defmethod server/receive-notification "textDocument/didSave"
  [_ context params]
  (te/log!
   {:level :info
    :data {:context context
           :params params}}
   "textDocument/didSave")
  (type-check-and-notify! context))

; (t/ann tcp/start-server [[manifold.stream.default.Stream map? :-> t/Nothing] :-> netty/AlephServer])
; (t/ann start-server! [:-> netty/AlephServer])
(defn start-stdio-server! []
  (let [server (io-server/stdio-server)
        context {:server server}
        start! (server/start server context)]

    (a/go-loop []
      (when-let [[level & args] (a/<! (:log-ch server))]
        (te/log!
         {:level :info
          :data {:level level
                 :args args}}
         "lsp4clj [log]")
        (recur)))

    (a/go-loop []
      (when-let [[level & args] (a/<! (:trace-ch server))]
        (te/log!
         {:level :info
          :data {:level level
                 :args args}}
         "lsp4clj [trace]")
        (recur)))

    {:server server
     :start! start!}))
