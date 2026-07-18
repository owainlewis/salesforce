(ns salesforce.api
  "Generic access to every Salesforce REST resource plus API discovery helpers."
  (:refer-clojure :exclude [identity])
  (:require
   [salesforce.client :as client]))

(defn request-response!
  "Calls any endpoint under /services/data/v{client-version}."
  ([client-value method endpoint]
   (request-response! client-value method endpoint nil))
  ([client-value method endpoint options]
   (client/request-response! client-value method
                             (client/api-path client-value endpoint)
                             options)))

(defn request!
  "Calls any versioned REST endpoint and returns its decoded body."
  ([client-value method endpoint]
   (:body (request-response! client-value method endpoint)))
  ([client-value method endpoint options]
   (:body (request-response! client-value method endpoint options))))

(defn raw-request-response!
  "Calls an instance-relative or absolute REST path without adding an API prefix.
  This is useful for opaque pagination URLs, Apex REST and product APIs."
  ([client-value method path]
   (client/request-response! client-value method path))
  ([client-value method path options]
   (client/request-response! client-value method path options)))

(defn raw-request!
  ([client-value method path]
   (:body (raw-request-response! client-value method path)))
  ([client-value method path options]
   (:body (raw-request-response! client-value method path options))))

(defn versions [client-value]
  (raw-request! client-value :get "/services/data/"))

(defn latest-version [client-value]
  (:version (last (versions client-value))))

(defn resources [client-value]
  (request! client-value :get "/"))

(defn limits [client-value]
  (request! client-value :get "/limits"))

(defn identity [client-value]
  (raw-request! client-value :get "/services/oauth2/userinfo"))
