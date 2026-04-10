(ns typedclojure-lsp.smoke-test
  "End-to-end smoke test that spawns the actual JVM process, sends an LSP
  initialize request over stdin, and reads the response from stdout."
  (:require [clojure.data.json :as json]
            [clojure.test :as t]
            [taoensso.telemere :as te])
  (:import [java.io BufferedReader InputStreamReader OutputStream]
           [java.nio.charset StandardCharsets]))

(defn- project-root []
  (System/getProperty "user.dir"))

(defn- encode-lsp-message
  "Encodes a Clojure map as an LSP message with Content-Length header."
  [msg]
  (let [body (json/write-str msg)
        body-bytes (.getBytes body StandardCharsets/UTF_8)
        header (str "Content-Length: " (alength body-bytes) "\r\n\r\n")]
    (byte-array (concat (seq (.getBytes header StandardCharsets/UTF_8))
                        (seq body-bytes)))))

(defn- read-lsp-message
  "Reads a single LSP message from a BufferedReader. Parses the Content-Length
  header, then reads that many bytes of JSON body. Returns the parsed map."
  [^BufferedReader reader timeout-ms]
  (let [deadline (+ (System/currentTimeMillis) timeout-ms)]
    ;; Read headers until we find Content-Length
    (loop [content-length nil]
      (when (> (System/currentTimeMillis) deadline)
        (throw (ex-info "Timeout waiting for LSP response header" {})))
      (let [line (.readLine reader)]
        (te/log! {:level :debug :data {:header-line line}} "smoke-test read header line")
        (when (nil? line)
          (throw (ex-info "Stream closed while reading LSP headers" {})))
        (cond
          ;; Empty line signals end of headers
          (= line "")
          (if content-length
            (let [buf (char-array content-length)
                  remaining (- deadline (System/currentTimeMillis))]
              (when (<= remaining 0)
                (throw (ex-info "Timeout waiting for LSP response body" {})))
              ;; Read exactly content-length chars
              (loop [offset 0]
                (when (< offset content-length)
                  (let [n (.read reader buf offset (- content-length offset))]
                    (when (neg? n)
                      (throw (ex-info "Stream closed while reading LSP body" {})))
                    (recur (+ offset n)))))
              (let [body (String. buf)]
                (te/log! {:level :debug :data {:body body}} "smoke-test read body")
                (json/read-str body :key-fn keyword)))
            (throw (ex-info "End of headers without Content-Length" {})))

          ;; Parse Content-Length header
          (.startsWith line "Content-Length:")
          (let [len (-> line
                        (.substring (count "Content-Length:"))
                        (.trim)
                        (Integer/parseInt))]
            (recur len))

          ;; Skip other headers
          :else
          (recur content-length))))))

(t/deftest stdio-smoke-test
  (t/testing "spawn process, send initialize, verify response has capabilities"
    (let [proc-builder (doto (ProcessBuilder. ["clojure" "-M" "-m" "typedclojure-lsp.main"])
                         (.directory (java.io.File. (project-root)))
                         (.redirectErrorStream false))
          proc (.start proc-builder)]
      (te/log! {:level :info} "smoke-test: process started")
      (try
        (let [stdin (.getOutputStream proc)
              stdout-reader (BufferedReader.
                             (InputStreamReader.
                              (.getInputStream proc)
                              StandardCharsets/UTF_8))
              stderr-reader (BufferedReader.
                             (InputStreamReader.
                              (.getErrorStream proc)
                              StandardCharsets/UTF_8))
              ;; Drain stderr in a background thread so it doesn't block
              stderr-future (future
                              (try
                                (loop []
                                  (when-let [line (.readLine stderr-reader)]
                                    (te/log! {:level :debug :data {:stderr line}} "smoke-test stderr")
                                    (recur)))
                                (catch Exception e
                                  (te/log! {:level :debug :data {:error e}} "smoke-test stderr reader done"))))
              init-request {:jsonrpc "2.0"
                            :id 1
                            :method "initialize"
                            :params {:processId nil
                                     :rootPath (project-root)
                                     :capabilities {}}}
              msg-bytes (encode-lsp-message init-request)]

          ;; Send initialize request
          (te/log! {:level :info :data {:request init-request}} "smoke-test: sending initialize")
          (.write ^OutputStream stdin msg-bytes)
          (.flush stdin)

          ;; Read initialize response with 60s timeout
          (let [response (read-lsp-message stdout-reader 60000)]
            (te/log! {:level :info :data {:response response}} "smoke-test: received response")

            (t/is (= 1 (:id response))
                  "Response should have :id 1")
            (t/is (some? (:result response))
                  "Response should have a :result")
            (t/is (some? (get-in response [:result :capabilities]))
                  "Result should contain :capabilities")))
        (finally
          (te/log! {:level :info} "smoke-test: destroying process")
          (.destroyForcibly proc))))))
