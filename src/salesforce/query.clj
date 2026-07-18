(ns salesforce.query
  "SOQL, SOSL and cursor-safe pagination."
  (:require
   [clojure.string :as str]
   [salesforce.api :as api])
  (:import
   [java.time Instant LocalDate ZonedDateTime]))

(defprotocol SOQLable
  (->soql [value] "Serializes a value as a SOQL literal."))

(defn- escape-string [value]
  (-> value
      (str/replace "\\" "\\\\")
      (str/replace "'" "\\'")
      (str/replace "\n" "\\n")
      (str/replace "\r" "\\r")))

(extend-protocol SOQLable
  String (->soql [value] (str "'" (escape-string value) "'"))
  clojure.lang.Ratio (->soql [value] (str (double value)))
  Number (->soql [value] (str value))
  Boolean (->soql [value] (if value "TRUE" "FALSE"))
  nil (->soql [_] "NULL")
  LocalDate (->soql [value] (str value))
  Instant (->soql [value] (str value))
  ZonedDateTime (->soql [value] (str (.toInstant value)))
  clojure.lang.IPersistentSet
  (->soql [values] (str "(" (str/join "," (sort (map ->soql values))) ")"))
  clojure.lang.Sequential
  (->soql [values] (str "(" (str/join "," (map ->soql values)) ")")))

(defn sqlvec->query
  "Expands ? placeholders outside quoted SOQL strings using SOQLable values."
  [[query & values]]
  (when (or (not (string? query)) (str/blank? query))
    (throw (ex-info "A non-empty SOQL query string is required"
                    {:type ::invalid-query})))
  (let [serialized (map ->soql values)]
    (loop [characters (seq query)
           values serialized
           quoted? false
           escaped? false
           result (StringBuilder.)]
      (if-let [character (first characters)]
        (cond
          (and (= character \?) (not quoted?))
          (if-let [value (first values)]
            (recur (next characters) (next values) quoted? false (.append result value))
            (throw (ex-info "Not enough values for SOQL placeholders"
                            {:type ::invalid-query})))

          :else
          (let [quote? (and (= character \') (not escaped?))
                escaped-next? (and (= character \\) (not escaped?))]
            (.append result character)
            (recur (next characters)
                   values
                   (if quote? (not quoted?) quoted?)
                   escaped-next?
                   result)))
        (do
          (when (seq values)
            (throw (ex-info "Too many values for SOQL placeholders"
                            {:type ::invalid-query})))
          (str result))))))

(defn query
  [client-value soql]
  (api/request! client-value :get "/query" {:query-params {:q soql}}))

(defn query-all [client-value soql]
  (api/request! client-value :get "/queryAll" {:query-params {:q soql}}))

(defn query-more
  "Follows Salesforce's opaque nextRecordsUrl verbatim."
  [client-value next-records-url]
  (api/raw-request! client-value :get next-records-url))

(defn pages
  "Returns a lazy sequence of query pages. Network calls happen as pages realize."
  [client-value first-page]
  (letfn [(step [page]
            (lazy-seq
             (when page
               (cons page
                     (when-let [next-url (:nextRecordsUrl page)]
                       (step (query-more client-value next-url)))))))]
    (step first-page)))

(defn records
  "Returns a lazy sequence of records across all query pages."
  [client-value first-page]
  (mapcat :records (pages client-value first-page)))

(defn explain [client-value soql]
  (api/request! client-value :get "/query" {:query-params {:explain soql}}))

(defn search [client-value sosl]
  (api/request! client-value :get "/search" {:query-params {:q sosl}}))

(defn parameterized-search [client-value parameters]
  (api/request! client-value :get "/parameterizedSearch" {:query-params parameters}))

(defn search-scope-and-order [client-value]
  (api/request! client-value :get "/search/scopeOrder"))

(defn suggestions [client-value sobject query parameters]
  (api/request! client-value :get
                "/search/suggestions"
                {:query-params (merge {:q query :sobject sobject} parameters)}))
