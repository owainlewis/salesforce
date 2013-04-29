(ns salesforce.core
  (:require
    [cheshire.core :as json]
    [clj-http.client :as http]))

(def ^:dynamic +token+ nil)

(defmacro with-token
  [token & body]
  `(binding [+token+ ~token]
     (do ~@body)))

(defn auth!
  "Get security token"
  [{:keys [client-id client-secret username password security-token]}]
     (let [params {:grant_type "password"
                   :client_id client-id
                   :client_secret client-secret
                   :username username
                   :password (str password security-token)
                   :format "json"}
           resp (http/post "https://login.salesforce.com/services/oauth2/token" {:form-params params :as :json})]
       (:body resp)))

(defn request [method url token]
  (let [base-url (instance-url token)]
    (->
      (http/request
        {:method method
         :url (str base-url url)
         :headers {"Authorization" (str "Bearer " (:access_token token))}})
      :body
      (json/decode true))))

(defn instance-url [token]
  (:instance_url token))

(defn version
  "Get the Salesforce API version"
  [token]
  (let [response (request :get "/services/data/" token)]
    (if-let [version ((comp :version first) response)]
      version)))

(defn resources [token]
  (request :get "/services/data/v20.0/" token))

(defn s-objects [token]
  (let [version (version token)]
    (request :get (format "/services/data/v%s/sobjects/" version) token)))

(defn s-object-names
  "Returns the name of the sobject and the url"
  [token]
  (->> (s-objects token)
       :sobjects
       (map (juxt :name (comp :sobject :urls)))))

