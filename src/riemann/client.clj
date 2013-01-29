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

  (:import (com.aphyr.riemann.client RiemannThreadedClient
                                     RiemannRetryingTcpClient
                                     RiemannBatchClient
                                     RiemannTcpClient 
                                     RiemannUDPClient 
                                     AbstractRiemannClient)
           [java.net InetSocketAddress]
           [java.io IOException])
  (:use riemann.codec)
  (:use clojure.tools.logging))

(defn query
  "Query the server for events in the index. Returns a list of events."
  [^AbstractRiemannClient client string]
  (map decode-pb-event (.query client string)))

(defn send-events
  "Send several events over client."
  ([client events]
   (send-events client events true))
  ([^AbstractRiemannClient client events ack]
   (let [events (map encode-pb-event events)]
     (if ack
       (.sendEventsWithAck client events)
       (.sendEvents client events)))))

(defn send-event
  "Send an event over client."
  ([client event]
   (send-event client event true))
  ([^AbstractRiemannClient client event ack]
   (send-events client (list event) ack)))

(defn connect-client
  "Connect a client."
  [^AbstractRiemannClient client]
  (.connect client))

(defn close-client
  "Close a client."
  [^AbstractRiemannClient client]
  (.disconnect client))

(defn reconnect-client
  "Reconnect a client."
  [client]
  (.reconnect client))

(defn batch
  "Wraps a client in a RiemannBatchClient, with batch size n."
  ([client] (batch 10 client))
  ([n ^AbstractRiemannClient client]
   (RiemannBatchClient. n client)))

(defn threaded-tcp-client
  "Creates a new threaded TCP client. Example:

  (threaded-tcp-client)
  (threaded-tcp-client :host \"foo\" :port 5555)"
  [& { :keys [^String host ^Integer port]
       :or {port 5555
            host "localhost"}
       :as opts}]
  (let [c (RiemannThreadedClient.
            (RiemannTcpClient. (InetSocketAddress. host port)))]
    (try (connect-client c) (catch IOException e nil))
    c))

(defn retrying-tcp-client
  "Create a new TCP client. Example:

  (tcp-client)
  (tcp-client :host \"foo\" :port 5555)"
  [& { :keys [^String host ^Integer port]
       :or {port 5555
            host "localhost"}
       :as opts}]
  (doto (RiemannRetryingTcpClient. (InetSocketAddress. host port))
    (connect-client)))

(def tcp-client retrying-tcp-client)

(defn udp-client
  "Create a new UDP client. Example:

  (udp-client)
  (udp-client :host \"foo\" :port 5555)"
  [& {:keys [^String host ^Integer port]
      :or {port 5555
            host "localhost"}
      :as opts}]
  (doto (RiemannUDPClient. (InetSocketAddress. host port))
    (connect-client)))

(defn multi-client
  "Creates a new multiclient from n clients"
  [clients]
  (let [clients (vec clients)
        i       (atom 0)
        n       (count clients)
        c       (fn next-client [] (clients (swap! i #(mod (inc %) n))))]
    (proxy [AbstractRiemannClient] []
;      (sendMessage [this msg] (throw))
;      (recvMessage [this] (throw))
      (sendRecvMessage [msg] (.sendRecvMessage (c) msg))
      (sendMaybeRecvMessage [msg] (.sendMaybeRecvMessage (c) msg))
      (connect [] (doseq [client clients] (.connect client)))
      (disconnect [] (doseq [client clients] (.disconnect client))))))
