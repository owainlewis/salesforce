(ns salesforce.client
  "HTTP transport and immutable client configuration for Salesforce REST APIs."
  (:require
   [cheshire.core :as json]
   [clj-http.client :as http]
   [clojure.string :as str])
  (:import
   [java.io ByteArrayOutputStream InputStream]
   [java.net URI URLEncoder]
   [java.nio.charset StandardCharsets]))

(def default-api-version "67.0")

(def ^:private local-hosts #{"localhost" "127.0.0.1" "::1"})

(def ^:dynamic *http-request*
  "HTTP adapter used by the client. Bind this in tests to avoid network calls."
  http/request)

(defn client
  "Creates an immutable Salesforce client.

  Required keys are :instance-url and either :access-token or :token-provider.
  Optional keys include :api-version, :http-options, :headers and :limit-info."
  [{:keys [instance-url instance_url access-token access_token token-provider
           api-version http-options headers limit-info allow-insecure?]
    :as options}]
  (let [instance-url (or instance-url instance_url)
        access-token (or access-token access_token)
        ^URI uri (try
                   (URI. (or instance-url ""))
                   (catch Exception _ nil))]
    (when-not (and (string? instance-url) (not (str/blank? instance-url)))
      (throw (ex-info ":instance-url is required" {:type ::invalid-client})))
    (when-not (and uri
                   (contains? #{"http" "https"} (.getScheme uri))
                   (.getHost uri)
                   (nil? (.getUserInfo uri))
                   (or (str/blank? (.getPath uri)) (= "/" (.getPath uri)))
                   (nil? (.getQuery uri))
                   (nil? (.getFragment uri)))
      (throw (ex-info ":instance-url must be an HTTP(S) origin without credentials"
                      {:type ::invalid-client})))
    (when (and (= "http" (.getScheme uri))
               (not (and allow-insecure? (contains? local-hosts (.getHost uri)))))
      (throw (ex-info "Salesforce instance URLs must use HTTPS"
                      {:type ::insecure-instance-url})))
    (when-not (or (and (string? access-token) (not (str/blank? access-token)))
                  (ifn? token-provider))
      (throw (ex-info ":access-token or :token-provider is required"
                      {:type ::invalid-client})))
    (merge
     (dissoc options :instance-url :instance_url :access-token :access_token
             :token-provider :api-version :http-options :headers :limit-info
             :allow-insecure?)
     {:salesforce/client true
      :instance-url (str/replace instance-url #"/+$" "")
      :api-version (or api-version default-api-version)
      :http-options (merge {:connect-timeout 10000
                            :socket-timeout 60000}
                           http-options)
      :headers headers
      :limit-info (or limit-info (atom {}))}
     (when access-token {:access-token access-token})
     (when token-provider {:token-provider token-provider}))))

(defn ensure-client
  "Returns a client for either a modern client map or a legacy auth token map."
  [value]
  (if (:salesforce/client value)
    value
    (client value)))

(defn access-token
  "Resolves a static access token or invokes the client's token provider."
  [client-value]
  (let [{:keys [token-provider] :as client} (ensure-client client-value)
        supplied (if token-provider (token-provider) (:access-token client))]
    (or (:access-token supplied)
        (:access_token supplied)
        (when (string? supplied) supplied)
        (throw (ex-info "Token provider returned no access token"
                        {:type ::invalid-token-provider})))))

(defn versioned-path
  "Builds a Salesforce data path for an explicit API version."
  [version endpoint]
  (str "/services/data/v" version
       (when-not (str/starts-with? endpoint "/") "/")
       endpoint))

(defn api-path
  "Builds a versioned Salesforce data path for a client."
  [client-value endpoint]
  (versioned-path (:api-version (ensure-client client-value)) endpoint))

(defn path-segment
  "Percent-encodes a value for use as one URL path segment."
  [value]
  (-> (URLEncoder/encode (str value) StandardCharsets/UTF_8)
      (str/replace "+" "%20")))

(defn parse-limit-info
  "Parses Sforce-Limit-Info into a map keyed by usage category."
  [header]
  (when-not (str/blank? header)
    (into {}
          (map (fn [[_ name used available]]
                 [(keyword (str/lower-case name))
                  {:used (Long/parseLong used)
                   :available (Long/parseLong available)}]))
          (re-seq #"([A-Za-z-]+)=(\d+)/(\d+)" header))))

(defn- normalize-headers [headers]
  (into {}
        (map (fn [[key value]] [(str/lower-case (name key)) value]))
        headers))

(defn- media-type [headers]
  (some-> (get headers "content-type")
          (str/split #";")
          first
          str/lower-case))

(defn- byte-array? [value]
  (= (class value) (Class/forName "[B")))

(defn- bytes->string [body]
  (if (byte-array? body)
    (String. ^bytes body StandardCharsets/UTF_8)
    (str body)))

(defn- read-limited-bytes
  [^InputStream input maximum]
  (with-open [input input
              output (ByteArrayOutputStream.)]
    (let [buffer (byte-array 8192)]
      (loop [remaining maximum]
        (when (pos? remaining)
          (let [read (.read input buffer 0 (min (alength buffer) remaining))]
            (when (pos? read)
              (.write output buffer 0 read)
              (recur (- remaining read)))))))
    (.toByteArray output)))

(defn- parse-body [body headers response-as]
  (cond
    (nil? body) nil
    (= :stream response-as) body
    (= :bytes response-as) body
    (= :text response-as) (bytes->string body)
    (and (byte-array? body) (zero? (alength ^bytes body))) nil
    (and (string? body) (str/blank? body)) nil
    (or (= :json response-as)
        (str/includes? (or (media-type headers) "") "json"))
    (json/parse-string (bytes->string body) true)
    :else body))

(defn- encode-body [{:keys [body json-body raw-body]}]
  (cond
    (some? raw-body) raw-body
    (some? json-body) (json/generate-string json-body)
    (or (map? body) (vector? body) (sequential? body))
    (json/generate-string body)
    :else body))

(defn- request-url [{:keys [instance-url]} path]
  (if (re-find #"^https?://" path)
    (let [base (URI. instance-url)
          target (URI. path)]
      (when-not (and (= (.getScheme base) (.getScheme target))
                     (= (.getHost base) (.getHost target))
                     (= (.getPort base) (.getPort target)))
        (throw (ex-info "Refusing to send a Salesforce token to another origin"
                        {:type ::cross-origin-request
                         :origin (str (.getScheme base) "://" (.getAuthority base))
                         :target-origin (str (.getScheme target) "://"
                                             (.getAuthority target))})))
      path)
    (str instance-url (when-not (str/starts-with? path "/") "/") path)))

(defn- content-type-value [content-type]
  (case content-type
    :json "application/json"
    :csv "text/csv"
    :text "text/plain"
    (when content-type (name content-type))))

(defn- error-details [body]
  (let [error (if (sequential? body) (first body) body)]
    (when (map? error)
      {:error-code (or (:errorCode error) (:error-code error))
       :message (:message error)
       :fields (:fields error)})))

(defn request-response!
  "Performs a Salesforce HTTP request and returns a response envelope.

  The result contains :status, normalized :headers, decoded :body, :request-id,
  and :limit-info. Non-2xx responses throw ExceptionInfo with the same data.

  Options support :query-params, :headers, :body/:json-body, :raw-body,
  :content-type, :accept and :as (:json, :text, :bytes or :stream)."
  ([client-value method path]
   (request-response! client-value method path nil))
  ([client-value method path options]
   (let [{:keys [http-options headers limit-info] :as client} (ensure-client client-value)
         options (or options {})
         response-as (or (:as options) :auto)
         url (request-url client path)
         content-type (or (:content-type options)
                          (when (or (contains? options :body)
                                    (contains? options :json-body))
                            "application/json"))
         request-headers (merge {"Accept" (or (:accept options) "application/json")
                                 "User-Agent" "salesforce-clj/2"}
                                headers
                                (:headers options)
                                (when content-type
                                  {"Content-Type" (content-type-value content-type)})
                                {"Authorization" (str "Bearer " (access-token client))})
         http-options (merge http-options
                             (dissoc options :as :body :json-body :raw-body
                                     :content-type :accept :headers)
                             {:method method
                              :url url
                              :headers request-headers
                              :body (encode-body options)
                              :follow-redirects false
                              :redirect-strategy :none
                              :throw-exceptions false
                              :as (if (= :stream response-as) :stream :byte-array)})
         response (try
                    (*http-request* http-options)
                    (catch Exception cause
                      (throw (ex-info "Salesforce transport request failed"
                                      {:type ::transport-error
                                       :method method
                                       :url url}
                                      cause))))
         normalized-headers (normalize-headers (:headers response))
         raw-body (if (and (= :stream response-as)
                           (not (<= 200 (or (:status response) 0) 299))
                           (instance? InputStream (:body response)))
                    (read-limited-bytes (:body response) 65536)
                    (:body response))
         parse-as (if (and (contains? #{:stream :bytes} response-as)
                           (not (<= 200 (or (:status response) 0) 299)))
                    :auto
                    response-as)
         body (try
                (parse-body raw-body normalized-headers parse-as)
                (catch Exception cause
                  (if (<= 200 (or (:status response) 0) 299)
                    (throw (ex-info "Unable to decode Salesforce response"
                                    {:type ::decode-error
                                     :status (:status response)
                                     :method method
                                     :url url
                                     :content-type (media-type normalized-headers)
                                     :body (bytes->string raw-body)}
                                    cause))
                    (bytes->string raw-body))))
         parsed-limits (parse-limit-info (get normalized-headers "sforce-limit-info"))
         envelope {:status (:status response)
                   :headers normalized-headers
                   :body body
                   :request-id (or (get normalized-headers "sforce-request-id")
                                   (get normalized-headers "x-request-id"))
                   :limit-info parsed-limits}]
     (when (and limit-info parsed-limits)
       (reset! limit-info parsed-limits))
     (if (<= 200 (or (:status response) 0) 299)
       envelope
       (throw (ex-info (or (:message (error-details body))
                           (str "Salesforce request failed with status " (:status response)))
                       (merge {:type ::api-error
                               :status (:status response)
                               :method method
                               :url url}
                              envelope
                              (error-details body))))))))

(defn request!
  "Performs a request and returns only the decoded response body."
  ([client-value method path]
   (:body (request-response! client-value method path)))
  ([client-value method path options]
   (:body (request-response! client-value method path options))))
