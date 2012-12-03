(ns riemann.codec
  "Encodes and decodes Riemann messages and events, between byte arrays,
  buffers, and in-memory types."
  (:import [com.aphyr.riemann Proto$Query Proto$Event Proto$Msg]
           [com.google.protobuf ByteString]))

(defrecord Query [string])
(defrecord Event [host service state description metric tags time ttl])
(defrecord Msg [ok error events query])

(defn decode-pb-query
  "Transforms a java protobuf Query to a Query."
  [^Proto$Query q]
  (Query. (.getString q)))

(defn encode-pb-query
  "Transforms a Query to a protobuf Query."
  (^Proto$Query [query]
     (-> (Proto$Query/newBuilder)
       (.setString (:string query))
       (.build))))

(defn decode-pb-event
  "Transforms a java protobuf to an Event."
  [^Proto$Event e]
  (Event.
    (when (.hasHost e) (.getHost e))
    (when (.hasService e) (.getService e))
    (when (.hasState e) (.getState e))
    (when (.hasDescription e) (.getDescription e))
    (cond
      (.hasMetricF e)      (.getMetricF e)
      (.hasMetricD e)      (.getMetricD e)
      (.hasMetricSint64 e) (.getMetricSint64 e)
      (.hasMetricBigint e) (-> e (.getMetricBigint) (.toByteArray) (bigint)))
    (when (< 0 (.getTagsCount e)) (vec (.getTagsList e)))
    (when (.hasTime e) (.getTime e))
    (when (.hasTtl e) (.getTtl e))))

(defn encode-pb-event
  "Transforms a map to a java protobuf Event."
  [e]
  (let [event (Proto$Event/newBuilder)]
    (when (:host e)         (.setHost         event (:host e)))
    (when (:service e)      (.setService      event (:service e)))
    (when (:state e)        (.setState        event (:state e)))
    (when (:description e)  (.setDescription  event (:description e)))
    (when-let [m (:metric e)]
      (cond
        (instance? Double m)  (.setMetricD event m)
        (instance? Float m)   (.setMetricF event m)
        (ratio? m)            (.setMetricD event (double m))

        (instance? clojure.lang.BigInt m)
          ; LMAO if a lack of immutability means mandatory defensive copies
          ; at every layer
          (.setMetricBigint event (ByteString/copyFrom
                                    (.toByteArray
                                      (.toBigInteger m))))

        (instance? java.math.BigInteger m)
          (.setMetricBigint event (ByteString/copyFrom (.toByteArray m)))
        
        (integer? m)          (.setMetricSint64 event (long m))
        true (throw (RuntimeException. 
                      (str "Unknown metric type" (class m))))))
    (when (:tags e)         (.addAllTags      event (:tags e)))
    (when (:time e)         (.setTime         event (long (:time e))))
    (when (:ttl e)          (.setTtl          event (:ttl e)))
    (.build event)))

(defn decode-pb-msg
  "Transforms a java protobuf Msg to a Msg."
  [^Proto$Msg m]
  (Msg. (when (.hasOk m) (.getOk m))
        (when (.hasError m) (.getError m))
        (map decode-pb-event (.getEventsList m))
        (when (.hasQuery m) (decode-pb-query (.getQuery m)))))

(defn encode-pb-msg
  "Transform a map to a java probuf Msg."
  [m]
  (let [msg (Proto$Msg/newBuilder)]
    (when-let [events (:events m)] 
      (.addAllEvents msg (map encode-pb-event events)))
    (when-not (nil? (:ok m)) (.setOk msg (:ok m)))
    (when (:error m) (.setError msg (:error m)))
    (when (:query m) (.setQuery msg (encode-pb-query (:query m))))
    (.build msg)))
