(defproject com.owainlewis/salesforce "1.0.2"
  :description "A Clojure library for accessing the Salesforce API"
  :url "https://github.com/owainlewis/salesforce"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.10.2"]
                 [clj-http "3.10.3"]
                 [cheshire "5.10.0"]]
  :deploy-repositories
  [["clojars"
    {:url "https://repo.clojars.org"
     :username :env/clojars_username
     :password :env/clojars_password
     :sign-releases false}]]
  :profiles {:dev {:plugins [[lein-cljfmt "0.7.0"]]
                   :global-vars {*warn-on-reflection* true}}})
