;;
;; Provides functions to test the functions in the bishop.flow
;; namespace.
;;
(ns com.tnrglobal.bishop.test.flow
  (:use [com.tnrglobal.bishop.core]
        [com.tnrglobal.bishop.flow]
        [clojure.test])
  (:require [clojure.java.io :as io]
            [clojure.string :as string])
  (:import [java.io StringBufferInputStream]
           [java.util Date]))

(def test-request
  {:remote-addr "0:0:0:0:0:0:0:1%0"
   :scheme :http
   :query-params {}
   :form-params {}
   :request-method :get
   :query-string nil
   :content-type nil
   :uri "/"
   :server-name "localhost"
   :params {}
   :headers {"user-agent" "curl/7.21.4 (universal-apple-darwin11.0) libcurl/7.21.4 OpenSSL/0.9.8r zlib/1.2.5"
             "accept" "*/*"
             "host" "localhost:8080"}})

(deftest states

  ;; Available?

  (testing "B13 Invalid"
    (let [res (resource {"text/html" "testing..."}
                        {:service-available? (fn [request] false)})
          req test-request]
      (is (= 503 (:status (run req res))) "Service unavailable")))

  (testing "B13 Valid"
    (let [res (resource {"text/html" "testing..."})
          req test-request]
      (is (= 200 (:status (run req res))))))

  ;; Known method?

  (testing "B12 Invalid"
    (let [res (resource {"text/html" "testing"})
          req (assoc test-request :request-method :super-get)]
      (is (= 501 (:status (run req res))) "Not implemented")))

  (testing "B12 Valid"
    (let [res (resource {"text/html" "testing"})
          req test-request]
      (is (= 200 (:status (run req res))))))

  ;; URI too long?

  (testing "B11 Invalid"
    (let [res (resource {"text/html" "testing"}
                        {:uri-too-long? (fn [request] true)})
          req test-request]
      (is (= 414 (:status (run req res))) "Request URI too long")))

  (testing "B11 Valid"
    (let [res (resource {"text/html" "testing"})
          req test-request]
      (is (= 200 (:status (run req res))))))

  ;; Is method allowed on this resource?

  (testing "B10 Invalid"
    (let [res (resource {"text/html" "testing"}
                        {:allowed-methods (fn [request] [:post])})
          req test-request]
      (let [res-out (run req res)]
        (is (and (= 405 (:status res-out))
                 (some (fn [[head val]]
                         (= "allow" head)) (:headers res-out))) "Method not allowed"))))

  (testing "B10 Valid"
    (let [res (resource {"text/html" "testing"})
          req test-request]
      (is (= 200 (:status (run req res))))))

  ;; Contains "Content-MD5" header?

  (testing "B9 No Header"
    (let [res (resource {"text/html" "testing"})
          req test-request]
      (is (= 200 (:status (run req res))))))

  (testing "B9 Valid"
    (let [res (resource {"text/html" "testing"})
          req (merge test-request
                     {:headers (conj (:headers test-request)
                                     ["content-md5" "e4e68fb7bd0e697a0ae8f1bb342846d7"])
                      :body (StringBufferInputStream. "Test message.")})]
      (is (= 400 (:status (run req res))))))

  (testing "B9 Invalid"
    (let [res (resource {"text/html" "testing"})
          req (merge-with concat
                          test-request
                          {:headers ["content-md5" "e4e68fb7bd0e697a0ae8f1bb342846b3"]
                           :body (StringBufferInputStream. "Test message.")})]
      (is (= 200 (:status (run req res))) "Content-MD5 header does not match request body")))

  ;; is authorized?

  (testing "B8 Valid"
    (let [res (resource {"text/html" "testing"})
          req test-request]
      (is (= 200 (:status (run req res))))))

  (testing "B8 Valid"
    (let [res (resource {"text/html" "testing"}
                        {:is-authorized? (fn [request] false)})
          req test-request]
      (is (= 401 (:status (run req res))) "Unauthorized")))

    (testing "B8 Invalid"
      (let [res (resource {"text/html" "testing"}
                          {:is-authorized? (fn [request] "Basic")})
            req test-request
            response (run req res)]
        (is (and (= 200 (:status response))
                 (some (fn [[head value]]
                         (and (= "www-authenticate" head)
                              (= "Basic" value)))
                       (:headers response))) "Authenticate")))

    ;; forbidden?

    (testing "B7 Valid"
      (let [res (resource {"text/html" "testing"})
            req test-request]
        (is (= 200 (:status (run req res))))))

    (testing "B7 Invalid"
      (let [res (resource {"text/html" "testing"}
                          {:forbidden? (fn [request] true)})
            req test-request]
        (is (= 403 (:status (run req res))) "Forbidden")))

    ;; valid content headers?

    (testing "B6 Valid"
      (let [res (resource {"text/html" "testing"})
            req test-request]
        (is (= 200 (:status (run req res))))))

    (testing "B6 Invalid"
      (let [res (resource {"text/html" "testing"}
                          {:valid-content-headers? (fn [request] false)})
            req test-request]
        (is (= 501 (:status (run req res))) "Not implemented")))

    ;; known content type?

    (testing "B5 Valid"
      (let [res (resource {"text/html" "testing"})
            req test-request]
        (is (= 200 (:status (run req res))))))

    (testing "B5 Invalid"
      (let [res (resource {"text/html" "testing"}
                          {:known-content-type? (fn [request] false)})
            req test-request]
        (is (= 415 (:status (run req res))) "Unsupported media type")))

    ;; valid entity length?

    (testing "B4 Valid"
      (let [res (resource {"text/html" "testing"})
            req test-request]
        (is (= 200 (:status (run req res))))))

    (testing "B4 Invalid"
      (let [res (resource {"text/html" "testing"}
                          {:valid-entity-length? (fn [request] false)})
            req test-request]
        (is (= 413 (:status (run req res))) "Request entity too large")))

    ;; options?

    (testing "B3 Valid"
      (let [res (resource {"text/html" "testing"})
            req test-request]
        (is (= 200 (:status (run req res))))))

    (testing "B3 Options"
      (let [res (resource {"text/html" "testing"}
                          {:allowed-methods (fn [request] [:get :head :options])
                           :options (fn [request] {"allow" "GET, HEAD, OPTIONS"})})
            req (assoc test-request :request-method :options)]
        (is (some (fn [[header value]]
                    (= "allow" header))
                  (:headers (run req res))) "Request entity too large")))

    ;; acceptable content type?

    (testing "B4 Valid"
      (let [res (resource {"text/html" (fn [r] (:acceptable-type r))})
            req test-request]
        (let [response (run req res)]
          (is (and (= 200 (:status response))
                   (= "text/html" (:body response)))))))

    (testing "B4 Invalid"
      (let [res (resource {"text/plain" "testing"})
            req (assoc-in test-request [:headers "accept"]
                       "text/html,application/xhtml+xml,application/xml;q=0.9")]
        (is (= 406 (:status (run req res))) "Not Acceptable")))

    ;; acceptable language?

    (testing "D4 Unspecified"
      (let [res (resource {"text/html" (fn [r] (:acceptable-language r))})
            req test-request]
        (let [response (run req res)]
          (is (and (= 200 (:status response))
                   (= nil (:body response)))))))

    (testing "D4 Valid"
      (let [res (resource {"text/html" (fn [r] (:acceptable-language r))})
            req (assoc-in test-request [:headers "accept-language"]
                          "en,*;q=0.8")]
        (let [response (run req res)]
          (is (and (= 200 (:status response))
                   (= "*" (:body response)))))))

    (testing "D4 Invalid"
      (let [res (resource {"text/html" "testing"})
            req (assoc-in test-request [:headers "accept-language"]
                          "da,en;q=0.8")]
        (is (= 406 (:status (run req res))) "Not Acceptable")))

    ;; acceptable language available?

    (testing "D5 Unspecified"
      (let [res (resource {"text/html" "testing"})
            req test-request]
        (is (= 200 (:status (run req res))))))

    (testing "D5 Available"
      (let [res (resource {"text/html" (fn [r] (:acceptable-language r))}
                          {:languages-provided (fn [r] ["en"])})
            req (assoc-in test-request [:headers "accept-language"]
                          "da,en;q=0.8")]
        (let [response (run req res)]
          (is (and (= 200 (:status response))
                   (= "en" (:body response)))))))

    (testing "D5 Invalid"
      (let [res (resource {"text/html" "testing"})
            req (assoc-in test-request [:headers "accept-language"]
                          "da,en;q=0.8")]
        (is (= 406 (:status (run req res))) "Not Acceptable")))

    ;; acceptable charset?

    (testing "E5 Unspecified"
      (let [res (resource {"text/html" (fn [r] (:acceptable-charset r))})
            req test-request]
        (let [response (run req res)]
          (is (and (= 200 (:status response))
                   (= nil (:body response)))))))

    (testing "E5 Valid"
      (let [res (resource {"text/html" (fn [r] (:acceptable-charset r))})
            req (assoc-in test-request [:headers "accept-charset"]
                          "utf8,*;q=0.8")]
        (let [response (run req res)]
          (is (and (= 200 (:status response))
                   (= "*" (:body response)))))))

    (testing "E5 Invalid"
      (let [res (resource {"text/html" "testing"})
            req (assoc-in test-request [:headers "accept-charset"]
                          "utf8,ISO-8859-1;q=0.8")]
        (is (= 406 (:status (run req res))) "Not Acceptable")))

    ;; acceptable charset available?

    (testing "E6 Unspecified"
      (let [res (resource {"text/html" "testing"})
            req test-request]
        (is (= 200 (:status (run req res))))))

    (testing "E6 Available"
      (let [res (resource {"text/html" (fn [r] (:acceptable-charset r))}
                          {:charsets-provided (fn [r] ["UTF8"])})
            req (assoc-in test-request [:headers "accept-charset"]
                          "utf8,iso-8859-1;q=0.8")]
        (let [response (run req res)]
          (is (and (= 200 (:status response))
                   (= "utf8" (:body response)))))))

    (testing "E6 Invalid"
      (let [res (resource {"text/html" "testing"})
            req (assoc-in test-request [:headers "accept-charset"]
                          "utf8,iso-8859-1;q=0.8")]
        (is (= 406 (:status (run req res))) "Not Acceptable")))

    ;; acceptable encoding?

    (testing "F6 Unspecified"
      (let [res (resource {"text/html" (fn [r] (:acceptable-encoding r))})
            req test-request]
        (let [response (run req res)]
          (is (and (= 200 (:status response))
                   (= nil (:body response)))))))

    (testing "F6 Valid"
      (let [res (resource {"text/html" (fn [r] (:acceptable-encoding r))})
            req (assoc-in test-request [:headers "accept-encoding"]
                          "gzip,*;q=0.8")]
        (let [response (run req res)]
          (is (and (= 200 (:status response))
                   (= "identity" (:body response)))))))

    (testing "F6 Invalid"
      (let [res (resource {"text/html" "testing"})
            req (assoc-in test-request [:headers "accept-encoding"]
                          "gzip,deflate;q=0.8")]
        (is (= 406 (:status (run req res))) "Not Acceptable")))

    ;; acceptable encoding available?

    (testing "F7 Unspecified"
      (let [res (resource {"text/html" "testing"})
            req test-request]
        (is (= 200 (:status (run req res))))))

    (testing "F7 Available"
      (let [res (resource {"text/html" (fn [r] (:acceptable-encoding r))})
            req (assoc-in test-request [:headers "accept-encoding"]
                          "gzip,*;q=0.8")]
        (let [response (run req res)]
          (is (and (= 200 (:status response))
                   (= "identity" (:body response)))))))

    (testing "F7 Invalid"
      (let [res (resource {"text/html" "testing"})
            req (assoc-in test-request [:headers "accept-encoding"]
                          "gzip,deflate;q=0.8")]
        (is (= 406 (:status (run req res))) "Not Acceptable")))

    ;; vary header

    (testing "G7 No Header"
      (let [res (resource {"text/html" "testing"})
            req test-request]
        (let [response (run req res)]
          (is (and (= 200 (:status response))
                   (= "accept" ((:headers response) "vary")))))))

    (testing "G7 Header"
      (let [res (resource {"text/html" "testing"})
            req (assoc test-request :headers
                       (concat (:headers test-request)
                               {"accept-encoding" "gzip,*;q=0.8"}
                               {"accept-charset" "utf8,*;q=0.8"}
                               {"accept-language" "en,*;q=0.8"}
                               {"accept" "text/html,application/xhtml+xml;q=0.8"}))]
        (let [response (run req res)]
          (is (and (= 200 (:status response))
                   (= "accept-encoding, accept-charset, accept-language, accept"
                      ((:headers response) "vary")))))))

    ;; if-match etag

    (testing "G8 No If-Match"
      (let [res (resource {"text/html" "testing"})
            req test-request]
        (let [response (run req res)]
          (is (and (= 200 (:status response)))))))

    (testing "G9 If-Match *"
      (let [res (resource {"text/html" "testing"})
            req (assoc test-request :headers
                       (concat (:headers test-request)
                               {"if-match" "*"}))]
        (let [response (run req res)]
          (is (= 200 (:status response))))))

    (testing "G11 E-Tag Matches"
      (let [res (resource {"text/html" "testing"}
                          {:generate-etag (fn [r] "testing")})
            req (assoc test-request :headers
                       (concat (:headers test-request)
                               {"if-match" "\"testing\", \"testing-ish\""}))]
        (let [response (run req res)]
          (is (and (= 200 (:status response)))))))

    (testing "G11 E-Tag Does Not Match"
      (let [res (resource {"text/html" "testing"}
                          {:generate-etag (fn [r] "testing")})
            req (assoc test-request :headers
                       (concat (:headers test-request)
                               {"if-match" "\"not testing\", \"production\""}))]
        (let [response (run req res)]
          (is (= 412 (:status response))))))

    ;; if-match is a quoted astrisk

    (testing "H7 If-Match Quoted *"
      (let [res (resource {"text/html" "testing"})
            req (assoc test-request :headers
                       (concat (:headers test-request)
                               {"if-match" "\"*\""}))]
        (let [response (run req res)]
          (is (and (= 412 (:status response)))))))

    ;; if-unmodified-since

    (testing "H11 If-Unmodified-Since, Format #1"
      (let [res (resource {"text/html" (fn [r] (r :if-unmodified-since))})
            req (assoc test-request :headers
                       (concat (:headers test-request)
                               {"if-unmodified-since" "Fri, 31 Dec 1999 23:59:59 GMT"}))]
        (let [response (run req res)]
          (is (and (= 200 (:status response))
                   (= 946684799000 (.getTime (response :body))))))))

    (testing "H11 If-Unmodified-Since, Format #2"
      (let [res (resource {"text/html" (fn [r] (r :if-unmodified-since))})
            req (assoc test-request :headers
                       (concat (:headers test-request)
                               {"if-unmodified-since" "Friday, 31-Dec-99 23:59:59 GMT"}))]
        (let [response (run req res)]
          (is (and (= 200 (:status response))
                   (= 946684799000 (.getTime (response :body))))))))

    (testing "H11 If-Unmodified-Since, Format #3"
      (let [res (resource {"text/html" (fn [r] (r :if-unmodified-since))})
            req (assoc test-request :headers
                       (concat (:headers test-request)
                               {"if-unmodified-since" "Fri Dec 31 23:59:59 1999"}))]
        (let [response (run req res)]
          (is (and (= 200 (:status response))
                   (= 946702799000 (.getTime (response :body))))))))

    (testing "H11 If-Unmodified-Since, Invalid"
      (let [res (resource {"text/html" (fn [r] (r :if-unmodified-since))})
            req (assoc test-request :headers
                       (concat (:headers test-request)
                               {"if-unmodified-since" "I like ice cream!"}))]
        (let [response (run req res)]
          (is (and (= 200 (:status response))
                   (nil? (response :body)))))))

    (testing "H12 If-Unmodified-Since, True"
      (let [res (resource {"text/html" "testing"}
                          {:last-modified (fn [r] (Date.))})
            req (assoc test-request :headers
                       (concat (:headers test-request)
                               {"if-unmodified-since"
                                "Fri, 31 Dec 1999 23:59:59 GMT"}))]
        (let [response (run req res)]
          (is (= 200 (:status response))))))

    (testing "H12 If-Unmodified-Since, False"
      (let [res (resource {"text/html" "testing"}
                          {:last-modified (fn [r] (Date. 139410000000))})
            req (assoc test-request :headers
                       (concat (:headers test-request)
                               {"if-unmodified-since"
                                "Fri, 31 Dec 1999 23:59:59 GMT"}))]
        (let [response (run req res)]
          (is (= 412 (:status response))))))

    (testing "I12 No If-None-Match Header"
      (let [res (resource {"text/html" "testing"})
            req test-request]
        (let [response (run req res)]
          (is (= 200 (:status response))))))

    (testing "I13 GET, If-None-Match = *, True"
      (let [res (resource {"text/html" "testing"})
            req (assoc test-request :headers
                       (concat (:headers test-request)
                               {"if-none-match" "*"}))]
        (let [response (run req res)]
          (is (= 304 (:status response))))))

    (testing "I13 POST, If-None-Match = *, True"
      (let [res (resource {"text/html" "testing"}
                          {:allowed-methods (fn [request] [:post])})
            req (assoc (assoc test-request :headers
                              (concat (:headers test-request)
                                      {"if-none-match" "*"}))
                  :request-method :post)]
        (let [response (run req res)]
          (is (= 412 (:status response))))))

    (testing "K13 ETag not in If-None-Match"
      (let [res (resource {"text/html" "testing"}
                          {:generate-etag (fn [request] "eb54d63b7351fb3a92bf008179cdacd2")})
            req (assoc test-request :headers
                              (concat (:headers test-request)
                                      {"if-none-match" "\"ba51d0516daf8d09919af69e8fc8145d\""}))]
        (let [response (run req res)]
          (is (= 200 (:status response))))))

    (testing "G8, H10, L13 No If-Modified-Since"
      (let [res (resource {"text/html" "testing"})
            req test-request]
        (let [response (run req res)]
          (is (= 200 (:status response))))))

    (testing "L14 If-Modified-Since, Valid"
      (let [res (resource {"text/html" (fn [request]
                                         (:if-modified-since request))}
                          {:last-modified (fn [request] (Date. 946684799000))})
            req (assoc test-request :headers
                       (concat (:headers test-request)
                               {"if-modified-since"
                                "Fri, 31 Dec 1999 23:59:59 GMT"}))]
        (let [response (run req res)]
          (is (:body response)))))

    (testing "L14 If-Modified-Since, Invalid"
      (let [res (resource {"text/html" (fn [request]
                                         (:if-modified-since request))})
            req (assoc test-request :headers
                       (concat (:headers test-request)
                               {"if-modified-since"
                                "Booyah!"}))]
        (let [response (run req res)]
          (is (not (:body response))))))

    (testing "L17 Last-Modified > If-Modified-Since, True"
      (let [res (resource {"text/html" "testing"}
                          {:last-modified (fn [request] (Date. 946684799000))})
            req (assoc test-request :headers
                       (concat (:headers test-request)
                               {"if-modified-since"
                                "Fri, 31 Dec 1969 23:59:59 GMT"}))]
           (let [response (run req res)]
             (is (= 304 (:status response))))))

    (testing "M20 DELETE If-Umodified-Since, True"
      (let [res (resource {"text/html" "testing"}
                          {:allowed-methods (fn [request] [:delete])
                           :last-modified (fn [request] (Date. 946684799000))
                           :delete-resource (fn [request] true)})
            req (assoc (assoc test-request :request-method :delete)
                  :headers (concat (:headers test-request)
                                   {"if-modified-since"
                                    "Fri, 31 Dec 2011 23:59:59 GMT"}))]
        (let [response (run req res)]
          (is (= 204 (:status response))))))

    (testing "M20 DELETE If-Umodified-Since, True But No Delete"
      (let [res (resource {"text/html" "testing"}
                          {:allowed-methods (fn [request] [:delete])
                           :last-modified (fn [request] (Date. 946684799000))
                           :delete-resource (fn [request] false)})
            req (assoc (assoc test-request :request-method :delete)
                  :headers (concat (:headers test-request)
                                   {"if-modified-since"
                                    "Fri, 31 Dec 2011 23:59:59 GMT"}))]
        (let [response (run req res)]
          (is (= 202 (:status response))))))

    (testing "M20 DELETE If-Umodified-Since, False"
      (let [res (resource {"text/html" "testing"}
                          {:allowed-methods (fn [request] [:delete])
                           :last-modified (fn [request] (Date. 946684799000))
                           :delete-resource (fn [request] true)})
            req (assoc (assoc test-request :request-method :delete)
                  :headers (concat (:headers test-request)
                                   {"if-modified-since"
                                    "Fri, 31 Dec 1974 23:59:59 GMT"}))]
        (let [response (run req res)]
          (is (= 304 (:status response))))))

    (testing "018 Multiple-Representations, False"
      (let [res (resource {"text/html" "testing"}
                          {:last-modified (fn [request] (Date. 946684799000))})
            req (assoc test-request :headers
                       (concat (:headers test-request)
                               {"if-modified-since"
                                "Fri, 31 Dec 2010 23:59:59 GMT"}))]
        (let [response (run req res)]
          (is (= 200 (:status response))))))

    (testing "018 Multiple-Representations, True"
      (let [res (resource {"text/html" "testing"}
                          {:last-modified (fn [request] (Date. 946684799000))
                           :multiple-representations (fn [request] true)})
            req (assoc test-request :headers
                       (concat (:headers test-request)
                               {"if-modified-since"
                                "Fri, 31 Dec 2010 23:59:59 GMT"}))]
        (let [response (run req res)]
          (is (= 300 (:status response))))))

    (testing "016 PUT Conflict"
      (let [res (resource {"text/html" "testing"}
                          {:allowed-methods (fn [request] [:put])
                           :is_conflict? (fn [request] true)})
            req (assoc test-request :request-method :put)]
        (let [response (run req res)]
          (is (= 409 (:status response))))))
  )
