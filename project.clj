(defproject lsevero/salesforce "1.1.0-SNAPSHOT"
  :description "A clojure library for accessing the salesforce api"
  :url "https://github.com/lsevero/salesforce"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.10.2"]
                 [clj-http "3.10.3"]
                 [cheshire "5.10.0"]]
  :profiles {:dev {:plugins [[cider/cider-nrepl "0.22.3"]]
                   :global-vars {*warn-on-reflection* true}
                   }})
