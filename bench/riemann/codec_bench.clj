(ns riemann.codec-bench
  (:require criterium.core
            [riemann.codec :refer :all]
            [clojure.test :refer :all])
  (:import [riemann.codec Msg Event Query]))

(defn msg
  "Map to Msg"
  [m]
  (-> m
    (assoc :events (map map->Event (:events m)))
    (map->Msg)))

(deftest protobufs-bench
  (println "deserialize\n")
  (let [m (msg {:events [{:host "a"
                          :service "b"
                          :state "c"
                          :description "yo"
                          :metric (float 1.8)
                          :tags ["a" "b" "c"]
                          :time 12345
                          :ttl (float 10)}]})
        serialized (encode-pb-msg m)]
    (criterium.core/bench (decode-pb-msg serialized)))

  (println "serialize\n")
  (let [m (msg {:events [{:host "a"
                          :service "b"
                          :state "c"
                          :description "yo"
                          :metric (float 1.8)
                          :tags ["a" "b" "c"]
                          :time 12345
                          :ttl (float 10)}]}) ]
    (criterium.core/bench (encode-pb-msg m)))

  (println "roundtrip\n")
  (let [roundtrip (fn [m] (let [m' (-> m encode-pb-msg decode-pb-msg)]))
        m (msg {:events [{:host "a"
                          :service "b"
                          :state "c"
                          :description "yo"
                          :metric (float 1.8)
                          :tags ["a" "b" "c"]
                          :time 12345
                          :ttl (float 10)}]})]
    (criterium.core/bench (roundtrip m))))
