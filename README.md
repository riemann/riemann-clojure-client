# riemann-clojure-client

A clojure client for [Riemann](http://aphyr.github.com/riemann)

## Usage

For riemann-java-client, add the boundary repo to your project.clj:
```clojure
:repositories {
  "boundary-site" "http://maven.boundary.com/artifactory/repo"
}
```

``` clojure
(use 'riemann.client)
(def c (tcp-client :host "1.2.3.4"))
(send-event c {:service "foo" :state "ok"})
(query c "state = \"ok\"")
```

## License

Copyright © 2012 Kyle Kingsbury

Distributed under the Eclipse Public License, the same as Clojure.
