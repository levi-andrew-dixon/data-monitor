(ns data-monitor.lib.aws.ec2
  "Wrapper for Amazonica AWS EC2 lib to ease and automate common usage patterns."
  (:require
    [clojure.java.io        :as jio]
    [clojure.tools.logging  :as log]
    [clojure.pprint         :as pp]
    [clojure.java.shell     :as sh]
    [clojure.string         :as string]
      
    [me.raynes.fs           :as fsu]
    [aero.core              :as aero]
    [amazonica.aws.ec2      :as aws-ec2])
  (:gen-class))


(defn get-instance-meta
  "@param filter-vec
    A vector of maps, with each map consisting of :name and :values keywords on which
    to filter results (filters applied on AWS side)
    e.g. [{:name \"tag:env\" :values [\"production\"]} {:name \"tag:name\" :values [#\"beetl-d0production.*\"]}]"
  [filter-vec]

  (try
    (apply aws-ec2/describe-instances (when (> (count filter-vec) 0) {:filters filter-vec}))
  (catch Exception e
    (log/error "Unable to query ec2 instances: " (.getMessage e))
    (throw (ex-info "Unable to query ec2 instaces with the provided filters" {:filter-vec filter-vec} e)))))


(defn get-private-hostname-from-instance-map
  "Retrieves the first private hostname found in the provided instance-map's network interface list
   NOTE: As of the time of this writing, you must be connected to the VPN to lookup DNS for the
         AWS private hostnames returned by this function.

   @param   instance-map  An instance-map as returned by get-instances-by-name.
   @return  hostname      The first hostname found or nil."
  [instance-map]
  (get-in instance-map [:network-interfaces 0 :private-dns-name]))


(defn get-private-ip-from-instance-map
  "Retrieves the first private ip address found in the first private hostname
   found in the provided instance-map's network interface list.

   @param   instance-map  An instance-map as returned by get-instances-by-name.
   @return  hostname      The first hostname found or nil."
  [instance-map]
  (get-in instance-map [:network-interfaces 0 :private-dns-name :private-ip-addresses 0]))


(defn get-instances-by-name
  "Retrieve instances with the given name or matching the given regex.
   @param instance-name-or-regex Either a string or regex to apply as a filter to instance names; e.g. #))\"beetl-d0production-.*\""
  [instance-name-or-regex]
  (let [instance-matches-map  (aws-ec2/describe-instances)
        reservations-vec      (instance-matches-map :reservations)]
    (doall
      (filter
        (fn [instance-map]
          (when-let [tag-vec (some-> instance-map :tags)]
            (not (empty?
              (filter
                (fn [tag-map]
                  (let [tag-key (tag-map :key)
                        tag-val (tag-map :value)]
                    (and (= tag-key "aws:autoscaling:groupName")
                       (if (instance? java.util.regex.Pattern instance-name-or-regex)
                         (re-find instance-name-or-regex tag-val)
                         (= instance-name-or-regex tag-val)))))
                  tag-vec)))))
        (flatten (for [reservation-map reservations-vec] (for [instance-map (reservation-map :instances)] instance-map)))))))

  
