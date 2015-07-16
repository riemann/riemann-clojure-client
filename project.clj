(defproject riemann-clojure-client "0.4.1"
  :description "Clojure client for the Riemann monitoring system"
  :url "https://github.com/aphyr/riemann-clojure-client"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/tools.logging "0.2.3"]
                 [less-awful-ssl "1.0.0"]
                 [com.aphyr/riemann-java-client "0.4.0"]]
  :profiles {:dev {:dependencies [[org.clojure/clojure "1.6.0"]]}
             :bench {:dependencies [[org.clojure/clojure "1.6.0"]
                                    [criterium "0.4.1"]]
                     :jvm-opts ["-server" "-Xms1024m" "-Xmx1024m"
                                "-XX:+UseParNewGC" "-XX:+UseConcMarkSweepGC"
                                "-XX:+CMSParallelRemarkEnabled"
                                "-XX:+AggressiveOpts"
                                "-XX:+UseFastAccessorMethods"
                                "-XX:+CMSClassUnloadingEnabled"]
                     :test-paths ["bench"]}})
