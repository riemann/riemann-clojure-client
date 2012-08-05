# riemann-clojure-client

A clojure client for (Riemann)[http://aphyr.github.com/riemann]

## Usage

``` clojure
(use 'riemann.client)
(def c (tcp-client :host "1.2.3.4"))
(send-event c {:service "foo" :state "ok"})
(query c "state = \"ok\"")
```

## License

Copyright © 2012 Kyle Kingsbury

Distributed under the Eclipse Public License, the same as Clojure.
