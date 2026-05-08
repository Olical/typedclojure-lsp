(ns typedclojure-lsp.lsp
  (:require [clojure.core.async :as a]
            [clojure.string :as str]
            [lsp4clj.server :as server]
            [lsp4clj.io-server :as io-server]
            [taoensso.telemere :as te]
            [typedclojure-lsp.path :as path]
            [typedclojure-lsp.runner :as runner]
            [typedclojure-lsp.schema :as schema]))

;; TODO Hook the server up to a channel pair for testing.
;; TODO Type check this namespace. Dog food is delicious!

(schema/define! ::initialize-result
  (schema/lsp-json-schema->malli :InitializeResult))

(schema/define! ::publish-diagnostics-params
  (schema/lsp-json-schema->malli :PublishDiagnosticsParams))

(schema/define! ::initialize-params
  (schema/lsp-json-schema->malli :InitializeParams))

(defn- validate-outgoing!
  "Validate an outgoing LSP message against its schema.
  Logs an error if validation fails since this indicates a bug in us."
  [schema-id message]
  (when-let [error (schema/validate schema-id message)]
    (te/log!
     {:level :error
      :data {:schema-id schema-id
             :error error
             :message message}}
     "Outgoing LSP message failed schema validation")))

(defn- validate-incoming!
  "Validate an incoming LSP message against its schema.
  Logs a warning if validation fails but does not throw."
  [schema-id message]
  (when-let [error (schema/validate schema-id message)]
    (te/log!
     {:level :warn
      :data {:schema-id schema-id
             :error error
             :message message}}
     "Incoming LSP message failed schema validation")))

(defn loggable-context
  "Returns context with the :server value elided for safe logging."
  [context]
  (assoc context :server ::elided))

(defmethod server/receive-request "initialize"
  [_ context params]
  (te/log!
   {:level :info
    :data {:context (loggable-context context)
           :params params}}
   "initialize")
  (validate-incoming! ::initialize-params params)
  (reset! (:root-uri! context) (:root-path params))
  (let [result {:capabilities {:textDocumentSync {:openClose true
                                                  :save {:includeText false}}}
                :serverInfo {:name "typedclojure"}}]
    (validate-outgoing! ::initialize-result result)
    result))

(defn- send-diagnostics!
  "Send a publishDiagnostics notification, validating the params first."
  [server params]
  (validate-outgoing! ::publish-diagnostics-params params)
  (server/send-notification server "textDocument/publishDiagnostics" params))

(defn type-check-and-notify!
  "Runs the type checker on classpath dirs under root-uri and publishes diagnostics to the client.
  Clears previously reported diagnostics before sending new ones."
  [{:keys [server files-with-diagnostics! root-uri!] :as _context}]
  (te/log! :info "Running type checker...")
  (let [;; We can't just give the project root, typedclojure doesn't seem to do anything then.
        ;; So instead we pass all the classpath dirs under the project root.
        dirs-to-check (filter #(str/starts-with? % @root-uri!) (path/classpath-dirs))

        {:keys [result type-errors] :as type-check-result} (runner/check-dirs dirs-to-check)]
    (te/log!
     {:level :info
      :data {:dirs-to-check dirs-to-check
             :type-check-result type-check-result}}
     "Type checking result")

    (run!
     (fn [path]
       (send-diagnostics! server {:uri path :diagnostics []}))
     @files-with-diagnostics!)

    (when (= result :type-errors)
      (run!
       (fn [[file-path type-errors-for-file]]
         (swap! files-with-diagnostics! conj file-path)
         (send-diagnostics!
          server
          {:uri file-path
           :diagnostics
           (map
            (fn [{:keys [message form _type-error env]}]
              ;; typedclojure reports 1-indexed line/column; LSP requires 0-indexed.
              (let [line (dec (:line env))
                    column (dec (:column env))]
                {:source "typedclojure"
                 :message message
                 :range {:start {:line line :character column}
                         :end {:line line :character (+ column (count (pr-str form)))}}}))
            type-errors-for-file)}))
       (group-by (comp :file :env) type-errors))))

  nil)

(defmethod server/receive-notification "initialized"
  [_ context params]
  (te/log!
   {:level :info
    :data {:context (loggable-context context)
           :params params}}
   "initialized")
  (type-check-and-notify! context))

(defmethod server/receive-notification "textDocument/didOpen"
  [_ context params]
  (te/log!
   {:level :info
    :data {:context (loggable-context context)
           :params params}}
   "textDocument/didOpen")
  (type-check-and-notify! context))

(defmethod server/receive-notification "textDocument/didSave"
  [_ context params]
  (te/log!
   {:level :info
    :data {:context (loggable-context context)
           :params params}}
   "textDocument/didSave")
  (type-check-and-notify! context))

; (t/ann tcp/start-server [[manifold.stream.default.Stream map? :-> t/Nothing] :-> netty/AlephServer])
; (t/ann start-server! [:-> netty/AlephServer])
(defn start-stdio-server!
  "Creates and starts an LSP server communicating over stdio.
  Returns a map with :server and :start! (a derefable that blocks until shutdown)."
  []
  (let [server (io-server/stdio-server)
        context {:server server
                 :files-with-diagnostics! (atom #{})
                 :root-uri! (atom nil)}
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
