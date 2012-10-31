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

(deftest protobufs
         (let [roundtrip (comp decode-pb-msg encode-pb-msg)]
           (are [m] (= (msg m) (roundtrip m))
                {}
                {:ok true}
                {:ok false}
                {:error "foo"}
                {:query (Query. "hi")}
                {:ok false :error "hi" :query (Query. "yo")}
                {:events [{}]}
                {:events [{:host "sup"}]}
                {:events [{:host "a"
                           :service "b"
                           :state "c"
                           :description "yo"
                           :metric (float 1.8)
                           :tags ["a" "b" "c"]
                           :time 12345
                           :ttl (float 10)}]})

           (let [m (msg {:events [{:host "a"
                                   :service "b"
                                   :state "c"
                                   :description "yo"
                                   :metric (float 1.8)
                                   :tags ["a" "b" "c"]
                                   :time 12345
                                   :ttl (float 10)}]})]
;             (time (dotimes [n 10000000] 
;                     (roundtrip m)))))) 
             )))
