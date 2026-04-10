(ns typedclojure-lsp.schema
  "Schema registration and validation for LSP types.

  Provides a mutable malli registry, define!/validate helpers,
  and lsp-json-schema->malli for bridging JSON Schema definitions
  into malli validators via luposlip/json-schema."
  (:require [clojure.string :as str]
            [malli.core :as m]
            [malli.error :as me]
            [malli.util :as mu]
            [malli.registry :as mr]
            [json-schema.core :as json-schema]
            [taoensso.telemere :as te]))

(defonce schemas! (atom (merge (m/default-schemas) (mu/schemas))))
(mr/set-default-registry! (mr/mutable-registry schemas!))

(defonce explainers! (atom {}))

(defn define!
  "Register a schema in the global malli registry. Returns the id."
  [id schema]
  (swap! schemas! assoc id schema)
  (reset! explainers! {})
  id)

(defn- upsert-explainer!
  "Return a cached explainer for the schema id, compiling if needed."
  [id]
  (if-let [schema (get @schemas! id)]
    (or (get @explainers! id)
        (let [explainer (m/explainer schema)]
          (swap! explainers! assoc id explainer)
          explainer))
    nil))

(defn validate
  "Validate a value against a registered schema.
  Returns nil on success, a map with :message and :humanized on failure."
  [id value]
  (if-let [explainer (upsert-explainer! id)]
    (when-let [explanation (explainer value)]
      {:message (str "Failed to validate against schema " id)
       :explanation explanation
       :humanized (me/humanize explanation)})
    {:message (str "Unknown schema: " id)}))

(defn lsp-json-schema->malli
  "Create a malli :fn schema that validates against a definition in the
  LSP JSON Schema resource using luposlip/json-schema."
  [definition-key]
  (let [prepared-schema
        (json-schema/prepare-schema
         {:$schema "http://json-schema.org/draft-07/schema"
          :id (str "typedclojure-lsp.schema/" (name definition-key))
          :$ref (str "classpath://typedclojure-lsp/lsp-json-schema.json#/definitions/" (name definition-key))}
         {:classpath-aware? true})]
    [:fn
     {:error/fn
      (fn [{:keys [_schema value]} _]
        (try
          (json-schema/validate prepared-schema value)
          nil
          (catch clojure.lang.ExceptionInfo e
            (let [cause (.getMessage e)
                  {:keys [errors]} (ex-data e)]
              (str cause " " (str/join ", " errors))))))}
     (fn [x]
       (try
         (json-schema/validate prepared-schema x)
         true
         (catch clojure.lang.ExceptionInfo _e
           false)))]))
