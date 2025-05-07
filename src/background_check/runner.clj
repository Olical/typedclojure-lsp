(ns ^:typed.clojure background-check.runner
  "Wrappers around typed.clojure that return type check results as data."
  (:require [typed.clojure :as t]))

(t/ann t/check-dir-clj [(t/Seqable t/Str) :-> t/Nothing])

(t/defalias TypedClojureExInfoData
  (t/HMap
   :mandatory {:form t/Any
               :type-error t/Keyword
               :env (t/HMap
                     :mandatory {:line t/Num
                                 :column t/Num
                                 :file t/Str})}))

(t/defalias TypeError
  (t/Merge
   TypedClojureExInfoData
   (t/HMap
    :mandatory {:message (t/Nilable t/Str)})))

(t/ann ExceptionInfo->type-errors [t/ExInfo :-> (t/ASeq TypeError)])
(defn ExceptionInfo->type-errors
  "Takes an ExceptionInfo from Typed Clojure and converts it to a sequence of maps we can easily display."
  [exinf]
  (let [errors ^{::t/unsafe-cast (t/Seqable t/ExInfo)} (:errors (ex-data exinf))]
    (assert errors)
    (map
     (fn [error]
       (let [{:keys [env form type-error]}
             ^{::t/unsafe-cast TypedClojureExInfoData} (ex-data error)

             {:keys [line column file]} env]
         (assert env)
         (assert type-error)
         (assert line)
         (assert column)
         (assert file)
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
  (t/U
   (t/HMap
    :mandatory
    {:result (t/Val :ok)})

   (t/HMap
    :mandatory
    {:result (t/Val :type-errors)
     :type-errors (t/Seqable TypeError)})

   (t/HMap
    :mandatory
    {:result (t/Val :exception)
     :exception Throwable}))])

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
