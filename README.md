# Salesforce

Helpers for working with the Salesforce.com REST API

## Settings

## Usage

Authentication

```clojure
(def conf
  (ref
    {:client-id ""
     :client-secret ""
     :username ""
     :password ""
     :security-token ""})

;; Get an authentication token for requests
(def my-token (:auth_token (auth! @conf)))

```

## License

Copyright © 2013 FIXME

Distributed under the Eclipse Public License, the same as Clojure.
