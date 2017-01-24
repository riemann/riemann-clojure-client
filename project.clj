(defproject riemann-clojure-client "0.4.4"
  :description "Clojure client for the Riemann monitoring system"
  :url "https://github.com/aphyr/riemann-clojure-client"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[less-awful-ssl "1.0.0"]
                 [io.riemann/riemann-java-client "0.4.3"]]
  :profiles {:dev {:dependencies [[org.clojure/clojure "1.8.0"]]}
             :bench {:dependencies [[org.clojure/clojure "1.8.0"]
                                    [criterium "0.4.1"]]
                     :jvm-opts ["-server" "-Xms1024m" "-Xmx1024m"
                                "-XX:+UseParNewGC" "-XX:+UseConcMarkSweepGC"
                                "-XX:+CMSParallelRemarkEnabled"
                                "-XX:+AggressiveOpts"
                                "-XX:+UseFastAccessorMethods"
                                "-XX:+CMSClassUnloadingEnabled"]
                     :test-paths ["bench"]}})
