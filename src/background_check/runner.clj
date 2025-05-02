(ns  background-check.runner
  "Wrappers around typed.clojure that return type check results as data."
  (:require [typed.clojure :as t]
            [clojure.core.typed :as tc]))

;; TODO Can we annotate inside the defn with a macro?
;; TODO Expand upon the t/Any placeholders.

(t/ann t/check-dir-clj [(t/Seqable t/Str) :-> t/Nothing])

(t/defalias TypeError
  (t/HMap
   :mandatory {:message (t/Nilable t/Str)
               :form t/Any
               :type-error t/Keyword
               :env (t/HMap
                     :mandatory {:line t/Num
                                 :column t/Num
                                 :file t/Str})}))

(t/ann ExceptionInfo->type-errors [t/ExInfo :-> (t/ASeq TypeError)])
(defn ExceptionInfo->type-errors
  "Takes an ExceptionInfo from Typed Clojure and converts it to a sequence of maps we can easily display."
  [exinf]
  (let [errors (:errors (ex-data exinf))]
    (assert errors)
    (map
     (fn [error]
       (let [{:keys [env form type-error]} (ex-data error)
             {:keys [line column file]} env]
         (assert (keyword? type-error))
         (assert (number? line))
         (assert (number? column))
         (assert (string? file))
         {:message (ex-message error)
          :form form
          :type-error type-error
          :env {:line line
                :column column
                :file file}}))
     errors)))

(t/ann
 check-dirs
 [(t/Seqable t/Str) :->
  (t/HMap
   :mandatory {:result (t/U (t/Val :ok) (t/Val :type-errors) (t/Val :exception))}
   :optional {:type-errors (t/Seqable TypeError)
              :exception t/Any})])
(defn check-dirs
  "Type check the given directories and return the errors as data or nil if there are none."
  [dirs]
  (try
    (t/check-dir-clj dirs)
    {:result :ok}
    (catch clojure.lang.ExceptionInfo e
      {:result :type-errors
       :type-errors (ExceptionInfo->type-errors e)})
    (catch Throwable e
      {:result :exception
       :exception e})))
