(ns riemann.client-test
  (:use riemann.client
        clojure.test))

(deftest send-query
         (let [c (tcp-client)
               e {:service "test" :state "ok"}]
             (send-event c {:service "test" :state "ok"})
             (let [e1 (first (query c "service = \"test\" and state = \"ok\""))]
               (is (= (:service e) (:service e1)))
               (is (= (:state e) (:state e1)))
               (is (float? (:ttl e1)))
               (is (integer? (:time e1))))))

