(ns riemann.client-test
  (:import java.net.InetAddress
           com.aphyr.riemann.client.OverloadedException)
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

(deftest load-shedding-test
  (let [c (tcp-client)
        e {:service "overload-test" :ttl 10 :description (apply str (repeat 1e2 "x"))}]
    (try
      (let [results (->> (repeat 1e5 e)
                         (pmap (partial async-send-event c))
                         doall)
            ok      (->> results
                         (map #(try (deref %) 1
                                    (catch OverloadedException e 0)))
                         (reduce +))]
        (is (< ok (count results))))
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

(deftest default-time
         (let [c (tcp-client)]
           (testing "undefined time"
                    (let [t1 (quot (System/currentTimeMillis) 1000)
                          _ (send-event c {:service "test-no-time"})
                          t2 (quot (System/currentTimeMillis) 1000)
                          t  (-> c
                                 (query "service = \"test-no-time\"")
                                 first
                                 :time)]
                    (is (<= t1 t t2))))

           (testing "with an explicitly nil time"
                    (send-event c {:service "test-nil-time" :time nil})
                    ; Server should fill it in
                    (is (number? (-> c (query "service = \"test-nil-time\"")
                                   first
                                   :time))))

           (testing "with a given time"
                    (send-event c {:service "test-given-time" :time 1234})
                    (is (= 1234
                           (-> c (query "service = \"test-given-time\"")
                             first
                             :time))))))

(deftest default-host
         (let [c (tcp-client)]
           (testing "undefined host"
                    (send-event c {:service "test-no-host" :state "ok"})
                    (is (= (.. InetAddress getLocalHost getHostName)
                           (-> c (query "service = \"test-no-host\"")
                             first
                             :host))))

           (testing "with an explicitly nil host"
                    (send-event c {:service "test-nil-host" :host nil})
                    (is (nil? (-> c (query "service = \"test-nil-host\"")
                                first
                                :host))))

           (testing "with a given host"
                    (send-event c {:service "test-given-host" :host "foo"})
                    (is (= "foo"
                           (-> c (query "service = \"test-given-host\"")
                             first
                             :host))))))
