;;
;; Defines a resource and default callback functions.
;;
(ns com.tnrglobal.bishop.resource
  (:require [com.tnrglobal.bishop.encoding :as encoding]))

(defn default-handlers
  "Returns a map of all of the supported handlers and default
  implenentations."
  []
  {

   ;; When you are coding up a handler function, you may return
   ;; several values. For all handlers you may return either a true or
   ;; false or a map containing a response that will be merged with
   ;; the response map built-up at callback time. For instance, the
   ;; :malformed-request? handler, uin the simplest case, would look
   ;; like the following.
   ;;
   ;; (fn [r] true)
   ;;
   ;; That is, for all request a 400 response would be returned. If
   ;; you'd like to provide body as well, you could provide this data
   ;; in a response map.
   ;;
   ;; (fn [r] {:headers {"content-type" "text/plain"}
   ;;          :body "The provided request was not data I like."})
   ;;
   ;; Several callbacks are more complicated. For instance, the
   ;; :is-authorized?  callback expects to recieve a true if the
   ;; client is authorized, a false if they or not or a String that
   ;; will be used in the "WWW-Authenticate" header for the
   ;; unauthorized client.
   ;;
   ;; In this case if you need to return a body you may return a
   ;; sequence. The first item in that sequence should be true or
   ;; false, the second item should be a response map that will be
   ;; merged into the response built-up at the time of callback. For
   ;; instance, to provide a body for the :is-authorized? callback
   ;; (this would be for unauthorized clients), you might return the
   ;; following sequence:
   ;;
   ;; [false {:headers {"www-authenticate" "Basic realm=\"Secure\""
   ;;                   "content-type" "text/plain"}
   ;;         :body "Accounts will lock after three failed attempts"}]
   ;;
   ;; Note that since you are returning a response map instead of the
   ;; String that the calling functions expect, it's assumed that you
   ;; are handling the necessary header for the callback. These
   ;; headers are noted in the comments for the resources. If ignored,
   ;; your application will demonstrate unpredictable behavior.
   ;;
   ;; Additionally, not all callbacks result in a response being sent
   ;; to the client. For instance, the :allow-missing-post? callback
   ;; is used to decide how an incoming request is handled. Returning
   ;; a body from this function would make no sense. The callback
   ;; functions that may use the above mechanism to return a response
   ;; body are listed below.
   ;;
   ;; *  :is-conflict?
   ;; *  :process-post
   ;; *  :allow-missing-post?
   ;; *  :delete-completed?
   ;; *  :valid-entity-length?
   ;; *  :known-content-type?
   ;; *  :is-authorized?
   ;; *  :malformed-request?
   ;; *  :uri-too-long?
   ;; *  :resource-exists?
   ;; *  :service-available? (false or a map if unavailable)

   ;; Return false to indicate the requested resource does not
   ;; exist; A "404 Not Found" message will be provided to the client.
   :resource-exists? (fn [request] true)

   ;; Return false if the requested service is not available; a "503
   ;; Service Not Available" message will be provided to the
   ;; cluent. If the resource is only temporarily unavailable, suppy a
   ;; map containing a "Retry-After" header.
   :service-available? (fn [request] true)

   ;; Returning anything other than true will send a "401
   ;; Unauthorized" to the client. Returning a String will cause that
   ;; value to be added to the response in the "WWW-Authenticate"
   ;; header.
   :is-authorized? (fn [request] true)

   ;; If true, sends a "403 Forbidden" response to the client.
   :forbidden? (fn [request] false)

   ;; If true, indicates that the resource accepts POST requests to
   ;; non-existant resources.
   :allow-missing-post? (fn [request] false)

   ;; Should return true if the provided request is malformed;
   ;; returning true sends a "400 Malformed Request" response to the
   ;; client.
   :malformed-request? (fn [request] false)

   ;; Should return true if the URI of the provided request is too
   ;; long to be processed. Returning true sends a "414 Request URI
   ;; Too Long" response to the client.
   :uri-too-long? (fn [request] false)

   ;; Should return false if the "Content-Type" of the provided
   ;; request is unknown (normally a PUT or POST). Returning false
   ;; will send a "415 Unsupported Media" response to the client.
   :known-content-type? (fn [request] true)

   ;; Should return false if the provided request contains and invalid
   ;; "Content-*" headers. Returning false sends a "501 Not
   ;; Implemented" response to the client.
   :valid-content-headers? (fn [request] true)

   ;; Should return false if the entity length of the provided request
   ;; (normally a PUT or POST) is invalid. Returning false sends a
   ;; "413 Request Entity Too Large" response.
   :valid-entity-length? (fn [request] true)

   ;; If the OPTIONS method is supported and used by the resource,
   ;; this method should return a hashmap of headers that will appear
   ;; in the response.
   :options (fn [request] {})

   ;; Returns an sequence of keywords representing the HTTP methods
   ;; supported by the resource.
   :allowed-methods (fn [request] [:get :head])

   ;; Returns a sequence of keywords representing all of the HTTP
   ;; methods known to the resource.
   :known-methods  (fn [request]
                     [:get :head :post :put :delete :trace :connect :options])

   ;; This function is called when a DELETE request should be enacted,
   ;; it should return true if the deletion was successful.
   :delete-resource (fn [request] false)

   ;; This function is called after a sucessful call to
   ;; :delete-resource and should return false if the deletion was
   ;; accepted but cannot be guaranteed to have completed.
   :delete-completed? (fn [request] true)

   ;; Returns true if POST requests should be treated as a request to
   ;; PUT content into a (potentically new) resource as opposed to a
   ;; generic submission for processing. If true is returned, the
   ;; :create-path function will be called and the rest of the request
   ;; will be treated like a PUT to that path.
   :post-is-create? (fn [request] false)

   ;; Will be called on a POST request if :post-is-create? returns
   ;; true. The path returned should be a valid URI part following the
   ;; dispatcher prefix; that path will replace the path under the
   ;; requests :uri key for all subsequent resource function calls.
   :create-path (fn [request] false)

   ;; Called after :create-path but before setting the "Location"
   ;; response header; determins the root URI of the new resource. If
   ;; nil, uses the URI of the request as the base.
   :base-uri (fn [request] nil)

   ;; This function is called if :post-is-create? returns false. It
   ;; should process any POST request and return true or a response
   ;; map if successful.
   :process-post (fn [request] nil)

   ;; Returns a sequence of character sets provided by this
   ;; resource. Correctly returning the appropriate character set is
   ;; handled by the resource.
   :charsets-provided (fn [request] ["utf-8"])

   ;; Returns a sequence of languages provided by the
   ;; resource. Correctly returning content with the correct language
   ;; is handled by the resource.
   :languages-provided (fn [reqeust] [] ["en"])

   ;; Returns a map of encodings provided by the resource, they key is
   ;; the name of the encoding and the value is a function that can
   ;; correctly encode the response body. Only the "identity" encoding
   ;; is provided, most other encoding (i.e. GZIP) should likely be
   ;; provided by additional Ring middleware. This function should be
   ;; used to support encodings for which no appropriate Ring
   ;; middleware exists.
   :encodings-provided (fn [request] {"identity" encoding/identity-enc})

   ;; Returns a sequence of header names that should be included in
   ;; the response's "Vary" header. The headers "Accept",
   ;; "Accept-Encoding", "Accept-Charset" and "Accept-Language" are
   ;; handled automatically by Bishop.
   :variances (fn [request] [])

   ;; Returns true if the provided PUT request would cause a conflict,
   ;; the "409 Conflict" response will be sent to the client.
   :is-conflict? (fn [request] false)

   ;; Returns true if multiple representations of the response are
   ;; possible and a single representation cannot be chosen; a "300
   ;; Multiple Choices" response will be sent to the client.
   :multiple-representations (fn [request] false)

   ;; Returns true if the resource is known to have previously
   ;; existed.
   :previously-existed? (fn [request] false)

   ;; If the resource has been permanently moved to a new location,
   ;; this function should return a String with the URI of the new
   ;; location.
   :moved-permanently? (fn [request] false)

   ;; If the resource has been temporarily moved to a new location,
   ;; this function should return a String with the URI of the new
   ;; location.
   :moved-temporarily? (fn [request] false)

   ;; Returns a Date with the last modified date of the resource. This
   ;; value will be used to construct the "Last-Modified" header. You
   ;; may use return a java.util.Date or a org.joda.time.DateTime
   ;; instance; the former will be converted to the latter.
   :last-modified (fn [request] nil)

   ;; If this resource expires, the date of that expiration should be
   ;; returned. This value will be used to construct the "Expires"
   ;; header. You may use return a java.util.Date or a
   ;; org.joda.time.DateTime instance; the former will be converted to
   ;; the latter.
   :expires (fn [request] nil)

   ;; Returns a value that will be used to construct the "ETag" header
   ;; for use in conditional requests.
   :generate-etag (fn [request] nil)

   ;; Verifies the "Content-MD5" header of the request against the
   ;; request's body. You may perform your own validation or return
   ;; true to bypass header validation. By default nil is returned and
   ;; Bishop will handle this validation automatically.
   :validate-content-checksum (fn [request] nil)})