(ns background-check.kaocha-test
  (:require [clojure.test :as t]
            [spy.core :as spy]
            [background-check.kaocha :as kaocha]
            [background-check.runner :as runner]))

(t/deftest plugin-post-run-hook
  (t/testing "it calls check-dirs with the dirs config"
    (with-redefs [runner/check-dirs (spy/spy)]
      (let [dirs ["foo"]]
        (kaocha/plugin-post-run-hook {:background-check/dirs dirs})
        (t/is (spy/called-once? runner/check-dirs))
        (t/is (spy/called-with? runner/check-dirs dirs))))))
