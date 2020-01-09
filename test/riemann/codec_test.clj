(ns riemann.codec-test
  (:use riemann.codec
        clojure.test)
  (:import [riemann.codec Msg Event Query]))

(defn msg-equal?
  [m1 m2]
  (prn "Compare" m1 m2)
  (cond
    (map? m1)
      (every? (fn [k] (msg-equal? (m1 k) (m2 k)))
              (into #{} (concat (keys m1) (keys m2))))

    (coll? m1)
    (every? identity
            (map msg-equal? m1 m2))

    true
    (do (prn (class m1) (class m2))
        (= m1 m2))))

(defn msg
  "Map to Msg"
  [m]
  (-> m
    (assoc :events (map map->Event (:events m)))
    (map->Msg)))

(defn roundtrip
  [m]
  (let [m' (-> m
               encode-pb-msg
               decode-pb-msg
               (assoc :decode-time nil))]
    ;; Ignore automatically provided times.
    (->> (:events m')
         (map (fn [e e']
                (if (:time e)
                  e'
                  (assoc e' :time nil)))
              (:events m))
         (assoc m' :events))))

(deftest protobufs
  (are [m] (= (msg m) (roundtrip m))
                {}
                {:ok true}
                {:ok false}
                {:error "foo"}
                {:query (Query. "hi")}
                {:ok false :error "hi" :query (Query. "yo")}
                {:events [{}]}
                {:events [{:host "sup"}]}
                ; Longs
                {:events [{:metric Long/MIN_VALUE}]}
                {:events [{:metric 0}]}
                {:events [{:metric Long/MAX_VALUE}]}
                ; Doubles
                {:events [{:metric 55555555.5555555555}]}
                {:events [{:metric Double/MIN_VALUE}]}
                {:events [{:metric Double/MAX_VALUE}]}
                ; Floats
                {:events [{:metric (float 5555555.555555555)}]}
                {:events [{:metric Float/MIN_VALUE}]}
                {:events [{:metric Float/MAX_VALUE}]}
                {:events [{:host "a"
                           :service "b"
                           :state "c"
                           :description "yo"
                           :metric (float 1.8)
                           :tags ["a" "b" "c"]
                           :time 12345.0
                           :ttl (float 10)}]}
                ; Custom attributes
                {:events [{:host "a"
                           :service "b"
                           :state "c"
                           :description "yo"
                           :metric (float 1.8)
                           :key1 "value1"
                           :key2 "value2"
                           :time 12345.0
                           :ttl (float 10)
                           :my-ns/key3 "value3"}]})

  ; Test time
  (are [map event] (= (decode-pb-event-record (encode-pb-event map)) event)
    {:host "sup" :time 1} (map->Event {:host "sup" :time 1.0})
    {:host "sup" :time 100.500} (map->Event {:host "sup" :time 100.500}))

  ; Test encoding
  (testing "Protobuf encoding"
    (let [proto-event (encode-pb-event {:host "sup" :time 1})]
      (is (= (.getHost proto-event) "sup"))
      (is (= (.getTime proto-event) 1))
      (is (= (.getTimeMicros proto-event) 1000000)))
    (let [proto-event (encode-pb-event {:host "sup" :time 100.500})]
      (is (= (.getHost proto-event) "sup"))
      (is (= (.getTime proto-event) 100))
      (is (= (.getTimeMicros proto-event) 100500000))))

  (let [m (msg {:events [{:host "a"
                          :service "b"
                          :state "c"
                          :description "yo"
                          :metric (float 1.8)
                          :tags ["a" "b" "c"]
                          :time 12345
                          :ttl (float 10)}]})]
             ;(time (dotimes [n 10000000]
                     ;(roundtrip m))))))
             ))

(deftest nil-custom-attributes-should-be-removed-during-serialization
  (let [original {:host "somehost"
                  :service "someservice"
                  :empty-attr nil}
        expected (dissoc original :empty-attr)]
    (is (= (msg {:events [expected]})
           (roundtrip {:events [original]})))))

(deftest false-custom-attributes
  (let [original {:host "host.1"
                  :service "service.1"
                  :nil-attr nil
                  :false-attr false}
        expected (-> original
                     (dissoc :nil-attr)
                     (assoc :false-attr "false"))]
    (is (= (msg {:events [expected]})
           (roundtrip {:events [original]}))
        "false values should be converted to string and not be removed")))
