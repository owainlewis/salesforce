(ns salesforce.graphql
  "Salesforce GraphQL endpoint with HTTP-200 logical error handling."
  (:require
   [salesforce.api :as api]))

(defn execute!
  ([client-value query]
   (execute! client-value query nil nil))
  ([client-value query variables]
   (execute! client-value query variables nil))
  ([client-value query variables operation-name]
   (let [body (api/request! client-value :post "/graphql"
                            {:json-body (cond-> {:query query}
                                          variables (assoc :variables variables)
                                          operation-name (assoc :operationName operation-name))})]
     (when (seq (:errors body))
       (throw (ex-info "Salesforce GraphQL operation returned errors"
                       {:type ::graphql-error
                        :errors (:errors body)
                        :data (:data body)})))
     body)))
