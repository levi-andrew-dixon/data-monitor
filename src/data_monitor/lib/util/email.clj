(ns data-monitor.lib.util.email
  (:require 
    [clojure.java.io        :as jio]
    [clojure.pprint         :as pp]
    [clojure.tools.logging  :as log]
    [clojure.spec           :as s]
    [clojure.string         :as string]

    ; TODO: Resolve issues with postal when run from JAR
    [postal.core            :as email]

    [data-monitor.lib.util.config  :as conf])
  (:gen-class))

(defn get-mail-conf
  [config-map]
)

(defn process-attachment
  "Process a single map representing an email attachment.
   file-name may be used to override default attachment name chosen by email lib.
   file-path may be one of stream, file object, or string path.

   @return attachment-map   A map representing an attachment, formatted as expected by email lib."
  [attachment-detail-map]
  (let [
    {:keys [att-name content-type inline? file-name file-path file-contents]} attachment-detail-map
    postal-att-map {
      :type
        (if inline? :inline :attachment)
      (when content-type :content-type) (when content-type content-type)
      (when file-name :file-name)       (when file-name file-name)
      :content
        (cond
          (when file-contents file-contents)
          (when file-path
            (cond
              (instance? java.lang.String file-path)
                (jio/file file-path)
              (instance? java.io.BufferedInputStream file-path)
                (slurp file-path)
              :else
                file-path))) }]
    postal-att-map))

(defn send-message
  "Send basic email message with the supplied input.
   @param   message-detail-map
   @return  result"
  [message-detail-map]
  (log/debug "Sending email message: " message-detail-map)
  (let [
    {:keys [sender, recipient, subject, content-type, body, attachment-vec]} message-detail-map]
    (email/send-message {
      :from     sender
      :to       recipient
      :subject  subject
      :body
        (concat
          [{:type content-type :content body}]
          (doall (map process-attachment attachment-vec))) })))

(defn construct-profile-string
  [conf-map & [conf-profile-name]]
  (let [
    conf-profile-name
      (or conf-profile-name (conf/get-val-memoized-fn conf-map [:profile-name]))]
    (when (not (empty? conf-profile-name))
      (str "ENV-NAME [" conf-profile-name "]"))))

(defn construct-subject-string
  [conf-map & [subject subject-prefix]]
  (let[
    subject-detail-vec
      (concat [(construct-profile-string conf-map)] [subject])
    subject
      (str subject-prefix (string/join "; " subject-detail-vec))]
    subject))

; Convert to use send-message using message-detail-map
(defn send-message-using-conf
  "Send an email message, pulling most details from the config file
  @return message-status-map  {:code 0, :error :SUCCESS, :message \"message sent\"} "
  [conf-map mail-details-map subject message & [content-type]]

  (let [
    conf-profile-name
      (conf/get-val-memoized-fn conf-map [:profile-name])
    subject-prefix
      (conf/get-val-memoized-fn mail-details-map [:subject-prefix])
    subject
      (construct-subject-string conf-map subject subject-prefix)
    content-type
      (or content-type "text/plain")]

    (email/send-message {
      :from     (conf/get-val-memoized-fn mail-details-map [:sender])
      :to       (conf/get-val-memoized-fn mail-details-map [:recipient])
      :subject  subject
      :body     [{
        :type     content-type
        :content  message}]})))

(defn process-alert
  "Processes alerts dispatched by generic alert library."
  [conf-map alert-action-detail-map alert-detail-map]

  (let [
    [sender, subject-prefix]
      (doall (conf/get-multi-memoized conf-map [
        [:monitoring :email :sender]
        [:monitoring :email :subject-prefix]]))
    recipient
      (or (alert-action-detail-map :email-recipient) (conf/get-val-memoized-fn [:monitoring :email :recipient])) ]

  (log/debug (format "Sending email alert to recipient: [%s]" recipient))

  (send-message {
    :sender       sender
    :recipient    recipient
    :subject      (construct-subject-string conf-map (alert-detail-map :subject) subject-prefix)
    :content-type (alert-detail-map :content-type)
    :body         (alert-detail-map :body)})))
