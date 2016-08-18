(ns data-monitor.lib.util.pagerduty
  (:require 
    [clojure.pprint         :as pp]
    [clojure.tools.logging  :as log]
    [clojure.spec           :as s]
    [clojure.string         :as string]

    [data-monitor.lib.util.email      :as email]
  )

  (:gen-class)
)

(def alert-method? #{:email :api})

(s/def ::alert-method alert-method?)

(defn process-alert
  [conf-map alert-action-detail-map alert-detail-map]

  ;(log/debug "conf-map in pagerduty/process-alert: " conf-map)
  ;(log/debug "alert-action-detail-map in pagerduty/process-alert: " alert-action-detail-map)

  (when (= (alert-action-detail-map :method) "email")
    (log/debug "Sending pagerduty alert via email")
    (email/process-alert conf-map alert-action-detail-map alert-detail-map)
    (log/debug "pagerduty alert via email complete"))

  (when (= (alert-action-detail-map :method) "api")
    (log/debug "Sending pagerduty alert via PD API")
    (log/warn "Unable to process uninmplemented pagerduty alert method 'api'!"))
)

(defn notify-email
  [recipient-email]
)

(defn notify-api
  [severity user_or_group]
)
