# riemann-clojure-client

A Clojure client for [Riemann](http://aphyr.github.com/riemann)

## Usage

Download from clojars: https://clojars.org/riemann-clojure-client

``` clojure
(require '[riemann.client :as r])
(def c (r/tcp-client {:host "1.2.3.4"}))
(-> c (r/send-event {:service "foo" :state "ok"})
      (deref 5000 ::timeout))
@(r/query c "state = \"ok\"")
```

All operations return deref-able Riemann Promises, supporting both the untimed
and time-bounded deref operations. Deref will throw for IO errors, or when the
server returns an invalid response. You can and should retry these operations.
Note that the client will not accept unbounded writes; calls to send() when the
client's local buffers are full may return immediately with an
OverloadedException.

## TLS

To connect to a Riemann server using TLS, please refer to
https://github.com/aphyr/less-awful-ssl for building a CA certificate, signed
key and cert.

``` clojure
(def c (r/tcp-client {:host "1.2.3.4"
                    :port 5554
                    :tls? true
                    :key "client.pkcs8"
                    :cert "client.crt"
                    :ca-cert "ca.crt"}))
@(r/send-event c {:service "foo" :state "ok"})
@(r/query c "state = \"ok\"")
```

:key, :cert and :ca-cert could be any type of File, URI, URL, Socket, byte
array, and String arguments. If the argument is a String, it tries to resolve
it first as a URI, then as a local file name. URIs with a 'file' protocol are
converted to local file names. (Check clojure.java.io/input-stream for more
information.)

## License

Copyright © 2012--2015 Kyle Kingsbury <aphyr@aphyr.com>

Distributed under the Eclipse Public License, the same as Clojure.
