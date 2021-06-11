# Salesforce

This is an up to date wrapper for the Salesforce.com REST API. I initially found working with
the API to be a bit frustrating and hopefully this wrapper will make everything easy for you.

More information about the Salesforce REST API can be found at

[http://www.salesforce.com/us/developer/docs/api_rest/](http://www.salesforce.com/us/developer/docs/api_rest/)

## Build

[![Clojars Project](https://img.shields.io/clojars/v/com.owainlewis/salesforce.svg)](https://clojars.org/com.owainlewis/salesforce)

[![CircleCI](https://circleci.com/gh/owainlewis/salesforce.svg?style=svg)](https://circleci.com/gh/owainlewis/salesforce)


## Usage

We first need to set up some authentication information as a Clojure map. All the information can be found in your Salesforce account.

In order to get an auth token and information about your account we call the auth! function
like this

```clojure
(use 'salesforce.core)

(def config
  {:client-id ""
   :client-secret ""
   :username ""
   :password ""
   :security-token ""})

(def auth-info (auth! config))
```
You can optionally pass in :login-host if you want to use test.salesforce.com or my.salesforce.com addresses

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

## Setting the API version

There are multiple versions of the Salesforce API so you need to decare the version you want to use.

You can easily get the latest API version with the following function

```clojure
(latest-version) ;; => "39.0"
```

You can set a version in several ways.

Globally

```clojure
(set-version! "39.0")
```

Inside a macro

```clojure
(with-version "39.0"
  ;; Do stuff here )

```

Or just using the latest version (this is slow as it needs to make an additional http request)

```clojure
(with-latest-version
  ;; Do stuff here)
```

## SObjects

The following methods are available

+ so->all
+ so->get
+ so->create
+ so->update
+ so->delete
+ so->describe

Get all sobjects

```clojure
(so->objects auth-info)
```

Get all records

```clojure
(so->all "Account" auth-info)
```

Get recently created items

```clojure
(so->recent "Account" auth-info)
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

Update a record

```clojure
(so->update "Account" "001i0000007nAs3" {:Name "My New Account Name"} auth-info)
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

Salesforce provides a search language called SOSL that lets you run custom searches on the API.

```clojure
(sosl "FIND {Joe Smith} IN Name Fields RETURNING lead" auth-info)
```

## A sample session

This final example shows an example REPL session using the API

```clojure

(def config
  {:client-id ""
   :client-secret ""
   :username ""
   :password ""
   :security-token ""})

;; Get auth info needed to make http requests
(def auth (auth! config))

;; Get and then set the latest API version globally
(set-version! (latest-version))

;; Now we are all set to access the salesforce API
(so->objects auth)

;; Get all information from accounts
(so->all "Account" auth)

;; Fetch a single account
(so->get "Account" "001i0000008Ge2TAAS" auth)

;; Create a new account
(so->create "Account" {:Name "My new account"} auth)

;; Update the account
(so->update "Account" "001i0000008JTPpAAO" {:Name "My Updated Account Name"} auth)


;; Delete the account we just created
(so->delete "Account" "001i0000008JTPpAAO" auth)

;; Finally use SOQL to find account information
(:records (soql "SELECT name from Account" auth))

;; You can also pass a clojure java.jdbc array to the soql query
;; The classes on the array will be serialized following the SOQLable protocol, which can be extended in your program.
(soql ["select * from fruits where name = ? and price >= ? and created = ?" "apple" 9/5 (java.time.LocalDate/of 2020 10 10)] auth)
```

## Contributors

+ Owain Lewis
+ Rod Pugh
+ Lucas Severo

## License

Distributed under the Eclipse Public License, the same as Clojure.
