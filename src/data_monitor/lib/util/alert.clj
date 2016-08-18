(ns data-monitor.lib.util.alert
  (:require 
    [clojure.tools.logging  :as log]
    [clojure.pprint         :as pp]
    [clojure.spec           :as s]
    [clojure.string         :as string]

    [data-monitor.lib.util.email      :as em]
    [data-monitor.lib.util.pagerduty  :as pd]
  )
  (:gen-class))


;
; Clojure spec schema configuration
;

(def    alert-action-type?     #{:pager-duty :team-email})
(s/def  ::alert-action-type    alert-action-type?)
(s/def  ::alert-action-detail-map
  (s/keys :req [::alert-action-type]))
;(s/def  ::alert-action-detail-vec  (s/*  ::alert-action-detail-map))
(s/def  ::alert-action-detail-vec
  (s/coll-of ::alert-action-detail-map))
(s/def  ::alert-action-detail
  (s/or ::alert-action-detail-map ::alert-action-detail-vec))


(defn process-alert
  "Given alert action details (a map or list of alert action detail maps) and alert details (information about a failed condition to be alerted on),
   Dispatches alerts to specific alter type processors implemented in other libraries.

  @param  alert-action-detail   Either a map representing an alert action or a vector of such maps.
  @param  alert-detail-map      A map containing alert details (message, recipient, etc.).

  @return alert-result          Either a result for an individual alert or a vector or such results."
  [conf-map alert-action-detail-map alert-detail-map ]

  {:pre (s/valid? ::alert-action-detail alert-action-detail-map)}

  (log/debug "Dispatching alert(s) to alert handler")
  (log/debug "alert-action-detail-map: "  alert-action-detail-map)
  (log/debug "alert-detail-map: "         alert-detail-map)

  (let [detail-map (alert-action-detail-map :details)]
    (case (alert-action-detail-map :alert-action-type)
      :pager-duty
        (pd/process-alert conf-map detail-map alert-detail-map)
      :team-email
        (em/process-alert conf-map detail-map alert-detail-map)))

  (log/debug "Alert dispatch complete"))

