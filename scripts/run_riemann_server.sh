#!/bin/bash

wget https://github.com/riemann/riemann/releases/download/0.3.3/riemann-0.3.3-standalone.jar

cat << EOF > ./riemann.config
; -*- mode: clojure; -*-
; vim: filetype=clojure

(logging/init {:file "riemann.log"})

(tcp-server {:tls? false
  :key "test/data/tls/server.pkcs8"
  :cert "test/data/tls/server.crt"
  :ca-cert "test/data/tls/demoCA/cacert.pem"})

(instrumentation {:interval 1})

(udp-server)
(ws-server)
(graphite-server)

(periodically-expire 1)

(let [index (tap :index (index))]
  (streams
    (default :ttl 3
      (expired #(prn "Expired" %))
      (where (not (service #"^riemann "))
          index))))
(tests
  (deftest index-test
    (is (= (inject! [{:service "test"
                      :time    1}])
            {:index [{:service "test"
                      :time    1
                      :ttl     3}]}))))
EOF

java -jar riemann-0.3.3-standalone.jar

