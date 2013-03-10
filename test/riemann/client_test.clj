(ns riemann.client-test
  (:use riemann.client
        clojure.test))

(deftest async-test
         (let [c (tcp-client)
               e {:service "async-test" :foo "hi there"}]
           (try
             (let [res (async-send-event c e)]
               (is (= true @res))
               ; Confirm receipt
               (is (-> c
                     (query "service = \"async-test\"")
                     first
                     :foo
                     (= "hi there"))))
             (finally
               (close-client c)))))

(deftest send-query
         (let [c (tcp-client)
               e {:service "test" :state "ok"}]
           (try
             (send-event c e)
             (let [e1 (first (query c "service = \"test\" and state = \"ok\""))]
               (is (= (:service e) (:service e1)))
               (is (= (:state e) (:state e1)))
               (is (float? (:ttl e1)))
               (is (integer? (:time e1))))
             (finally
               (close-client c)))))
