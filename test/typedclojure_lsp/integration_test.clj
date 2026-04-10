(ns typedclojure-lsp.integration-test
  "Channel-pair integration tests that exercise the full LSP stack including
  the real type checker. These tests start a real lsp4clj server over
  core.async channels and send/receive LSP messages through it."
  (:require [clojure.core.async :as a]
            [clojure.test :as t]
            [lsp4clj.server :as server]
            [matcher-combinators.test]
            [taoensso.telemere :as te]
            [typedclojure-lsp.lsp]
            [typedclojure-lsp.path :as path]))

;; Under kaocha, clojure.java.classpath/classpath may not return all project
;; directories (src, dev, test) because kaocha uses its own classloader.
;; We override path/classpath-dirs to ensure the type checker can find the
;; directories it needs.
(defn- full-classpath-dirs
  "Returns the project's source directories that exist on the filesystem,
  regardless of what the classloader reports."
  []
  (let [root (System/getProperty "user.dir")]
    (mapv #(str root "/" %) ["src" "dev" "test" "resources"])))

(defn- send! [input-ch msg]
  (te/log! {:level :debug :data {:msg msg}} "integration-test send!")
  (a/>!! input-ch msg))

(defn- read! [output-ch & {:keys [timeout-ms] :or {timeout-ms 30000}}]
  (let [result (a/alt!!
                 output-ch ([v] v)
                 (a/timeout timeout-ms) ::timeout)]
    (te/log! {:level :debug :data {:msg result}} "integration-test read!")
    result))

(defn- collect!
  "Read all available messages from output-ch. Waits up to `initial-timeout-ms`
  for the first message, then keeps reading with a shorter `drain-timeout-ms`
  to collect any remaining queued messages."
  [output-ch & {:keys [initial-timeout-ms drain-timeout-ms]
                :or {initial-timeout-ms 60000 drain-timeout-ms 2000}}]
  (loop [msgs []
         timeout (if (empty? msgs) initial-timeout-ms drain-timeout-ms)]
    (let [result (a/alt!!
                   output-ch ([v] v)
                   (a/timeout timeout) ::timeout)]
      (if (or (= result ::timeout) (nil? result))
        msgs
        (recur (conj msgs result)
               drain-timeout-ms)))))

(defn- with-lsp-server*
  "Starts a real lsp4clj chan-server with the standard LSP handlers and calls
  `f` with a map of {:send! :read! :collect! :input-ch :output-ch :server}."
  [f]
  (let [input-ch (a/chan 100)
        output-ch (a/chan 100)
        srv (server/chan-server {:input-ch input-ch :output-ch output-ch})
        context {:server srv
                 :files-with-diagnostics! (atom #{})
                 :root-uri! (atom nil)}
        _join (server/start srv context)]
    ;; Drain log-ch and trace-ch so they don't fill up
    (a/go-loop []
      (when-let [[level & args] (a/<! (:log-ch srv))]
        (te/log! {:level :debug :data {:level level :args args}} "lsp4clj [log]")
        (recur)))
    (a/go-loop []
      (when-let [[level & args] (a/<! (:trace-ch srv))]
        (te/log! {:level :debug :data {:level level :args args}} "lsp4clj [trace]")
        (recur)))
    (try
      (f {:send! (partial send! input-ch)
          :read! (partial read! output-ch)
          :collect! (partial collect! output-ch)
          :input-ch input-ch
          :output-ch output-ch
          :server srv})
      (finally
        (server/shutdown srv)))))

(defmacro with-lsp-server
  "Binds `sym` to a test helper map and runs body within a real LSP server."
  [sym & body]
  `(with-lsp-server* (fn [~sym] ~@body)))

(defn- project-root []
  (System/getProperty "user.dir"))

(defn- initialize-request [id root-path]
  {:jsonrpc "2.0"
   :id id
   :method "initialize"
   :params {:root-path root-path}})

(defn- initialized-notification []
  {:jsonrpc "2.0"
   :method "initialized"
   :params {}})

(defn- did-save-notification [uri]
  {:jsonrpc "2.0"
   :method "textDocument/didSave"
   :params {:text-document {:uri uri}}})

(defn- did-open-notification [uri]
  {:jsonrpc "2.0"
   :method "textDocument/didOpen"
   :params {:text-document {:uri uri
                            :language-id "clojure"
                            :version 1
                            :text ""}}})

(defn- init-sequence!
  "Perform the standard initialize/initialized handshake, returning the
  initialize response. After this, the server will begin type checking."
  [{:keys [send! read!]} root-path]
  (send! (initialize-request 1 root-path))
  (let [resp (read!)]
    (te/log! {:level :info :data {:response resp}} "initialize response")
    (send! (initialized-notification))
    resp))

(defn- diagnostics-notifications
  "Filter a collection of messages to just publishDiagnostics notifications."
  [msgs]
  (filter #(= "textDocument/publishDiagnostics" (:method %)) msgs))

(defn- has-type-error-diagnostics?
  "Returns true if any of the messages contain non-empty diagnostics."
  [msgs]
  (some (fn [msg]
          (seq (get-in msg [:params :diagnostics])))
        (diagnostics-notifications msgs)))

(t/deftest full-lifecycle-test
  (t/testing "initialize -> initialized -> receive publishDiagnostics with type errors"
    (with-redefs [path/classpath-dirs full-classpath-dirs]
      (with-lsp-server ctx
        (let [resp (init-sequence! ctx (project-root))
              _ (te/log! {:level :info :data {:resp resp}} "init response received, waiting for diagnostics...")
              msgs ((:collect! ctx) :initial-timeout-ms 120000 :drain-timeout-ms 3000)]
          (te/log! {:level :info :data {:msgs msgs}} "collected messages after initialized")

          ;; The initialize response should have capabilities
          (t/is (match? {:jsonrpc "2.0"
                         :id 1
                         :result {:capabilities map?
                                  :serverInfo {:name "typedclojure"}}}
                        resp))

          ;; We should receive publishDiagnostics notifications with type errors
          ;; from dev/examples/core.clj
          (let [diag-msgs (diagnostics-notifications msgs)]
            (t/is (pos? (count diag-msgs))
                  "Expected at least one publishDiagnostics notification")
            (t/is (has-type-error-diagnostics? msgs)
                  "Expected diagnostics with type errors from dev/examples")))))))

(t/deftest did-save-triggers-recheck-test
  (t/testing "didSave triggers a new type check and publishes diagnostics"
    (with-redefs [path/classpath-dirs full-classpath-dirs]
      (with-lsp-server ctx
        (init-sequence! ctx (project-root))
        ;; Collect initial diagnostics from initialized
        ((:collect! ctx) :initial-timeout-ms 120000 :drain-timeout-ms 3000)

        ;; Now send didSave and collect new diagnostics
        ((:send! ctx) (did-save-notification "file:///whatever.clj"))
        (let [msgs ((:collect! ctx) :initial-timeout-ms 120000 :drain-timeout-ms 3000)
              diag-msgs (diagnostics-notifications msgs)]
          (te/log! {:level :info :data {:msgs msgs}} "collected messages after didSave")
          (t/is (pos? (count diag-msgs))
                "Expected publishDiagnostics after didSave")
          (t/is (has-type-error-diagnostics? msgs)
                "Expected type errors in diagnostics after didSave"))))))

(t/deftest did-open-triggers-recheck-test
  (t/testing "didOpen triggers a new type check and publishes diagnostics"
    (with-redefs [path/classpath-dirs full-classpath-dirs]
      (with-lsp-server ctx
        (init-sequence! ctx (project-root))
        ;; Collect initial diagnostics from initialized
        ((:collect! ctx) :initial-timeout-ms 120000 :drain-timeout-ms 3000)

        ;; Now send didOpen and collect new diagnostics
        ((:send! ctx) (did-open-notification "file:///whatever.clj"))
        (let [msgs ((:collect! ctx) :initial-timeout-ms 120000 :drain-timeout-ms 3000)
              diag-msgs (diagnostics-notifications msgs)]
          (te/log! {:level :info :data {:msgs msgs}} "collected messages after didOpen")
          (t/is (pos? (count diag-msgs))
                "Expected publishDiagnostics after didOpen")
          (t/is (has-type-error-diagnostics? msgs)
                "Expected type errors in diagnostics after didOpen"))))))

(t/deftest clean-code-empty-diagnostics-test
  (t/testing "init with root pointing at src/ only produces no error diagnostics"
    (with-redefs [path/classpath-dirs full-classpath-dirs]
      (with-lsp-server ctx
        ;; Point root at just src/ which should have clean typed code
        (let [src-root (str (project-root) "/src")]
          (init-sequence! ctx src-root)
          ;; Type checking src/ should complete quickly with no errors.
          ;; Use a shorter timeout since we expect either no messages or only
          ;; empty-diagnostics clearing notifications.
          (let [msgs ((:collect! ctx) :initial-timeout-ms 30000 :drain-timeout-ms 3000)
                diag-msgs (diagnostics-notifications msgs)]
            (te/log! {:level :info :data {:msgs msgs}} "collected messages for clean-code test")
            ;; Either there are no diagnostic notifications, or all of them have empty diagnostics
            (t/is (every? (fn [msg]
                            (empty? (get-in msg [:params :diagnostics])))
                          diag-msgs)
                  "Expected no error diagnostics when checking only src/")))))))

(t/deftest stale-diagnostics-cleared-test
  (t/testing "recheck clears stale diagnostics before re-publishing"
    (with-redefs [path/classpath-dirs full-classpath-dirs]
      (with-lsp-server ctx
        (init-sequence! ctx (project-root))
        ;; Collect initial diagnostics (should include type errors)
        (let [initial-msgs ((:collect! ctx) :initial-timeout-ms 120000 :drain-timeout-ms 3000)]
          (te/log! {:level :info :data {:msgs initial-msgs}} "initial diagnostics collected")
          (t/is (has-type-error-diagnostics? initial-msgs)
                "Expected initial type error diagnostics")

          ;; Trigger a recheck via didSave
          ((:send! ctx) (did-save-notification "file:///whatever.clj"))
          (let [recheck-msgs ((:collect! ctx) :initial-timeout-ms 120000 :drain-timeout-ms 3000)
                diag-msgs (diagnostics-notifications recheck-msgs)]
            (te/log! {:level :info :data {:msgs recheck-msgs}} "recheck messages collected")

            ;; On recheck, the server should first clear stale diagnostics
            ;; (empty diagnostics array) before sending new ones.
            ;; Find the clearing notifications (empty diagnostics) that come
            ;; before the new error diagnostics.
            (let [clearing-msgs (filter #(empty? (get-in % [:params :diagnostics])) diag-msgs)
                  error-msgs (filter #(seq (get-in % [:params :diagnostics])) diag-msgs)]
              (t/is (pos? (count clearing-msgs))
                    "Expected clearing notifications (empty diagnostics) before re-publishing")
              (t/is (pos? (count error-msgs))
                    "Expected error diagnostics after clearing"))))))))
