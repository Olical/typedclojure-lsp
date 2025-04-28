(ns examples.core
  (:require [malli.experimental :as mx]))

(mx/defn add :- number?
  "If you have LSP configured correctly you should see a type error / warning if you try to type (add :foo 10) inside this buffer."
  [a :- number?
   b :- number?]
  (+ a b))

(mx/defn a-bad-fn :- number? []
  (add :foo 10))
