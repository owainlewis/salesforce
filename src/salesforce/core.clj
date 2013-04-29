(ns salesforce.core
  (:require
    [cheshire.core :as json]
    [clj-http.client :as http]))

(def ^:dynamic +token+ nil)

(defmacro with-token
  [token & body]
  `(binding [+token+ ~token]
     (do ~@body)))

;; Salesforce config variables
;; ******************************************************************************

(def ^:dynamic conf
  (ref (binding [*read-eval* false]
         (with-open [r (clojure.java.io/reader "settings.clj")]
           (read (java.io.PushbackReader. r))))))

;; Authentication and request helpers
;; ******************************************************************************

(defn auth!
  "Get security token and auth info from Salesforce
   config is a map in the form
   - client-id ID
   - client-secret SECRET
   - username USERNAME
   - password PASSWORD
   - security-token TOKEN"
  [{:keys [client-id client-secret username password security-token]}]
     (let [auth-url "https://login.salesforce.com/services/oauth2/token"
           params {:grant_type "password"
                   :client_id client-id
                   :client_secret client-secret
                   :username username
                   :password (str password security-token)
                   :format "json"}
           resp (http/post auth-url {:form-params params})]
       (-> (:body resp) (json/decode true))))

(defn ^:private request
  "Make a HTTP request to the Salesforce.com REST API"
  [method url token]
  (let [base-url (instance-url token)]
    (->
      (http/request
        {:method method
         :url (str base-url url)
         :headers {"Authorization" (str "Bearer " (:access_token token))}})
      :body
      (json/decode true))))

;; API
;; ******************************************************************************

(defn instance-url [token]
  (:instance_url token))

(defn version
  "Get the Salesforce API version"
  [token]
  (let [response (request :get "/services/data/" token)]
    (if-let [version ((comp :version first) response)]
      version)))

(defonce ^:dynamic +version+ nil)

(defmacro with-version [token & body]
  `(binding [+version+ (version ~token)]
     (do ~@body)))

(defn resources [token]
  (with-version token
    (request :get (format "/services/data/v%s/" +version+) token)))

(defn s-objects [token]
  (let [version (version token)]
    (request :get (format "/services/data/v%s/sobjects/" version) token)))

(defn s-object-names
  "Returns the name of the sobject and the url"
  [token]
  (->> (s-objects token)
       :sobjects
       (map (juxt :name (comp :sobject :urls)))))

