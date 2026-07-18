(defproject com.owainlewis/salesforce "2.0.0-SNAPSHOT"
  :description "A modern Clojure client for Salesforce Platform REST APIs"
  :url "https://github.com/owainlewis/salesforce"
  :license {:name "Eclipse Public License"
            :url "https://www.eclipse.org/legal/epl-v10.html"}
  :scm {:name "git"
        :url "https://github.com/owainlewis/salesforce"
        :connection "scm:git:https://github.com/owainlewis/salesforce.git"
        :developer-connection "scm:git:ssh://git@github.com/owainlewis/salesforce.git"
        :tag "HEAD"}
  :min-lein-version "2.11.2"
  :dependencies [[org.clojure/clojure "1.11.4"]
                 [clj-http "3.13.1"]
                 [cheshire "6.2.0"]]
  :deploy-repositories
  [["clojars"
    {:url "https://repo.clojars.org"
     :username :env/clojars_username
     :password :env/clojars_password
     :sign-releases false}]]
  :profiles {:dev {:plugins [[lein-cljfmt "0.9.2"]]
                   :global-vars {*warn-on-reflection* true}}
             :clojure-1.12 {:dependencies [[org.clojure/clojure "1.12.5"]]}}
  :aliases {"ci" ["do" "check," "test," "cljfmt" "check"]})
