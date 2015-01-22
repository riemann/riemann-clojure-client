(defproject riemann-clojure-client "0.3.2"
  :description "Clojure client for the Riemann monitoring system"
  :url "https://github.com/aphyr/riemann-clojure-client"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/tools.logging "0.2.3"]
                 [less-awful-ssl "1.0.0"]
                 [com.aphyr/riemann-java-client "0.3.1"]]
  :profiles {:dev {:dependencies [[org.clojure/clojure "1.6.0"]]}})
