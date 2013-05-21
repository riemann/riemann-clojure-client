(ns riemann.codec
  "Encodes and decodes Riemann messages and events, between byte arrays,
  buffers, and in-memory types."
  (:require clojure.set)
  (:import [com.aphyr.riemann Proto$Query Proto$Attribute Proto$Event Proto$Msg]
           [java.net InetAddress]
           [com.google.protobuf ByteString]))

(defrecord Query [string])
(defrecord Event [host service state description metric tags time ttl])
(defrecord Msg [ok error events query])

(def event-keys (set (map keyword (Event/getBasis))))

(defn assoc-default
  "Like assoc, but only alters the map if it does not already contain the given
  key.
  
  (assoc-default {} :foo 2)         ; {:foo 2}
  (assoc-default {:foo nil} :foo 2) ; {:foo nil}"
  [m k v]
  (if (contains? m k)
    m
    (assoc m k v)))

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

(defn decode-pb-event-record
  "Transforms a java protobuf to an Event."
  [^Proto$Event e]
  (Event.
    (when (.hasHost e) (.getHost e))
    (when (.hasService e) (.getService e))
    (when (.hasState e) (.getState e))
    (when (.hasDescription e) (.getDescription e))
    (cond
      (.hasMetricSint64 e) (.getMetricSint64 e)
      (.hasMetricD e)      (.getMetricD e)
      (.hasMetricF e)      (.getMetricF e))
    (when (< 0 (.getTagsCount e)) (vec (.getTagsList e)))
    (when (.hasTime e) (.getTime e))
    (when (.hasTtl e) (.getTtl e))))

(defn decode-pb-event
  [^Proto$Event e]
  (let [event (decode-pb-event-record e)]
    (if (< 0 (.getAttributesCount e))
      (into event (map (fn [^Proto$Attribute a] 
                         [(keyword (.getKey a)) (.getValue a)]) 
                       (.getAttributesList e)))
      event)))

(defn encode-pb-event
  "Transforms a map to a java protobuf Event."
  [e]
  (let [event (Proto$Event/newBuilder)]
    (when (:host e)         (.setHost         event (:host e)))
    (when (:service e)      (.setService      event (:service e)))
    (when (:state e)        (.setState        event (:state e)))
    (when (:description e)  (.setDescription  event (:description e)))
    (when-let [m (:metric e)]
      ; For backwards compatibility, always construct floats.
      (try (.setMetricF event (float m)) (catch Throwable _))
      (if (and (integer? m) (<= Long/MIN_VALUE m Long/MAX_VALUE))
        (.setMetricSint64 event (long m))
        (.setMetricD event (double m))))
    (when (:tags e)         (.addAllTags      event (:tags e)))
    (when (:time e)         (.setTime         event (long (:time e))))
    (when (:ttl e)          (.setTtl          event (:ttl e)))
    (doseq [k (clojure.set/difference (set (keys e)) event-keys)]
      (let [a (Proto$Attribute/newBuilder)]
        (.setKey a (name k))
        (.setValue a (get e k))
        (.addAttributes event a)))
    (.build event)))

(defn encode-client-pb-event
  "Clients usually fill in an event's local host and time automatically, if
  not explicitly specified."
  [e]
  (-> e
    (assoc-default :time (/ (System/currentTimeMillis) 1000))
    (assoc-default :host (.. InetAddress getLocalHost getHostName))
    encode-pb-event))

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
