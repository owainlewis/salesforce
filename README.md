# Salesforce

This is an up to date wrapper for the Salesforce.com REST API. I initially found working with
the API to be a bit frustrating and hopefully this wrapper will make everything easy for you.

More information about the Salesforce REST API can be found at

http://www.salesforce.com/us/developer/docs/api_rest/

## How do I use it?

It is available from Clojars. : )

```
[salesforce "0.1.0-SNAPSHOT"]
```

## Usage

We first need to set up some authentication information as a Clojure map. All the information can be found in your Salesforce account.

In order to get an auth token and information about your account we call the auth! function
like this

```clojure
(def config
  {:client-id ""
   :client-secret ""
   :username ""
   :password ""
   :security-token ""})

(def auth-info (auth! config))
```

This returns a map of information about your account including an authorization token that will allow you to make requests to the REST API.

The response looks something like this

```clojure
{:id "https://login.salesforce.com/id/1234",
 :issued_at "1367488271359",
 :instance_url "https://na15.salesforce.com",
 :signature "1234",
 :access_token "1234"}
```

Now you can use your auth-config to make requests to the API.

```clojure
(resources auth-info)
```

## SObjects

The following methods are available

+ so->all
+ so->get
+ so->create
+ so->update
+ so->delete
+ so->describe

Get all records

```clojure

```

Get a single record

```clojure
;; Fetch all the info
(so->get "Account" "001i0000007nAs3" auth-info)
;; Fetch only the name and website attributes
(so->get "Account" "001i0000007nAs3" ["Name" "Website"] auth-info))
```

Create a record

```clojure
(so->create "Account" {:Name "My Account"} auth-info)
```

Delete a record

```clojure
(so->delete "Account" "001i0000008Ge2OAAS" auth-info)
```

Describe an record

```clojure
(so->describe "Account" auth-info)
```

## Salesforce Object Query Language

Salesforce provides a query language called SOQL that lets you run custom queries on the API.

```clojure
(soql "SELECT name from Account" auth-info)
```

## License

Copyright © 2013 Owain Lewis

Distributed under the Eclipse Public License, the same as Clojure.
