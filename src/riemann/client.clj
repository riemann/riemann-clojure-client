(ns riemann.client
  "Network client for connecting to a Riemann server. Usage:
  
  (def c (tcp-client :host \"monitoring.local\"))
 
  (send-event c {:service \"fridge\" 
                 :state \"running\" 
                 :metric 2.0
                 :tags [\"joke\"]})

  (query c \"tagged \\\"joke\\\"\")
  => [{:service \"fridge\" ... }]

  (close c)

  Clients are mildly resistant to failure; they will attempt to reconnect a
  dropped connection once before giving up. They're backed by a
  RiemannRetryingTcpClient."

  (:import [com.aphyr.riemann.client RiemannThreadedClient
                                     RiemannRetryingTcpClient
                                     RiemannTcpClient 
                                     RiemannUDPClient 
                                     AbstractRiemannClient]
           [java.net InetSocketAddress]
           [java.io IOException])
  (:use riemann.codec)
  (:use clojure.tools.logging))

(defn query
  "Query the server for events in the index. Returns a list of events."
  [^AbstractRiemannClient client string]
  (map decode-pb-event (.query client string)))

(defn send-event
  "Send an event over client."
  ([client event]
   (send-event client event true))
  ([^AbstractRiemannClient client event ack]
   (let [events (into-array com.aphyr.riemann.Proto$Event
                            (list (encode-pb-event event)))]
     (if ack
       (.sendEventsWithAck client events)
       (.sendEvents client events)))))

(defn tcp-client
  "Creates a new threaded TCP client. Example:

  (tcp-client)
  (tcp-client :host \"foo\" :port 5555)"
  [& { :keys [^String host ^Integer port]
       :or {port 5555
            host "localhost"}
       :as opts}]
  (doto (RiemannThreadedClient.
          (RiemannTcpClient. (InetSocketAddress. host port)))
    (.connect)))

(defn retrying-tcp-client
  "Create a new TCP client. Example:

  (tcp-client)
  (tcp-client :host \"foo\" :port 5555)"
  [& { :keys [^String host ^Integer port]
       :or {port 5555
            host "localhost"}
       :as opts}]
  (doto (RiemannRetryingTcpClient. (InetSocketAddress. host port))
    (.connect)))

(defn udp-client
  "Create a new UDP client. Example:

  (udp-client)
  (udp-client :host \"foo\" :port 5555)"
  [& {:keys [^String host ^Integer port]
      :or {port 5555
            host "localhost"}
      :as opts}]
  (doto (RiemannUDPClient. (InetSocketAddress. host port))
    (.connect)))

(defn close-client
  "Close a client."
  [^AbstractRiemannClient client]
  (.disconnect client))

(defn reconnect-client
  "Reconnect a client."
  [client]
  (.reconnect client))
