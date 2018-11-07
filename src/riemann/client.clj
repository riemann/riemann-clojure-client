(ns riemann.client
  "Network client for connecting to a Riemann server. Usage:

  (def c (tcp-client {:host \"monitoring.local\"}))

  (send-event c {:service \"fridge\"
                 :state \"running\"
                 :metric 2.0
                 :tags [\"joke\"]})
  => #<com.riemann.riemann.client.MapPromise ...>

  @(query c \"tagged \\\"joke\\\"\")
  => [{:service \"fridge\" ... }]

  (close! c)

  Clients are resistant to failure; they will attempt to reconnect a dropped
  connection periodically. Note that clients will not automatically queue or
  retry failed sends."
  (:import (io.riemann.riemann.client RiemannBatchClient
                                      RiemannClient
                                      MapPromise
                                      IPromise
                                      Fn2
                                      Transport
                                      AsynchronousTransport
                                      IRiemannClient
                                      TcpTransport
                                      UdpTransport
                                      SSL)
           (java.util List)
           (clojure.lang IDeref)
           (java.net InetSocketAddress)
           (java.io IOException))
  (:require [less.awful.ssl :as ssl])
  (:use riemann.codec))

(defn map-promise
  "Maps a riemann client promise by applying a function."
  [^IPromise p f]
  (.map p (reify Fn2 (call [_ x] (f x)))))

(defn send-msg
  "Send a message to the server, asynchronously. Returns an IDeref which can be
  resolved to a response message."
  [^AsynchronousTransport client msg]
  (-> client
      (.sendMessage (encode-pb-msg msg))
      (map-promise decode-pb-msg)))

(defn query
  "Query the server for events in the index. Returns a list of events."
  [^IRiemannClient client string]
  (-> client
      (.query string)
      (map-promise (partial map decode-pb-event))))

(defn send-events
  "Sends several events, asynchronously, over client. Returns an IDeref which
  can be resolved to a response message."
  [^IRiemannClient client events]
  (-> client
      (.sendEvents ^List (map encode-client-pb-event events))
      (map-promise decode-pb-msg)))

(defn send-event
  "Sends a single event, asynchronously, over client. Returns an IDeref which
  can be resolved to a response message."
  [^IRiemannClient client event]
  (-> client
      (.sendEvent (encode-client-pb-event event))
      (map-promise decode-pb-msg)))

(defn send-exception
  "Send an exception, asynchronously, over client. Uses (:name service) as the
  service. Returns an IDeref which can be resolved to a response message."
  [^IRiemannClient client service ^Throwable t]
  (-> client
      (.sendException (name service) t)
      (map-promise decode-pb-msg)))

; Transports

(defn connect!
  "Connect a client or transport."
  [^Transport t]
  (.connect t))

(defn connected?
  "Is a client or transport connected?"
  [^Transport t]
  (.isConnected t))

(defn close!
  "Close a client or transport, shutting it down."
  [^Transport client]
  (.close client))

(defn flush!
  "Flush messages from a client or transport's buffers."
  [^Transport t]
  (.flush t))

(defn reconnect!
  "Reconnect a client or transport."
  [^Transport client]
  (.reconnect client))

(defn transport
  "Get an underlying transport from a client or transport."
  [^Transport t]
  (.transport t))

(defn ^RiemannBatchClient batch-client
  "Wraps a client in a RiemannBatchClient, with batch size n."
  ([client] (batch-client client 10))
  ([^IRiemannClient client n]
   (RiemannBatchClient. client n)))

(defn ^RiemannClient tcp-client
  "Creates a new TCP client. Options:

  :host         The host to connect to
  :port         The port to connect to

  :tls?         Whether to use TLS when connecting
  :key          A PKCS8 key
  :cert         A PEM certificate
  :ca-cert      The signing cert for our certificate and the server's
  :auto-connect Whether to call 'connect!' prior to returning the client

  Example:

  (tcp-client)
  (tcp-client {:host \"foo\" :port 5555})"
  [& opts]
  (let [opts (if (and (= 1 (count opts))
                      (map? (first opts)))
               (first opts)
               (apply hash-map opts))
        {:keys [^String host
                ^String remote-host
                ^String local-host
                ^Integer port
                ^Integer remote-port
                ^Integer local-port
                tls?
                key
                cert
                ca-cert
                auto-connect]
         :or {host "localhost", local-port 0, auto-connect true}} opts]

    ; Check options
    (when tls?
      (assert key)
      (assert cert)
      (assert ca-cert))

    ; Create client
    (let [remote-port (int (or remote-port port (if tls? 5554 5555)))
          client (if tls?
                   ; TLS client
                   (RiemannClient.
                     (doto (if-not local-host
                              (TcpTransport. (or remote-host host) remote-port)
                              (TcpTransport. (or remote-host host) remote-port local-host local-port))
                       (-> .sslContext
                           ;; (.set (SSL/sslContext key cert ca-cert))
                           (.set (ssl/ssl-context key cert ca-cert)))))

                   ; Standard client
                   (if-not local-host
                     (RiemannClient/tcp (or remote-host host) remote-port)
                     (RiemannClient/tcp (or remote-host host) remote-port local-host local-port)))]

      ; Attempt to connect lazily.
      (when auto-connect (try (connect! client)
                              (catch IOException _ nil)))
      client)))

(defn ^RiemannClient udp-client
  "Creates a new UDP client. Can take an optional maximum message size. Example:
  (udp-client)
  (udp-client {:host \"foo\" :port 5555 :max-size 16384})"
  [& opts]
  (let [opts (if (and (= 1 (count opts))
                      (map? (first opts)))
               (first opts)
               (apply hash-map opts))
        {:keys [^String host
                ^String remote-host
                ^String local-host
                ^Integer port
                ^Integer remote-port
                ^Integer local-port
                ^Integer max-size
                auto-connect]
         :or {port 5555
              host "localhost"
              max-size 16384
              local-port 0
              auto-connect true}} opts
        c (RiemannClient.
            (doto (if-not local-host
                      (UdpTransport. (or remote-host host) (or remote-port port))
                      (UdpTransport. (or remote-host host) (or remote-port port) local-host local-port))
              (-> .sendBufferSize (.set max-size))))]
    (when auto-connect (try (connect! c)
                            (catch IOException _ nil)))
    c))

(defn ^IRiemannClient multi-client
  "Creates a new multiclient from n clients"
  [clients]
  (let [clients (vec clients)
        n       (count clients)
        c       (fn choose-client []
                  (nth clients (mod (.getId (Thread/currentThread)) n)))]

    (reify IRiemannClient
      ; Transport
      (isConnected [c] (boolean (some connected? clients)))
      (connect     [c] (locking c (dorun (map connect! clients))))
      (reconnect   [c] (locking c (dorun (map reconnect! clients))))
      (close       [c] (locking c (dorun (map close! clients))))
      (flush       [c] (dorun (map flush! clients)))
      (transport   [c] (throw (UnsupportedOperationException.)))

      ; Client
      (sendMessage    [_ msg] (.sendMessage ^IRiemannClient (c) msg))
      (sendEvent      [_ e]   (.sendEvent ^IRiemannClient (c) e))

      (^IPromise sendEvents [_ ^List es]
        (.sendEvents ^IRiemannClient (c) ^List es))

      (sendException  [_ s t] (.sendException ^IRiemannClient (c) s t))
      (event          [_]     (.event ^IRiemannClient (c))))))
