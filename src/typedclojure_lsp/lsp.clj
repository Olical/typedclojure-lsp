(ns typedclojure-lsp.lsp
  (:require [clojure.core.async :as a]
            [clojure.java.classpath :as cp]
            [clojure.string :as str]
            [lsp4clj.server :as server]
            [lsp4clj.io-server :as io-server]
            [taoensso.telemere :as te]
            [typedclojure-lsp.runner :as runner]))

;; TODO Hook the server up to a channel pair for testing.
;; TODO Type check this namespace. Dog food is delicious!

(defn loggable-context [context]
  (assoc context :server ::elided))

(defmethod server/receive-request "initialize"
  [_ context params]
  (te/log!
   {:level :info
    :data {:context (loggable-context context)
           :params params}}
   "initialize")
  (reset! (:root-uri! context) (:root-path params))
  {:capabilities {:textDocumentSync {:openClose true
                                     :save {:includeText false}}}
   :serverInfo {:name "typedclojure"}})

(defn type-check-and-notify! [{:keys [server files-with-diagnostics! root-uri!] :as _context}]
  (te/log! :info "Running type checker...")
  (let [classpath-dirs (map #(.getCanonicalPath %) (cp/classpath))

        ;; We can't just give the project root, typedclojure doesn't seem to do anything then.
        ;; So instead we pass all the classpath dirs under the project root.
        dirs-to-check (filter #(str/starts-with? % @root-uri!) classpath-dirs)

        {:keys [result type-errors] :as type-check-result} (runner/check-dirs dirs-to-check)]
    (te/log!
     {:level :info
      :data {:dirs-to-check dirs-to-check
             :type-check-result type-check-result}}
     "Type checking result")

    (run!
     (fn [path]
       (server/send-notification
        server "textDocument/publishDiagnostics"
        {:uri path
         :diagnostics []}))
     @files-with-diagnostics!)

    (when (= result :type-errors)
      (run!
       (fn [[file-path type-errors-for-file]]
         (swap! files-with-diagnostics! conj file-path)
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
(defn start-stdio-server! []
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
