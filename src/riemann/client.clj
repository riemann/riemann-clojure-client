(ns riemann.client
  "Network client for connecting to a Riemann server. Usage:

  (def c (tcp-client :host \"monitoring.local\"))

  (send-event c {:service \"fridge\"
                 :state \"running\"
                 :metric 2.0
                 :tags [\"joke\"]})

  (query c \"tagged \\\"joke\\\"\")
  => [{:service \"fridge\" ... }]

  (close-client c)

  Clients are resistant to failure; they will attempt to reconnect a dropped
  connection periodically. Note that clients will not automatically queue or
  retry failed sends."
  (:import (com.aphyr.riemann.client RiemannBatchClient
                                     RiemannClient
                                     AsynchronizeTransport
                                     AbstractRiemannClient
                                     TcpTransport
                                     UdpTransport
                                     SSL)
           (java.util List)
           (clojure.lang IDeref)
           (java.net InetSocketAddress)
           (java.io IOException))
  (:use riemann.codec)
  (:use clojure.tools.logging))

(defn query
  "Query the server for events in the index. Returns a list of events."
  [^AbstractRiemannClient client string]
  (map decode-pb-event (.query client string)))

(defn async-send-msg
  "Send a message to the server, asynchronously. Returns an IDeref which can be
  resolved to a response Msg."
  [^AbstractRiemannClient client msg]
  (.aSendRecvMessage client msg))

(defn async-send-events
  "Sends several events, asynchronously, over client. Returns an IDeref which
  can be resolved to a response Msg."
  [^AbstractRiemannClient client events]
  (let [^List events (map encode-client-pb-event events)]
    (.aSendEventsWithAck client events)))

(defn send-events
  "Send several events over client. Requests acknowledgement from the Riemann
  server by default. If ack is false, sends in fire-and-forget mode."
  ([client events]
   (send-events client events true))
  ([^AbstractRiemannClient client events ack]
   (let [^List events (map encode-client-pb-event events)]
     (if ack
       (.sendEventsWithAck client events)
       (.sendEvents client events)))))

(defn async-send-event
  "Sends a single event, asynchronously, over client. Returns an IDeref which
  can be resolved to a response Msg."
  [^AbstractRiemannClient client event]
  (.aSendEventsWithAck client ^List (list (encode-client-pb-event event))))

(defn send-event
  "Send an event over client. Requests acknowledgement from the Riemann
    server by default. If ack is false, sends in fire-and-forget mode."
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
  [^AbstractRiemannClient client]
  (.reconnect client))

(defn batch
  "Wraps a client in a RiemannBatchClient, with batch size n."
  ([client] (batch 10 client))
  ([n ^AbstractRiemannClient client]
   (RiemannBatchClient. n client)))

(defn tcp-client
  "Creates a new TCP client. Options:

  :host       The host to connect to
  :port       The port to connect to
  
  :tls?       Whether to use TLS when connecting
  :key        A PKCS8 key file
  :cert       A PEM certificate
  :ca-cert    The signing cert for our certificate and the server's
  
  Example:

  (tcp-client)
  (tcp-client :host \"foo\" :port 5555)"
  [& { :keys [^String host ^Integer port
              tls? ^String key ^String cert ^String ca-cert]
       :or {host "localhost"}
       :as opts}]

  ; Check options
  (when tls?
    (assert key)
    (assert cert)
    (assert ca-cert))

  ; Create client
  (let [port   (or port (if tls? 5554 5555))
        client (if tls?
                 ; TLS client
                 (RiemannClient.
                   (doto (TcpTransport. host port)
                     (-> .sslContext
                       (.set (SSL/sslContext key cert ca-cert)))))
                     
                 ; Standard client
                 (RiemannClient/tcp host port))]

    ; Attempt to connect lazily.
    (try (connect-client client)
      (catch IOException e nil))
    client))

(defn udp-client
  "Creates a new UDP client. Can take an optional maximum message size. Example:
  (udp-client)
  (udp-client :host \"foo\" :port 5555 :max-size 16384)"
  [& {:keys [^String host ^Integer port ^Integer max-size]
      :or {port 5555
           host "localhost"
           max-size 16384}
      :as opts}]
  (let [c (RiemannClient.
          (doto (UdpTransport. host port)
            (-> .sendBufferSize (.set max-size))))]
    (try (connect-client c) (catch IOException e nil))
    c))

(defn multi-client
  "Creates a new multiclient from n clients"
  [clients]
  (let [clients (vec clients)
        n       (count clients)
        c       (fn choose-client []
                  (clients (mod (.getId (Thread/currentThread)) n)))]
    (proxy [AbstractRiemannClient] []
;      (sendMessage [this msg] (throw))
;      (recvMessage [this] (throw))
      (aSendRecvMessage [msg] (.aSendRecvMessage
                                ^AbstractRiemannClient (c) msg))
      (aSendMaybeRecvMessage [msg] (.aSendMaybeRecvMessage 
                                    ^AbstractRiemannClient (c) msg))
      (sendRecvMessage [msg] (.sendRecvMessage 
                               ^AbstractRiemannClient (c) msg))
      (sendMaybeRecvMessage [msg] (.sendMaybeRecvMessage 
                                    ^AbstractRiemannClient (c) msg))
      (connect [] (doseq [client clients] (.connect
                                            ^AbstractRiemannClient client)))
      (disconnect [] (doseq [client clients] (.disconnect
                                              ^AbstractRiemannClient client))))))
