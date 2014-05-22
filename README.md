# riemann-clojure-client

A clojure client for [Riemann](http://aphyr.github.com/riemann)

## Usage

Download from clojars: https://clojars.org/riemann-clojure-client

``` clojure
(use 'riemann.client)
(def c (tcp-client {:host "1.2.3.4"}))
(send-event c {:service "foo" :state "ok"})
(query c "state = \"ok\"")
```

To connect to Riemann server using TLS, please refer to https://github.com/aphyr/less-awful-ssl for getting the CA certificate, signed key and cert.

``` clojure
(use 'riemann.client)
(def c (tcp-client {:host "1.2.3.4"
                    :port 5554
                    :tls? true
                    :key "client.pkcs8"
                    :cert "client.crt"
                    :ca-cert "ca.crt"}))
(send-event c {:service "foo" :state "ok"})
(query c "state = \"ok\"")

```

:key, :cert and :ca-cert could be any type of File, URI, URL, Socket, byte array, and String arguments. If the argument is a String, it tries to resolve it first as a URI, then as a local file name. URIs with a 'file' protocol are converted to local file names. (Check clojure.java.io/input-stream for more information.)

## License

Copyright © 2012 Kyle Kingsbury

Distributed under the Eclipse Public License, the same as Clojure.
