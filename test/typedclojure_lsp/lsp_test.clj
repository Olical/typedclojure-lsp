(ns typedclojure-lsp.lsp-test
  (:require [clojure.test :as t]
            [matcher-combinators.test]
            [lsp4clj.server :as server]
            [typedclojure-lsp.lsp :as lsp]
            [typedclojure-lsp.runner :as runner]
            [typedclojure-lsp.path :as path]))

(t/deftest initialize-test
  (t/testing "returns capabilities and sets root-uri atom"
    (let [root-uri! (atom nil)
          context {:server :test-server
                   :files-with-diagnostics! (atom #{})
                   :root-uri! root-uri!}
          result (server/receive-request "initialize" context {:root-path "/home/user/project"})]
      (t/is (match?
             {:capabilities {:textDocumentSync {:openClose true
                                                :save {:includeText false}}}
              :serverInfo {:name "typedclojure"}}
             result))
      (t/is (= "/home/user/project" @root-uri!)))))

(t/deftest type-check-and-notify-test
  (t/testing "with type errors, sends diagnostics with correct file/message/range"
    (let [notifications (atom [])
          files-with-diagnostics! (atom #{})
          context {:server :test-server
                   :files-with-diagnostics! files-with-diagnostics!
                   :root-uri! (atom "/home/user/project")}
          mock-type-errors [{:message "Type mismatch"
                             :form '(add :foo 10)
                             :type-error :clojure.core.typed.errors/type-error
                             :env {:line 5
                                   :column 3
                                   :file "file:///home/user/project/src/core.clj"}}]]
      (with-redefs [runner/check-dirs (fn [_dirs]
                                        {:result :type-errors
                                         :type-errors mock-type-errors})
                    path/classpath-dirs (fn []
                                         ["/home/user/project/src"
                                          "/home/user/project/test"
                                          "/other/project/src"])
                    server/send-notification (fn [_server method params]
                                              (swap! notifications conj {:method method :params params}))]
        (lsp/type-check-and-notify! context)
        (t/is (= 1 (count @notifications)))
        (t/is (match?
               {:method "textDocument/publishDiagnostics"
                :params {:uri "file:///home/user/project/src/core.clj"
                         :diagnostics [{:source "typedclojure"
                                        :message "Type mismatch"
                                        :range {:start {:line 5 :character 3}
                                                :end {:line 5 :character (+ 3 (count (pr-str '(add :foo 10))))}}}]}}
               (first @notifications))))))

  (t/testing "with :ok result and pre-existing file, sends empty diagnostics to clear stale file"
    (let [notifications (atom [])
          stale-file "file:///home/user/project/src/old.clj"
          files-with-diagnostics! (atom #{stale-file})
          context {:server :test-server
                   :files-with-diagnostics! files-with-diagnostics!
                   :root-uri! (atom "/home/user/project")}]
      (with-redefs [runner/check-dirs (fn [_dirs]
                                        {:result :ok})
                    path/classpath-dirs (fn []
                                         ["/home/user/project/src"
                                          "/home/user/project/test"])
                    server/send-notification (fn [_server method params]
                                              (swap! notifications conj {:method method :params params}))]
        (lsp/type-check-and-notify! context)
        (t/is (= 1 (count @notifications)))
        (t/is (match?
               {:method "textDocument/publishDiagnostics"
                :params {:uri stale-file
                         :diagnostics []}}
               (first @notifications)))))))
