(defproject riemann-clojure-client "0.5.5-SNAPSHOT"
  :description "Clojure client for the Riemann monitoring system"
  :url "https://github.com/riemann/riemann-clojure-client"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[less-awful-ssl "1.0.4"]
                 [io.riemann/riemann-java-client "0.5.3"]]
  :plugins [[test2junit "1.3.3"]]
  :test2junit-output-dir "target/test2junit"
  :profiles {:dev {:dependencies [[org.clojure/clojure "1.9.0"]]}
             :bench {:dependencies [[org.clojure/clojure "1.9.0"]
                                    [criterium "0.4.4"]]
                     :jvm-opts ["-server" "-Xms1024m" "-Xmx1024m"
                                "-XX:+UseParNewGC" "-XX:+UseConcMarkSweepGC"
                                "-XX:+CMSParallelRemarkEnabled"
                                "-XX:+AggressiveOpts"
                                "-XX:+UseFastAccessorMethods"
                                "-XX:+CMSClassUnloadingEnabled"]
                     :test-paths ["bench"]}})
